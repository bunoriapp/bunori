package com.halovoid.bunori.crash.utils

import java.io.Closeable

/*
Directly pulled over from tachiyomi source code
 */
inline fun <T : Closeable?> Array<T>.use(block: () -> Unit) {
    var blockException: Throwable? = null
    try {
        return block()
    } catch (e: Throwable) {
        blockException = e
        throw e
    } finally {
        when (blockException) {
            null -> forEach { it?.close() }
            else -> forEach {
                try {
                    it?.close()
                } catch (closeException: Throwable) {
                    blockException.addSuppressed(closeException)
                }
            }
        }
    }
}