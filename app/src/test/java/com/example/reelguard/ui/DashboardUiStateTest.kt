package com.example.reelguard.ui

import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun defaultState_isEmptyAndDashboardScreen() {
        val state = DashboardUiState()
        assertEquals(Screen.DASHBOARD, state.currentScreen)
        assertFalse(state.isLoading)
        assertFalse(state.hasAnyData)
        assertEquals(0L, state.totalSecondsToday)
        assertEquals(0, state.totalReelsToday)
        assertFalse(state.allCriticalPermissionsGranted)
    }

    @Test
    fun aggregation_sumsSecondsAndReelsAcrossApps() {
        val app1 = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 300L,
            reelsCountToday = 5
        )
        val app2 = AppUsageData(
            packageName = "com.google.android.youtube",
            secondsToday = 600L,
            reelsCountToday = 8
        )
        val state = DashboardUiState(appsUsage = listOf(app1, app2))

        assertEquals(900L, state.totalSecondsToday)
        assertEquals(13, state.totalReelsToday)
        assertTrue(state.hasAnyData)
    }

    @Test
    fun warningsAndLimitsCount_reflectsAppStates() {
        val appNormal = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 100L,
            reelsCountToday = 2,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )
        val appWarning = AppUsageData(
            packageName = "com.google.android.youtube",
            secondsToday = 1600L, // 200s remaining <= 300s threshold
            reelsCountToday = 5,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20,
            warningThresholdMinutes = 5
        )
        val appReached = AppUsageData(
            packageName = "com.facebook.katana",
            secondsToday = 100L,
            reelsCountToday = 20, // Reached limit
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )

        val state = DashboardUiState(appsUsage = listOf(appNormal, appWarning, appReached))
        assertEquals(1, state.activeWarningsCount)
        assertEquals(1, state.reachedLimitsCount)
    }

    @Test
    fun permissionStatus_calculatesCorrectly() {
        val stateIncomplete = DashboardUiState(
            hasUsagePermission = true,
            hasOverlayPermission = false,
            hasAccessibilityPermission = true
        )
        assertFalse(stateIncomplete.allCriticalPermissionsGranted)

        val stateComplete = DashboardUiState(
            hasUsagePermission = true,
            hasOverlayPermission = true,
            hasAccessibilityPermission = true
        )
        assertTrue(stateComplete.allCriticalPermissionsGranted)
    }
}
