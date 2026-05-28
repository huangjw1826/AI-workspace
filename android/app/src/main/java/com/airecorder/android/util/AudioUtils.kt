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
        if (bytes <= 0) return if (bytes == 0L) "0 B" else "--"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    fun formatProgress(downloaded: Long, total: Long): String {
        val totalStr = if (total > 0) formatFileSize(total) else "未知大小"
        return "${formatFileSize(downloaded)} / $totalStr"
    }
    
    fun getSummaryDisplayTitle(mode: String?): String {
        return when (mode) {
            "structured_summary", "summary" -> "结构化摘要"
            "meeting_minutes" -> "会议纪要"
            "action_items", "todo_extraction" -> "待办事项"
            "decisions_risks" -> "决策与风险"
            "executive_brief" -> "管理层简报"
            "polished_transcript" -> "转写内容规整"
            "transcript" -> "全文转写"
            else -> mode ?: "摘要"
        }
    }
}
