package com.example.reelguard.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageTrackerEngineTest {

    private val tracked = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana"
    )

    @Test
    fun determineForegroundPackage_identifiesActiveApp() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", 5000L, UsageEventModel.MOVE_TO_FOREGROUND)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertEquals("com.instagram.android", result)
    }

    @Test
    fun determineForegroundPackage_handlesAppSwitching() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", 2000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("com.instagram.android", 5000L, UsageEventModel.MOVE_TO_BACKGROUND),
            UsageEventModel("com.google.android.youtube", 5100L, UsageEventModel.ACTIVITY_RESUMED)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertEquals("com.google.android.youtube", result)
    }

    @Test
    fun determineForegroundPackage_returnsNullWhenSwitchedToUntrackedApp() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", 2000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("com.instagram.android", 5000L, UsageEventModel.MOVE_TO_BACKGROUND),
            UsageEventModel("com.android.launcher", 5100L, UsageEventModel.MOVE_TO_FOREGROUND)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertNull(result)
    }

    @Test
    fun determineForegroundPackage_handlesScreenLock() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", 2000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("android", 4000L, UsageEventModel.SCREEN_NON_INTERACTIVE)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertNull(result)
    }

    @Test
    fun determineForegroundPackage_handlesScreenUnlock() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", 2000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("android", 4000L, UsageEventModel.SCREEN_NON_INTERACTIVE),
            UsageEventModel("android", 6000L, UsageEventModel.SCREEN_INTERACTIVE)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertEquals("com.instagram.android", result)
    }

    @Test
    fun determineForegroundPackage_debouncesDuplicateEventBursts() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.facebook.katana", 2000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("com.facebook.katana", 2005L, UsageEventModel.ACTIVITY_RESUMED),
            UsageEventModel("com.facebook.katana", 2010L, UsageEventModel.ACTIVITY_RESUMED)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertEquals("com.facebook.katana", result)
    }

    @Test
    fun determineForegroundPackage_filtersInvalidTimestamps() {
        val now = 10_000L
        val events = listOf(
            UsageEventModel("com.instagram.android", -500L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("com.instagram.android", 100_000L, UsageEventModel.MOVE_TO_FOREGROUND),
            UsageEventModel("com.google.android.youtube", 5000L, UsageEventModel.MOVE_TO_FOREGROUND)
        )
        val result = UsageTrackerEngine.determineForegroundPackage(events, tracked, now)
        assertEquals("com.google.android.youtube", result)
    }

    @Test
    fun calculateElapsedSeconds_regularDelta() {
        val seconds = UsageTrackerEngine.calculateElapsedSeconds(1000L, 2500L)
        assertEquals(1L, seconds) // 1.5s -> 1s integer division
    }

    @Test
    fun calculateElapsedSeconds_invalidOrBackwardDelta() {
        assertEquals(0L, UsageTrackerEngine.calculateElapsedSeconds(2500L, 1000L))
        assertEquals(0L, UsageTrackerEngine.calculateElapsedSeconds(0L, 1000L))
        assertEquals(0L, UsageTrackerEngine.calculateElapsedSeconds(-100L, 1000L))
    }

    @Test
    fun calculateElapsedSeconds_clampsLongSuspensionJump() {
        // If device sleeps for 1 hour (3,600,000ms), don't attribute 3600 seconds to foreground app!
        val seconds = UsageTrackerEngine.calculateElapsedSeconds(
            previousTickMs = 1000L,
            currentTickMs = 3_601_000L,
            maxTickWindowMs = 5000L
        )
        assertEquals(5L, seconds)
    }

    @Test
    fun isDateChanged_detectsMidnightRollover() {
        val today = LocalDate.of(2026, 10, 9)
        val sameDay = LocalDate.of(2026, 10, 9)
        val tomorrow = LocalDate.of(2026, 10, 10)

        assertFalse(UsageTrackerEngine.isDateChanged(today, sameDay))
        assertTrue(UsageTrackerEngine.isDateChanged(today, tomorrow))
    }

    @Test
    fun remainingSecondsAndLimitReached_behaveDeterministically() {
        val limitMinutes = 30 // 1800s
        assertEquals(1800L, UsageTrackerEngine.calculateRemainingSeconds(0L, limitMinutes))
        assertEquals(600L, UsageTrackerEngine.calculateRemainingSeconds(1200L, limitMinutes))
        assertEquals(0L, UsageTrackerEngine.calculateRemainingSeconds(1800L, limitMinutes))
        assertEquals(0L, UsageTrackerEngine.calculateRemainingSeconds(2000L, limitMinutes))

        assertFalse(UsageTrackerEngine.isTimeLimitReached(1799L, limitMinutes))
        assertTrue(UsageTrackerEngine.isTimeLimitReached(1800L, limitMinutes))
        assertTrue(UsageTrackerEngine.isTimeLimitReached(2000L, limitMinutes))
    }
}
