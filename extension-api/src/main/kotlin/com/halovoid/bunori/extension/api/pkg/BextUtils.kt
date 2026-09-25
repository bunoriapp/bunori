package com.halovoid.bunori.extension.api.pkg

import com.halovoid.bunori.extension.api.ExtensionJson
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class IncompatibleApiException(val requiredVersion: Int, val currentVersion: Int) :
    Exception("Extension requires apiVersion $requiredVersion, but current Bunori app supports apiVersion $currentVersion")

class InvalidBextPackageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

object BextUtils {
    const val CURRENT_API_VERSION = 1
    const val MANIFEST_FILE_NAME = "manifest.json"
    const val WASM_FILE_NAME = "source.wasm"

    fun readPackage(
        inputStream: InputStream,
        validateApiVersion: Boolean = true
    ): BextPackage {
        var manifest: ExtensionManifest? = null
        var wasmBytes: ByteArray? = null
        var iconBytes: ByteArray? = null
        val extraFiles = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    val content = readEntryBytes(zis)

                    when (name) {
                        MANIFEST_FILE_NAME -> {
                            val jsonString = content.toString(Charsets.UTF_8)
                            manifest = ExtensionJson.json.decodeFromString<ExtensionManifest>(jsonString)
                        }
                        WASM_FILE_NAME -> {
                            wasmBytes = content
                        }
                        else -> {
                            extraFiles[name] = content
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            if (e is IncompatibleApiException) throw e
            throw InvalidBextPackageException("Failed to read .bext archive: ${e.message}", e)
        }

        val resolvedManifest = manifest ?: throw InvalidBextPackageException("Archive is missing required '$MANIFEST_FILE_NAME'")
        val resolvedWasmBytes = wasmBytes ?: throw InvalidBextPackageException("Archive is missing required '$WASM_FILE_NAME'")

        if (validateApiVersion) {
            validateManifest(resolvedManifest)
        }

        val candidatePath = resolvedManifest.iconPath?.removePrefix("/")?.replace('\\', '/')
        if (candidatePath != null) {
            iconBytes = extraFiles[candidatePath]
        }
        if (iconBytes == null) {
            iconBytes = extraFiles["assets/icon.png"]
                ?: extraFiles["icon.png"]
                ?: extraFiles["assets/icon.webp"]
                ?: extraFiles["icon.webp"]
                ?: extraFiles["assets/icon.jpg"]
                ?: extraFiles["icon.jpg"]
                ?: extraFiles.entries.firstOrNull { (k, _) ->
                    k.startsWith("assets/icon") || k.startsWith("icon.")
                }?.value
        }

        return BextPackage(
            manifest = resolvedManifest,
            wasmBytes = resolvedWasmBytes,
            iconBytes = iconBytes,
            extraFiles = extraFiles
        )
    }

    fun writePackage(pkg: BextPackage, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_FILE_NAME))
            val manifestJson = ExtensionJson.json.encodeToString(ExtensionManifest.serializer(), pkg.manifest)
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(WASM_FILE_NAME))
            zos.write(pkg.wasmBytes)
            zos.closeEntry()

            pkg.manifest.iconPath?.let { iconPath ->
                val normalizedPath = iconPath.removePrefix("/")
                if (pkg.iconBytes != null && !pkg.extraFiles.containsKey(normalizedPath)) {
                    zos.putNextEntry(ZipEntry(normalizedPath))
                    zos.write(pkg.iconBytes)
                    zos.closeEntry()
                }
            }

            for ((name, bytes) in pkg.extraFiles) {
                if (name != MANIFEST_FILE_NAME && name != WASM_FILE_NAME) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    fun validateManifest(manifest: ExtensionManifest, maxApiVersion: Int = CURRENT_API_VERSION) {
        if (manifest.id.isBlank()) {
            throw InvalidBextPackageException("Manifest 'id' cannot be blank")
        }
        if (manifest.name.isBlank()) {
            throw InvalidBextPackageException("Manifest 'name' cannot be blank")
        }
        if (manifest.baseUrl.isBlank()) {
            throw InvalidBextPackageException("Manifest 'baseUrl' cannot be blank")
        }
        if (manifest.apiVersion > maxApiVersion) {
            throw IncompatibleApiException(
                requiredVersion = manifest.apiVersion,
                currentVersion = maxApiVersion
            )
        }
    }

    private fun readEntryBytes(zis: ZipInputStream): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var len: Int
        while (zis.read(buffer).also { len = it } != -1) {
            baos.write(buffer, 0, len)
        }
        return baos.toByteArray()
    }
}
