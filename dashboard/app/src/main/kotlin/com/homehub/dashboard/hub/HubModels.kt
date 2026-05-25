package com.homehub.dashboard.hub

data class HubDashboardResponse(
    val hubReady: Boolean,
    val batteryOptimizedIgnored: Boolean,
    val accessibilityEnabled: Boolean,
    val currentMode: String,
    val progressCurrent: Int,
    val progressTotal: Int,
    val currentFile: String,
    val compressionCurrent: Int,
    val compressionTotal: Int,
    val recentLogs: List<String>,
    val lastSyncSummary: String,
    val updatedAt: Long
)

data class HubUiState(
    val connected: Boolean = false,
    val statusLine: String = "Phone disconnected",
    val lastUpdated: String = "Waiting for hub",
    val transferLine: String = "Idle",
    val compressionLine: String = "Idle",
    val logs: List<String> = listOf("No hub data yet")
)
