package com.halovoid.bunori.extension.loader

import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import java.io.File

/**
 * Encapsulates an active, loaded extension instance and its installed package details.
 */
data class LoadedExtension(
    val manifest: ExtensionManifest,
    val extension: IExtension,
    val bextFile: File? = null,
    val jsFile: File? = null,
    val iconFile: File? = null
) {
    val packageFile: File?
        get() = bextFile ?: jsFile
}
