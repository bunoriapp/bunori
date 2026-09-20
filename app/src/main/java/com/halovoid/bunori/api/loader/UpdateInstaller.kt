package com.halovoid.bunori.api.loader

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.halovoid.bunori.utils.Logger
import java.io.File

object UpdateInstaller {

    fun installApk(context: Context, apkUriString: String) {
        try {
            val apkUri = apkUriString.toUri()
            val file = if (apkUri.scheme == "file") {
                File(apkUri.path!!)
            } else {
                File(context.cacheDir, "update_target.apk").also { dest ->
                    context.contentResolver.openInputStream(apkUri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e("[UpdateInstaller] Failed to install update: ${e.message}", e)
            throw e
        }
    }
}
