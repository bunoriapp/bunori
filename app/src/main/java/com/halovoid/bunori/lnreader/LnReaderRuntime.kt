package com.halovoid.bunori.lnreader

import android.content.Context

object LnReaderRuntime {
    private var cachedScript: String? = null;

    fun getScript(context: Context): String {
        return cachedScript ?: context.assets.open("ln_reader_runtime.js")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .also { cachedScript = it }
    }
}