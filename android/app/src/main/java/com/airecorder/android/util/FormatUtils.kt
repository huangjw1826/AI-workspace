package com.airecorder.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    
    fun formatDuration(seconds: Double?): String {
        if (seconds == null) return "--:--"
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }
    
    fun formatFileSize(bytes: Long?): String {
        if (bytes == null) return "--"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gb)
            mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
    
    private fun parseIsoTimestamp(timestamp: String?): Date? {
        if (timestamp.isNullOrBlank()) return null
        
        // Handle common formats manually if SimpleDateFormat fails
        val cleaned = timestamp.replace("Z", "+0000")
            .replace("T", " ")
            .split(".")[0] // Ignore nanoseconds
        
        val formats = arrayOf(
            "yyyy-MM-dd HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                return sdf.parse(cleaned)
            } catch (e: Exception) {
                // Try next
            }
        }
        
        // Last resort: try the original string with multiple formats
        val originalFormats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (format in originalFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                return sdf.parse(timestamp)
            } catch (e: Exception) { }
        }

        return null
    }
    
    fun formatDate(timestamp: String?): String {
        val date = parseIsoTimestamp(timestamp) ?: return "--"
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return outputFormat.format(date)
    }

    fun formatDateTime(timestamp: String?): String = formatDate(timestamp)
    
    fun formatShortDate(timestamp: String?): String {
        val date = parseIsoTimestamp(timestamp) ?: return "--"
        val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return outputFormat.format(date)
    }
    
    fun formatUptime(seconds: Double): String {
        val totalSeconds = seconds.toLong()
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return buildString {
            if (days > 0) append("$days 天 ")
            if (hours > 0) append("$hours 小时 ")
            if (minutes > 0) append("$minutes 分 ")
            if (days == 0L && hours == 0L) append("$secs 秒")
        }.trim()
    }
}
