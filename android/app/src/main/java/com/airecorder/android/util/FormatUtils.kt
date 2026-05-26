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
    
    fun formatDate(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "--"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            date?.let { outputFormat.format(it) } ?: "--"
        } catch (e: Exception) {
            "--"
        }
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
