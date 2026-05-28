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
            fun createRoute(recordingId: String) = "detail/$recordingId"
        }
    }
    
    @Serializable
    data class SummaryDetail(val summaryJson: String) : NavDestinations("summaryDetail/$summaryJson") {
        companion object {
            fun createRoute(summary: Summary): String {
                val json = Json.encodeToString(summary)
                return "summaryDetail/$json"
            }
        }
    }
    
    companion object {
        val bottomNavDestinations = listOf(Library, Watch, Settings)
    }
}
