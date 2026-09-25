package com.halovoid.bunori.crash.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId

/*
Directly pulled over from tachiyomi source code
 */
class CrashLogUtil(
    private val context: Context,
    private val storageRepository: StorageRepository
) {

    suspend fun dumpLogs(exception: Throwable? = null) {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                val cacheDir = storageRepository.getCacheDir()
                val file = File(cacheDir, "bunori_crash_logs.txt")
                val logContent = getFullLog(exception)

                file.writeText(logContent)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Crash Logs"))
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to get logs", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getFullLog(exception: Throwable?): String {
        return StringBuilder().apply {
            append(getDebugInfo()).append("\n\n")
            append("--- RECENT ACTIVITY (BREADCRUMBS) ---\n")
            append(Logger.getLogs())
            append("\n--- STACK TRACE ---\n")
            exception?.let { append(it.stackTraceToString()) }
        }.toString()
    }

    fun getDebugInfo(): String {
        return """
            App ID: ${BuildConfig.APPLICATION_ID}
            App version: ${BuildConfig.VERSION_NAME}  ${BuildConfig.VERSION_CODE})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            Current time: ${OffsetDateTime.now(ZoneId.systemDefault())}
        """.trimIndent()
    }
}