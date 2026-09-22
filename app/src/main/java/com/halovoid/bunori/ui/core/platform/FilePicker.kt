package com.halovoid.bunori.ui.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Shared composable helper for launching a folder tree picker (SAF).
 * Automatically handles taking persistable URI read/write permissions.
 */
@Composable
fun rememberFolderPickerLauncher(
    onFolderSelected: (Uri) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onFolderSelected(it)
        }
    }
    return { launcher.launch(null) }
}

/**
 * Shared composable helper for creating/exporting a document file (e.g. EPUB/ZIP).
 */
@Composable
fun rememberFileExportLauncher(
    mimeType: String = "application/epub+zip",
    onFileCreated: (Uri) -> Unit
): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        uri?.let(onFileCreated)
    }
    return { fileName -> launcher.launch(fileName) }
}

/**
 * Shared composable helper for opening/selecting an existing document.
 */
@Composable
fun rememberFileOpenLauncher(
    mimeTypes: Array<String> = arrayOf("*/*"),
    onFileSelected: (Uri) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onFileSelected)
    }
    return { launcher.launch(mimeTypes) }
}

/**
 * Convenience helper for launching an ACTION_VIEW intent for a file URI.
 */
fun Context.openFile(
    uri: Uri,
    mimeType: String,
    onNoApp: () -> Unit = {}
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Exception) {
        onNoApp()
    }
}

