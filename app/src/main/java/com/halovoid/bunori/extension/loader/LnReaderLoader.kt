package com.halovoid.bunori.extension.loader

import android.content.Context
import android.util.Log
import com.halovoid.bunori.extension.api.ExtensionJson
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionFormat
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import com.halovoid.bunori.lnreader.LnReaderExtension
import com.halovoid.bunori.lnreader.LnReaderRuntime
import java.io.File
import java.io.FileOutputStream

class LnReaderLoader(private val context: Context) {
    companion object {
        private const val TAG = "LnReaderLoader"
        private const val JS_FILE_NAME = "plugin.js"
        private const val MANIFEST_FILE_NAME = "manifest.json"
    }

    fun loadFromDirectory(dir: File): LoadedExtension {
        val jsFile = File(dir, JS_FILE_NAME)
        val manifestFile = File(dir, MANIFEST_FILE_NAME)

        if (!jsFile.exists() || jsFile.length() == 0L) {
            throw IllegalArgumentException("$JS_FILE_NAME does not exist or is empty in ${dir.absolutePath}")
        }
        if (!manifestFile.exists() || manifestFile.length() == 0L) {
            throw IllegalArgumentException("$MANIFEST_FILE_NAME does not exist or is empty in ${dir.absolutePath}")
        }

        Log.i(TAG, "Loading LNReader plugin from: ${dir.name}")

        val manifestJson = manifestFile.readText(Charsets.UTF_8)
        val decodedManifest = ExtensionJson.json.decodeFromString<ExtensionManifest>(manifestJson)
        val rawId = decodedManifest.id.removePrefix("lnreader.").removePrefix("bext.")
        val manifest = decodedManifest.copy(id = rawId, format = ExtensionFormat.LNREADER_JS)
        val jsContent = jsFile.readText(Charsets.UTF_8)
        val runtimeJs = LnReaderRuntime.getScript(context)

        val iconFile = File(dir, "icon.png").takeIf { it.exists() && it.length() > 0 }
            ?: File(dir, "icon.webp").takeIf { it.exists() && it.length() > 0 }

        val extensionInstance: IExtension = LnReaderExtension(
            manifest = manifest,
            pluginJs = jsContent,
            runtimeJs = runtimeJs
        )

        return LoadedExtension(
            manifest = manifest,
            extension = extensionInstance,
            jsFile = jsFile,
            iconFile = iconFile
        )
    }
    fun install(manifest: ExtensionManifest, jsBytes: ByteArray, iconBytes: ByteArray? = null): LoadedExtension {
        val rawId = manifest.id.removePrefix("lnreader.").removePrefix("bext.")
        val normalizedManifest = manifest.copy(id = rawId, format = ExtensionFormat.LNREADER_JS)
        val targetDir = File(File(context.filesDir, "installed_extensions"), normalizedManifest.id).apply { mkdirs() }

        val jsFile = File(targetDir, JS_FILE_NAME)
        FileOutputStream(jsFile).use { it.write(jsBytes) }

        val manifestFile = File(targetDir, MANIFEST_FILE_NAME)
        val manifestJson = ExtensionJson.json.encodeToString(ExtensionManifest.serializer(), normalizedManifest)
        manifestFile.writeText(manifestJson, Charsets.UTF_8)

        if (iconBytes != null && iconBytes.isNotEmpty()) {
            val iconFile = File(targetDir, "icon.png")
            FileOutputStream(iconFile).use { it.write(iconBytes) }
        }

        return loadFromDirectory(targetDir)
    }
}