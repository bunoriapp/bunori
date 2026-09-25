package com.halovoid.bunori.wasm

import android.util.Log

object WamrBridge {
    private const val TAG = "WamrBridge"

    init {
        try {
            Class.forName("com.halovoid.bunori.wasm.WamrHttpBridge")
            System.loadLibrary("wamr")
            Log.i(TAG, "Successfully loaded libwamr.so")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load libwamr.so", e)
        }
    }

    external fun nativeLoad(bytes: ByteArray): Long
    external fun nativeInstantiate(modulePtr: Long): Long
    external fun nativeCallString(instPtr: Long, funcName: String, argStr: String?, page: Int): String?
    external fun nativeDestroy(instPtr: Long, modulePtr: Long)
}
