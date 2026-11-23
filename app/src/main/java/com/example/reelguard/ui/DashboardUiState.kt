package com.example.reelguard.ui

import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.Screen

data class DashboardUiState(
    val currentScreen: Screen = Screen.DASHBOARD,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val appsUsage: List<AppUsageData> = emptyList(),
    val isMonitorRunning: Boolean = false,
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasBatteryOptimizationIgnored: Boolean = false
) {
    val totalSecondsToday: Long
        get() = appsUsage.sumOf { it.secondsToday }

    val totalReelsToday: Int
        get() = appsUsage.sumOf { it.reelsCountToday }

    val allCriticalPermissionsGranted: Boolean
        get() = hasUsagePermission && hasOverlayPermission && hasAccessibilityPermission

    val activeWarningsCount: Int
        get() = appsUsage.count { it.isWarningReached }

    val reachedLimitsCount: Int
        get() = appsUsage.count { it.isLimitReached }

    val hasAnyData: Boolean
        get() = appsUsage.isNotEmpty()
}
