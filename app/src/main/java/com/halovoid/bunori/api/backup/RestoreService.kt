package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class RestoreService(private val context: Context) {

    fun restoreBackup(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val tempDir = File(context.cacheDir, "restore_temp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            // Unzip archive
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Validate manifest
            val manifestFile = File(tempDir, "mainfest.json").takeIf { it.exists() } 
                ?: File(tempDir, "manifest.json").takeIf { it.exists() } 
                ?: return false

            val manifestText = manifestFile.readText()
            val manifest = JSONObject(manifestText)
            val contents = manifest.optJSONObject("contents")

            // Close DB connections before replacing
            AppDatabase.getDatabase(context).close()

            // Restore database.db
            val dbFile = File(tempDir, "database.db")
            if (dbFile.exists()) {
                val targetDbFile = context.getDatabasePath("bunori.db")
                targetDbFile.parentFile?.mkdirs()
                dbFile.copyTo(targetDbFile, overwrite = true)
            }

            // Restore novels/
            val novelsDir = File(tempDir, "novels")
            if (novelsDir.exists() && novelsDir.isDirectory) {
                val targetNovelsDir = File(context.filesDir, "novels")
                targetNovelsDir.mkdirs()
                novelsDir.copyRecursively(targetNovelsDir, overwrite = true)
            }

            // Restore datastore/
            val datastoreDir = File(tempDir, "datastore")
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                val targetDatastoreDir = File(context.filesDir, "datastore")
                targetDatastoreDir.mkdirs()
                datastoreDir.copyRecursively(targetDatastoreDir, overwrite = true)
            }

            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
