package com.halovoid.bunori.ui.core.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.halovoid.bunori.data.repository.StorageRepository
import okio.buffer
import okio.source

/**
 * Coil fetcher that intercepts relative storage paths (e.g. "novels/.../cover.jpg")
 * and resolves them directly through StorageRepository.
 */
class BunoriStorageFetcher(
    private val path: String,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val storageRepository = StorageRepository.getInstance(context)
        val stream = storageRepository.openInputStream(path) ?: return null
        return SourceResult(
            source = ImageSource(stream.source().buffer(), context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            val trimmed = data.trim()
            if (trimmed.startsWith("novels/", ignoreCase = true) ||
                trimmed.startsWith("artifacts/", ignoreCase = true)
            ) {
                return BunoriStorageFetcher(trimmed, context)
            }
            return null
        }
    }
}
