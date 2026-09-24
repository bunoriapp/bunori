package com.halovoid.bunori.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.halovoid.bunori.data.db.dao.ArtifactDao
import com.halovoid.bunori.data.db.dao.BatchDao
import com.halovoid.bunori.data.db.dao.ChapterDao
import com.halovoid.bunori.data.db.dao.DownloadDao
import com.halovoid.bunori.data.db.dao.NovelDao
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.data.db.entities.ArtifactEntity
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.ChapterEntity
import com.halovoid.bunori.data.db.entities.DownloadEntity
import com.halovoid.bunori.data.db.entities.NovelEntity
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.db.migrations.DatabaseMigrations

/**
 * Main Room database for the application.
 * Part of the Data layer, responsible for local persistence.
 */
@Database(
    entities = [NovelEntity::class, ChapterEntity::class, BatchEntity::class, TaskEntity::class, ArtifactEntity::class, DownloadEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun batchDao(): BatchDao
    abstract fun taskDao(): TaskDao
    abstract fun artifactDao(): ArtifactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bunori.db"
                )
                    .addCallback(DatabaseMigrations.SANITIZE_CALLBACK)
                    .addMigrations(
                        DatabaseMigrations.Migration_1_2
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndResetDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Exception) {}
                INSTANCE = null
            }
        }
    }
}
