package com.halovoid.bunori.lnreader

import android.util.Log
import com.dokar.quickjs.ExperimentalQuickJsApi
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.alias.asyncFunc
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LnReaderBridge(private val pluginName: String) : AutoCloseable {
    companion object {
        private const val TAG = "LnReaderBridge"
    }

    private var quickJs: QuickJs? = null

    @OptIn(ExperimentalQuickJsApi::class)
    suspend fun initialize(runtimeJs: String, pluginJs: String) = withContext(Dispatchers.IO) {
        val qjs = QuickJs.create(jobDispatcher = Dispatchers.IO)

        qjs.asyncFunc("__native_fetch") { args ->
            val url = args[0] as String
            val initJson = args.getOrNull(1) as? String ?: "{}"
            LnReaderHttpBridge.execute(url, initJson)
        }

        qjs.function("__native_log") { args ->
            val level = args.getOrNull(0) as? String ?: "DEBUG"
            val msg = args.getOrNull(1) as? String ?: ""
            when (level) {
                "ERROR" -> Log.e(TAG, "[$pluginName] $msg")
                "WARN" -> Log.w(TAG, "[$pluginName] $msg")
                else -> Log.d(TAG, "[$pluginName] $msg")
            }
        }

        qjs.evaluate<Any?>(runtimeJs)
        qjs.evaluate<Any?>(pluginJs)

        quickJs = qjs
    }

    suspend fun evaluate(script: String): String? = withContext(Dispatchers.IO) {
        val qjs = quickJs ?: throw IllegalStateException("QuickJS engine not initialized for $pluginName")
        qjs.evaluate<String?>(script)
    }

    override fun close() {
        try {
            quickJs?.close()
            quickJs = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing QuickJS for $pluginName", e)
        }
    }
}