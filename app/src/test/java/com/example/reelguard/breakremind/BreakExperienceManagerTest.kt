package com.example.reelguard.breakremind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakExperienceManagerTest {

    @Test
    fun eligibility_respectsSnoozeWindow() {
        val manager = BreakExperienceManager()
        val pkg = "com.instagram.android"
        val now = 10_000L

        // Initially eligible
        assertTrue(manager.isEligibleForBreakPrompt(pkg, now))

        // Snooze for 5 minutes (300_000ms)
        manager.applySnooze(pkg, now, 300_000L)

        // 2 minutes later: still snoozed -> NOT eligible
        assertFalse(manager.isEligibleForBreakPrompt(pkg, now + 120_000L))

        // 6 minutes later: snooze expired -> eligible again
        assertTrue(manager.isEligibleForBreakPrompt(pkg, now + 360_000L))
    }

    @Test
    fun eligibility_respectsDailyOverride() {
        val manager = BreakExperienceManager()
        val pkg = "com.google.android.youtube"
        val now = 10_000L

        assertTrue(manager.isEligibleForBreakPrompt(pkg, now))

        manager.applyDailyOverride(pkg)

        // Overridden for the day
        assertFalse(manager.isEligibleForBreakPrompt(pkg, now + 100_000L))
        assertFalse(manager.isEligibleForBreakPrompt(pkg, now + 1_000_000L))

        // Reset package -> eligible again
        manager.resetPackage(pkg)
        assertTrue(manager.isEligibleForBreakPrompt(pkg, now))
    }

    @Test
    fun sessionState_isSnoozeActiveCalculation() {
        val state = BreakSessionState(
            packageName = "com.facebook.katana",
            isSnoozed = true,
            snoozeUntilMs = 50_000L
        )

        assertTrue(state.isSnoozeActive(40_000L))
        assertFalse(state.isSnoozeActive(50_000L))
        assertFalse(state.isSnoozeActive(60_000L))
    }
}
