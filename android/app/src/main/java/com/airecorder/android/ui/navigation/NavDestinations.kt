package com.airecorder.android.ui.navigation

import com.airecorder.android.data.model.Summary
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class NavDestinations(val route: String) {
    @Serializable
    object Library : NavDestinations("library")
    
    @Serializable
    object Watch : NavDestinations("watch")
    
    @Serializable
    object Settings : NavDestinations("settings")
    
    @Serializable
    object Health : NavDestinations("health")
    
    @Serializable
    data class Detail(val recordingId: String) : NavDestinations("detail/$recordingId") {
        companion object {
            const val ROUTE_TEMPLATE = "detail/{recordingId}"
            fun createRoute(recordingId: String) = "detail/$recordingId"
        }
    }
    
    @Serializable
    data class SummaryDetail(val summaryJson: String) : NavDestinations("summaryDetail/$summaryJson") {
        companion object {
            const val ROUTE_TEMPLATE = "summaryDetail/{summaryJson}"
            fun createRoute(summary: Summary): String {
                val json = Json.encodeToString(summary)
                val encodedJson = android.util.Base64.encodeToString(
                    json.toByteArray(Charsets.UTF_8),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )
                return "summaryDetail/$encodedJson"
            }
        }
    }
    
    companion object {
        // 底栏 3-Tab: 录音库 | 设置 | 健康（监控整合到设置页）
        val bottomNavDestinations = listOf(Library, Settings, Health)
    }
}
