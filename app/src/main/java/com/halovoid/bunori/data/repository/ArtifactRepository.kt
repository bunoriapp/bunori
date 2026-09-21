package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class ArtifactRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    private val artifactDao = db.artifactDao()

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ArtifactRepository? = null

        fun getInstance(context: Context): ArtifactRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtifactRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getArtifactForBatch(id: String) : List<Artifact> {
        return artifactDao.getArtifactForBatch(id).map { it.toDomain() }
    }

    fun getArtifactsByNovelFlow(url: String): Flow<List<Artifact>> {
        return artifactDao.getArtifactsByNovelFlow(url).map { it.map { entity -> entity.toDomain() } }
    }

    suspend fun artifactExists(artifact: Artifact): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(artifact.artifactDestination.toUri())?.use {
                    true
                } ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun copyArtifactToUri(artifact: Artifact, destinationUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val sourceUri = artifact.artifactDestination.toUri()

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                destinationUri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun insertArtifacts(artifact: Artifact) {
        withContext(Dispatchers.IO) {
            artifactDao.insertArtifact(artifact.toEntity())
        }
    }

    suspend fun removeArtifact(artifact: Artifact) {
        withContext(Dispatchers.IO) {
            artifactDao.removeArtifact(artifact.toEntity())
        }
    }
}