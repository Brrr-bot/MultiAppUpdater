package com.homehub.dashboard.hub

data class HubDashboardResponse(
    val hubReady: Boolean = false,
    val batteryOptimizedIgnored: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val currentMode: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val currentFile: String = "",
    val compressionCurrent: Int = 0,
    val compressionTotal: Int = 0,
    val recentLogs: List<String> = emptyList(),
    val lastSyncSummary: String = "",
    val updatedAt: Long = 0L
)

data class HubUiState(
    val connected: Boolean = false,
    val statusLine: String = "Phone disconnected",
    val lastUpdated: String = "Waiting for hub",
    val transferLine: String = "Idle",
    val compressionLine: String = "Idle",
    val logs: List<String> = listOf("No hub data yet")
)
