package com.airecorder.android.util

object AudioUtils {
    
    fun formatDuration(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
    
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    fun formatProgress(downloaded: Long, total: Long): String {
        return "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
    }
    
    fun getSummaryDisplayTitle(mode: String?): String {
        return when (mode) {
            "structured_summary" -> "结构化摘要"
            "meeting_minutes" -> "会议纪要"
            "todo_extraction" -> "待办事项提取"
            else -> mode ?: "摘要"
        }
    }
}
