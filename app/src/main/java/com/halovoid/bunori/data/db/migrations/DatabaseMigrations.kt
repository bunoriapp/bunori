package com.halovoid.bunori.data.db.migrations

import android.net.Uri
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val SANITIZE_CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            sanitizeLegacyPaths(db)
        }

        private fun sanitizeLegacyPaths(db: SupportSQLiteDatabase) {
            try {
                // 1. Sanitize downloads table fileLocation
                db.query("SELECT id, fileLocation FROM downloads WHERE fileLocation LIKE 'content://%'").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val locIdx = cursor.getColumnIndex("fileLocation")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val loc = cursor.getString(locIdx)
                        if (loc != null && loc.startsWith("content://", ignoreCase = true)) {
                            val decoded = Uri.decode(loc)
                            val novelIndex = decoded.indexOf("novels/")
                            if (novelIndex != -1) {
                                val relPath = decoded.substring(novelIndex)
                                db.execSQL("UPDATE downloads SET fileLocation = ? WHERE id = ?", arrayOf<Any>(relPath, id))
                            }
                        }
                    }
                }

                // 2. Sanitize novels table coverUrl
                db.query("SELECT url, coverUrl FROM novels WHERE coverUrl LIKE 'content://%'").use { cursor ->
                    val urlIdx = cursor.getColumnIndex("url")
                    val coverIdx = cursor.getColumnIndex("coverUrl")
                    while (cursor.moveToNext()) {
                        val url = cursor.getString(urlIdx)
                        val cover = cursor.getString(coverIdx)
                        if (cover != null && cover.startsWith("content://", ignoreCase = true)) {
                            val decoded = Uri.decode(cover)
                            val novelIndex = decoded.indexOf("novels/")
                            if (novelIndex != -1) {
                                val relPath = decoded.substring(novelIndex)
                                db.execSQL("UPDATE novels SET coverUrl = ? WHERE url = ?", arrayOf<Any>(relPath, url))
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
