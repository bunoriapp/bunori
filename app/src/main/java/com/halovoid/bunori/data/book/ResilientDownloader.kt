package com.halovoid.bunori.data.book

import android.util.Log
import com.halovoid.bunori.api.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ResilientDownloader {
    private const val TAG = "ResilientDownloader"
    private const val BUFFER_SIZE = 8192
    private const val MAX_RETRIES_PER_MIRROR = 3

    /**
     * Downloads a file from a prioritized list of mirrors.
     * Supports HTTP Range request resumption if a connection drops mid-transfer.
     * Verifies the resulting EPUB archive integrity before promoting to [destFile].
     *
     * @param mirrors Prioritized list of mirror download URLs.
     * @param destFile Target destination file on disk.
     * @param onProgress Optional progress callback emitting (downloadedBytes, totalContentBytes).
     * @return true if successfully downloaded and verified, false otherwise.
     */
    suspend fun downloadWithMirrors(
        mirrors: List<String>,
        destFile: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (destFile.exists() && EpubExtractor.isEpub(destFile)) {
            Log.d(TAG, "File ${destFile.name} already exists and is a valid EPUB.")
            return@withContext true
        }

        val parentDir = destFile.parentFile ?: return@withContext false
        if (!parentDir.exists()) parentDir.mkdirs()

        val tempFile = File(parentDir, "${destFile.name}.tmp")
        val client = NetworkClient.okHttpClient

        val cleanMirrors = mirrors.filter { url ->
            val u = url.lowercase()
            !u.contains("/search?") && !u.contains("ipfs_cid:") && (u.startsWith("http://") || u.startsWith("https://"))
        }

        if (cleanMirrors.isEmpty()) {
            Log.w(TAG, "No valid direct download mirror URLs found in list of ${mirrors.size} candidates")
            return@withContext false
        }

        for ((index, mirrorUrl) in cleanMirrors.withIndex()) {
            Log.i(TAG, "Attempting mirror [${index + 1}/${cleanMirrors.size}]: $mirrorUrl")
            var retryCount = 0

            while (retryCount < MAX_RETRIES_PER_MIRROR) {
                var append = false
                var isHtml = false
                try {
                    val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                    val requestBuilder = Request.Builder()
                        .url(mirrorUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                    if (existingBytes > 0) {
                        requestBuilder.header("Range", "bytes=$existingBytes-")
                        Log.d(TAG, "Resuming download from byte $existingBytes...")
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }

                        val contentType = response.header("Content-Type")?.lowercase() ?: ""
                        if (contentType.contains("text/html") || contentType.contains("application/xhtml+xml")) {
                            Log.w(TAG, "Mirror $mirrorUrl returned webpage/HTML ($contentType). Skipping non-direct mirror.")
                            if (tempFile.exists()) tempFile.delete()
                            isHtml = true
                            return@use
                        }

                        append = (response.code == 206)
                        val contentLength = response.body?.contentLength() ?: -1L
                        val totalBytes = if (append && contentLength > 0) existingBytes + contentLength else contentLength

                        val stream = response.body?.byteStream()
                            ?: throw IOException("Empty response body from $mirrorUrl")

                        FileOutputStream(tempFile, append).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var downloadedSoFar = if (append) existingBytes else 0L

                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedSoFar += bytesRead
                                onProgress?.invoke(downloadedSoFar, totalBytes)
                            }
                            output.flush()
                        }
                    }

                    if (isHtml) {
                        break
                    }

                    // Verify EPUB archive integrity
                    if (EpubExtractor.isEpub(tempFile)) {
                        Log.i(TAG, "EPUB integrity verified for ${tempFile.name} (${tempFile.length()} bytes)")
                        if (destFile.exists()) destFile.delete()
                        if (tempFile.renameTo(destFile) || (tempFile.copyTo(destFile, overwrite = true).also { tempFile.delete() }.exists())) {
                            Log.i(TAG, "Successfully downloaded and promoted to ${destFile.absolutePath}")
                            return@withContext true
                        }
                    } else {
                        Log.w(TAG, "Downloaded file from $mirrorUrl is not a valid EPUB (size=${tempFile.length()} bytes). Skipping invalid mirror.")
                        if (tempFile.exists()) tempFile.delete()
                        retryCount++
                    }

                } catch (e: Exception) {
                    retryCount++
                    Log.w(TAG, "Download interrupted on mirror $mirrorUrl (retry $retryCount/$MAX_RETRIES_PER_MIRROR): ${e.message}")
                    if (!append && tempFile.exists()) {
                        tempFile.delete()
                    }
                    delay(1000L * retryCount)
                }
            }
            if (tempFile.exists()) tempFile.delete()
            Log.w(TAG, "Mirror $mirrorUrl failed after $retryCount attempts. Trying next mirror...")
        }

        // All mirrors exhausted
        if (tempFile.exists()) tempFile.delete()
        Log.e(TAG, "Failed to download EPUB from all ${cleanMirrors.size} mirrors.")
        false
    }
}
