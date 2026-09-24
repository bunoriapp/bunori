package com.halovoid.bunori.lnreader

import android.content.Context
import android.util.Log
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.extension.api.ExtensionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

@Serializable
data class RuntimeVersionMeta(
    val version: String,
    val tag: String? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val releasedAt: String? = null
)

object LnReaderRuntime {
    private const val TAG = "LnReaderRuntime"
    private const val RUNTIME_DIR = "runtime"
    private const val RUNTIME_FILE = "ln_reader_runtime.js"
    private const val PREFS_NAME = "lnreader_runtime_prefs"
    private const val KEY_VERSION = "runtime_version"
    private const val BASELINE_VERSION = "1.0.0"

    private const val VERSION_URL =
        "https://github.com/bunoriapp/bunori_lnreader_runtime/releases/latest/download/version.json"
    private const val SCRIPT_URL =
        "https://github.com/bunoriapp/bunori_lnreader_runtime/releases/latest/download/runtime.js"

    @Volatile
    private var cachedScript: String? = null

    /**
     * Retrieves the JS runtime script.
     * Prioritizes the updated script stored in the app's internal files directory.
     * If not found or empty, falls back to the bundled assets file.
     */
    fun getScript(context: Context): String {
        cachedScript?.let { return it }

        val localRuntimeFile = File(File(context.filesDir, RUNTIME_DIR), RUNTIME_FILE)
        val script = if (localRuntimeFile.exists() && localRuntimeFile.length() > 0) {
            try {
                localRuntimeFile.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read local runtime file, falling back to assets", e)
                loadFromAssets(context)
            }
        } else {
            loadFromAssets(context)
        }

        cachedScript = script
        return script
    }

    private fun loadFromAssets(context: Context): String {
        return context.assets.open(RUNTIME_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    fun getInstalledVersion(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VERSION, null) ?: BASELINE_VERSION
    }

    private fun saveInstalledVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VERSION, version).apply()
    }

    /**
     * Checks remote GitHub Releases for a newer version of the runtime.
     * If a newer version is found, downloads it to a .tmp file, verifies its SHA-256 hash,
     * atomically replaces the local runtime file, and invalidates the in-memory cache.
     *
     * @return true if an update was downloaded and applied, false otherwise.
     */
    suspend fun updateIfAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = NetworkClient.okHttpClient

            // 1. Fetch remote version metadata
            val versionReq = Request.Builder()
                .url(VERSION_URL)
                .header("User-Agent", "Bunori-App")
                .build()

            val versionJson = client.newCall(versionReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "Runtime update check returned status ${resp.code}")
                    return@withContext false
                }
                resp.body?.string() ?: return@withContext false
            }

            val meta = try {
                ExtensionJson.json.decodeFromString<RuntimeVersionMeta>(versionJson)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse remote version.json: ${e.message}")
                return@withContext false
            }

            val currentVersion = getInstalledVersion(context)
            Log.d(TAG, "Current runtime version: $currentVersion, Remote version: ${meta.version}")

            if (!isNewerVersion(meta.version, currentVersion)) {
                Log.d(TAG, "Runtime is already up to date ($currentVersion)")
                return@withContext false
            }

            // 2. Download new runtime to a temporary file
            Log.i(TAG, "Downloading new runtime ${meta.version}...")
            val targetDir = File(context.filesDir, RUNTIME_DIR).apply { mkdirs() }
            val tempFile = File(targetDir, "$RUNTIME_FILE.tmp")

            val scriptReq = Request.Builder()
                .url(SCRIPT_URL)
                .header("User-Agent", "Bunori-App")
                .build()

            client.newCall(scriptReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Failed to download runtime script: HTTP ${resp.code}")
                    return@withContext false
                }
                val bytes = resp.body?.bytes() ?: return@withContext false
                tempFile.writeBytes(bytes)
            }

            // 3. Verify SHA256 checksum if provided
            if (!meta.sha256.isNullOrBlank()) {
                val computedHash = sha256(tempFile.readBytes())
                if (!computedHash.equals(meta.sha256, ignoreCase = true)) {
                    Log.e(TAG, "Checksum mismatch! Expected: ${meta.sha256}, Got: $computedHash")
                    tempFile.delete()
                    return@withContext false
                }
            }

            // 4. Atomically promote temp file to active runtime
            val destFile = File(targetDir, RUNTIME_FILE)
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            // 5. Update saved version and clear memory cache
            saveInstalledVersion(context, meta.version)
            cachedScript = null
            Log.i(TAG, "Successfully updated LNReader runtime to version ${meta.version}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check or apply runtime update: ${e.message}")
            false
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun isNewerVersion(remote: String, installed: String): Boolean {
        if (remote == installed) return false
        val cleanRemote = remote.removePrefix("v")
        val cleanInstalled = installed.removePrefix("v")
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val installedParts = cleanInstalled.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val ins = installedParts.getOrElse(i) { 0 }
            if (r > ins) return true
            if (r < ins) return false
        }
        return cleanRemote != cleanInstalled
    }
}