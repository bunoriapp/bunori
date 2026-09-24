package com.halovoid.bunori.extension.loader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.pkg.BextPackage
import com.halovoid.bunori.extension.api.pkg.BextUtils
import com.halovoid.bunori.wasm.WamrExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * Unpacks .bext packages into app storage and instantiates native [WamrExtension] runners.
 * Automatically selects AOT machine code for device CPU architecture if present.
 */
class BextLoader(private val context: Context) {
    companion object {
        private const val TAG = "BextLoader"
    }

    fun loadFromBextFile(bextFile: File): LoadedExtension {
        if (!bextFile.exists() || bextFile.length() == 0L) {
            throw IllegalArgumentException("File does not exist or is empty: ${bextFile.absolutePath}")
        }

        Log.i(TAG, "Unpacking .bext archive: ${bextFile.name}")
        val pkg = bextFile.inputStream().use { stream ->
            BextUtils.readPackage(stream, validateApiVersion = true)
        }

        return loadPackage(pkg, bextFile)
    }

    fun loadPackage(pkg: BextPackage, sourceBextFile: File): LoadedExtension {
        val extensionId = pkg.manifest.id
        val targetDir = File(File(context.filesDir, "installed_extensions"), extensionId)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // 1. Pick the best binary: Native AOT matching device ABI, or portable source.wasm
        var selectedBytes = pkg.wasmBytes
        var binaryName = BextUtils.WASM_FILE_NAME
        var isAot = false

        for (abi in android.os.Build.SUPPORTED_ABIS) {
            val aotPath = "artifacts/$abi/extension.aot"
            val aotBytes = pkg.extraFiles[aotPath]
            if (aotBytes != null && aotBytes.isNotEmpty()) {
                selectedBytes = aotBytes
                binaryName = "extension_$abi.aot"
                isAot = true
                Log.i(TAG, "Selected native AOT binary for ABI '$abi' (${aotBytes.size} bytes) for ${pkg.manifest.name}")
                break
            }
        }

        val binaryFile = File(targetDir, binaryName)
        FileOutputStream(binaryFile).use { fos ->
            fos.write(selectedBytes)
        }

        // 2. Write icon if present
        var iconFile: File? = null
        if (pkg.iconBytes != null) {
            val ext = pkg.manifest.iconPath?.substringAfterLast('.', "webp") ?: "webp"
            val file = File(targetDir, "icon.$ext")
            FileOutputStream(file).use { fos ->
                fos.write(pkg.iconBytes)
            }
            iconFile = file
        } else {
            val existing = File(targetDir, "icon.webp").takeIf { it.exists() && it.length() > 0 }
                ?: File(targetDir, "icon.png").takeIf { it.exists() && it.length() > 0 }
            if (existing != null) {
                iconFile = existing
            }
        }

        // 3. Instantiate native WamrExtension with automatic fallback
        val extensionInstance: IExtension = try {
            val mode = if (isAot) "AOT Native Machine Code" else "Fast Interpreter"
            Log.i(TAG, "Initializing WamrExtension [$mode] for ${pkg.manifest.name} from ${binaryFile.name}")
            WamrExtension(
                manifest = pkg.manifest,
                binaryBytes = selectedBytes
            ).also {
                Log.i(TAG, "Successfully loaded extension: ${pkg.manifest.name} (v${pkg.manifest.version}) in $mode mode")
            }
        } catch (e: Exception) {
            if (isAot && pkg.wasmBytes.isNotEmpty()) {
                Log.w(TAG, "AOT binary instantiation failed for ${pkg.manifest.name}. Falling back to portable WASM bytecode.", e)
                val fallbackFile = File(targetDir, BextUtils.WASM_FILE_NAME)
                FileOutputStream(fallbackFile).use { fos ->
                    fos.write(pkg.wasmBytes)
                }

                val showToast = runCatching {
                    runBlocking { PreferenceRepository.getInstance(context).showWasmSlowModeToast.first() }
                }.getOrDefault(true)

                if (showToast) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "${pkg.manifest.name} entered slow mode due to a version mismatch. It will still work normally, but please report to the developer to resolve.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                WamrExtension(
                    manifest = pkg.manifest,
                    binaryBytes = pkg.wasmBytes
                ).also {
                    Log.i(TAG, "Successfully loaded extension fallback: ${pkg.manifest.name} (v${pkg.manifest.version}) in Fast Interpreter mode")
                }
            } else {
                throw e
            }
        }

        return LoadedExtension(
            manifest = pkg.manifest,
            extension = extensionInstance,
            bextFile = sourceBextFile,
            iconFile = iconFile
        )
    }
}
