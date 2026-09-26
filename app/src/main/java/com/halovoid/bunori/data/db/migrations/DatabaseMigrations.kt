package com.halovoid.bunori.data.db.migrations

import android.net.Uri
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.halovoid.bunori.extension.api.models.ExtensionRepo

object DatabaseMigrations {

    val Migration_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE novels ADD COLUMN status TEXT DEFAULT NULL")
            val targetPrefix = "${ExtensionRepo.generateStableKey("https://bunoriapp.github.io/extensions/index.min.json")}."
            db.execSQL("UPDATE novels SET crawlerName = '$targetPrefix' || crawlerName WHERE crawlerName NOT LIKE '%.%'")
            db.execSQL("UPDATE novels SET crawlerName = '$targetPrefix' || SUBSTR(crawlerName, 6) WHERE crawlerName LIKE 'bext.%'")
        }
    }

    val SANITIZE_CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            sanitizeLegacyPaths(db)
            // NOTE: crawlerName prefixing is handled one-time by Migration_1_2.
            // CrawlerFactory.getCrawler() resolves any remaining legacy/cross-repo
            // IDs at runtime, so no repeated migration is needed here.
        }


        private fun sanitizeLegacyPaths(db: SupportSQLiteDatabase) {
            try {
                db.query("SELECT id, fileLocation FROM downloads WHERE fileLocation LIKE 'content://%' OR fileLocation LIKE 'file://%'").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val locIdx = cursor.getColumnIndex("fileLocation")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val loc = cursor.getString(locIdx)
                        if (loc != null && (loc.startsWith("content://", ignoreCase = true) || loc.startsWith("file://", ignoreCase = true))) {
                            val decoded = Uri.decode(loc)
                            val novelIndex = decoded.indexOf("novels/")
                            if (novelIndex != -1) {
                                val relPath = decoded.substring(novelIndex)
                                db.execSQL("UPDATE downloads SET fileLocation = ? WHERE id = ?", arrayOf<Any>(relPath, id))
                            }
                        }
                    }
                }

                db.query("SELECT url, coverUrl FROM novels WHERE coverUrl LIKE 'content://%' OR coverUrl LIKE 'file://%'").use { cursor ->
                    val urlIdx = cursor.getColumnIndex("url")
                    val coverIdx = cursor.getColumnIndex("coverUrl")
                    while (cursor.moveToNext()) {
                        val url = cursor.getString(urlIdx)
                        val cover = cursor.getString(coverIdx)
                        if (cover != null && (cover.startsWith("content://", ignoreCase = true) || cover.startsWith("file://", ignoreCase = true))) {
                            val decoded = Uri.decode(cover)
                            val novelIndex = decoded.indexOf("novels/")
                            if (novelIndex != -1) {
                                val relPath = decoded.substring(novelIndex)
                                db.execSQL("UPDATE novels SET coverUrl = ? WHERE url = ?", arrayOf<Any>(relPath, url))
                            }
                        }
                    }
                }
                db.query("SELECT id, artifactDestination FROM artifacts WHERE artifactDestination LIKE 'content://%' OR artifactDestination LIKE 'file://%'").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val destIdx = cursor.getColumnIndex("artifactDestination")
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(idIdx)
                        val dest = cursor.getString(destIdx)
                        if (dest != null && (dest.startsWith("content://", ignoreCase = true) || dest.startsWith("file://", ignoreCase = true))) {
                            val decoded = Uri.decode(dest)
                            val artifactIndex = decoded.indexOf("artifacts/")
                            if (artifactIndex != -1) {
                                val relPath = decoded.substring(artifactIndex)
                                db.execSQL("UPDATE artifacts SET artifactDestination = ? WHERE id = ?", arrayOf<Any>(relPath, id))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
