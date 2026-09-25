package com.halovoid.bunori.api.loader

object VersionUtils {
    fun isUpdateAvailable(current: String?, latest: String): Boolean {
        if (current == null) return true
        
        val currentSegments = normalize(current)
        val latestSegments = normalize(latest)

        val maxSize = maxOf(currentSegments.size, latestSegments.size)
        
        for (i in 0 until maxSize) {
            val curr = currentSegments.getOrElse(i) { 0 }
            val late = latestSegments.getOrElse(i) { 0 }
            
            if (late > curr) return true
            if (curr > late) return false
        }
        
        return false
    }

    private fun normalize(version: String): List<Int> {
        return version.lowercase()
            .replace("v", "")
            .split("-")[0] // Ignore suffixes like -beta
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }
}
