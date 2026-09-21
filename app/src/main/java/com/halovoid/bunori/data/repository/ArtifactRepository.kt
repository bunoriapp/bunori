package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Main repository interface for managing artifact metadata.
 */
interface ArtifactRepository {
    suspend fun getArtifactForBatch(id: String): List<Artifact>
    fun getArtifactsByNovelFlow(url: String): Flow<List<Artifact>>
    suspend fun insertArtifact(artifact: Artifact)
    suspend fun removeArtifact(artifact: Artifact)

    companion object {
        fun getInstance(context: Context): ArtifactRepository = ArtifactRepositoryImpl.getInstance(context)
    }
}

class ArtifactRepositoryImpl private constructor(context: Context) : ArtifactRepository {

    private val db = AppDatabase.getDatabase(context)
    private val artifactDao = db.artifactDao()

    companion object {
        @Volatile
        private var INSTANCE: ArtifactRepository? = null

        fun getInstance(context: Context): ArtifactRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtifactRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override suspend fun getArtifactForBatch(id: String): List<Artifact> = withContext(Dispatchers.IO) {
        artifactDao.getArtifactForBatch(id).map { it.toDomain() }
    }

    override fun getArtifactsByNovelFlow(url: String): Flow<List<Artifact>> {
        return artifactDao.getArtifactsByNovelFlow(url)
            .map { it.map { entity -> entity.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun insertArtifact(artifact: Artifact) = withContext(Dispatchers.IO) {
        artifactDao.insertArtifact(artifact.toEntity())
    }

    override suspend fun removeArtifact(artifact: Artifact) = withContext(Dispatchers.IO) {
        artifactDao.removeArtifact(artifact.toEntity())
    }
}