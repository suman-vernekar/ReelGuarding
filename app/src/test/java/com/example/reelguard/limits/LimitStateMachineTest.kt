package com.example.reelguard.limits

import com.example.reelguard.model.AppLimitConfig
import com.example.reelguard.model.TriggerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LimitStateMachineTest {

    private val defaultConfig = AppLimitConfig(
        packageName = "com.instagram.android",
        dailyTimeLimitMinutes = 30, // 1800s
        dailyReelLimit = 20,
        warningThresholdMinutes = 5, // 300s
        warningThresholdReels = 3,
        isBreakReminderEnabled = true,
        triggerPolicy = TriggerPolicy.WHICHEVER_FIRST
    )

    @Test
    fun belowWarningState_producesBelowWarningWithoutAlerts() {
        val machine = LimitStateMachine()
        val result = machine.evaluate(
            config = defaultConfig,
            secondsToday = 300L,
            reelsToday = 5
        )

        assertEquals(LimitStatus.BELOW_WARNING, result.status)
        assertFalse(result.shouldTriggerWarningAlert)
        assertFalse(result.shouldTriggerLimitAlert)
    }

    @Test
    fun warningState_triggersWarningAlertOnceAndSuppressesDuplicates() {
        val machine = LimitStateMachine()

        // 1. Initial warning threshold crossing (1600s used -> 200s left <= 300s threshold)
        val res1 = machine.evaluate(
            config = defaultConfig,
            secondsToday = 1600L,
            reelsToday = 5
        )
        assertEquals(LimitStatus.WARNING, res1.status)
        assertTrue(res1.shouldTriggerWarningAlert)
        assertFalse(res1.shouldTriggerLimitAlert)

        // 2. Subsequent evaluation tick still in warning -> warning status maintained, but NO duplicate alert!
        val res2 = machine.evaluate(
            config = defaultConfig,
            secondsToday = 1601L,
            reelsToday = 5
        )
        assertEquals(LimitStatus.WARNING, res2.status)
        assertFalse(res2.shouldTriggerWarningAlert)
        assertFalse(res2.shouldTriggerLimitAlert)
    }

    @Test
    fun timeLimitReached_triggersLimitAlertOnceAndSuppressesDuplicates() {
        val machine = LimitStateMachine()

        // Cross time limit (1800s)
        val res1 = machine.evaluate(
            config = defaultConfig,
            secondsToday = 1800L,
            reelsToday = 10
        )
        assertEquals(LimitStatus.LIMIT_REACHED, res1.status)
        assertTrue(res1.shouldTriggerLimitAlert)
        assertFalse(res1.shouldTriggerWarningAlert)

        // Next tick
        val res2 = machine.evaluate(
            config = defaultConfig,
            secondsToday = 1801L,
            reelsToday = 10
        )
        assertEquals(LimitStatus.LIMIT_REACHED, res2.status)
        assertFalse(res2.shouldTriggerLimitAlert)
    }

    @Test
    fun reelLimitReached_triggersLimitAlert() {
        val machine = LimitStateMachine()

        val res = machine.evaluate(
            config = defaultConfig,
            secondsToday = 600L,
            reelsToday = 20 // 20 >= 20 limit
        )
        assertEquals(LimitStatus.LIMIT_REACHED, res.status)
        assertTrue(res.shouldTriggerLimitAlert)
    }

    @Test
    fun bothLimitsReached_triggersLimitAlert() {
        val machine = LimitStateMachine()

        val res = machine.evaluate(
            config = defaultConfig,
            secondsToday = 2000L,
            reelsToday = 25
        )
        assertEquals(LimitStatus.LIMIT_REACHED, res.status)
        assertTrue(res.shouldTriggerLimitAlert)
    }

    @Test
    fun settingsChangedWhileTrackingIsActive_transitionsProperly() {
        val machine = LimitStateMachine()

        // Reached 30m limit
        val res1 = machine.evaluate(defaultConfig, 1800L, 5)
        assertEquals(LimitStatus.LIMIT_REACHED, res1.status)
        assertTrue(res1.shouldTriggerLimitAlert)

        // User extends limit to 60 minutes
        val updatedConfig = defaultConfig.copy(dailyTimeLimitMinutes = 60)
        val res2 = machine.evaluate(updatedConfig, 1801L, 5)

        // 1801s is now well below the 60m (3600s) limit and warning threshold!
        assertEquals(LimitStatus.BELOW_WARNING, res2.status)
        assertFalse(res2.shouldTriggerLimitAlert)
        assertFalse(res2.shouldTriggerWarningAlert)
    }

    @Test
    fun dailyReset_clearsAlertHistory() {
        val machine = LimitStateMachine()
        val day1 = LocalDate.of(2026, 10, 9)
        val day2 = LocalDate.of(2026, 10, 10)

        // Day 1: limit reached
        val resDay1 = machine.evaluate(defaultConfig, 1800L, 20, day1)
        assertTrue(resDay1.shouldTriggerLimitAlert)

        // Day 2 (midnight rollover): if usage is at limit again, alert fires fresh for new day
        val resDay2 = machine.evaluate(defaultConfig, 1800L, 20, day2)
        assertTrue(resDay2.shouldTriggerLimitAlert)
    }

    @Test
    fun triggerPolicies_evaluatedAccurately() {
        val machine = LimitStateMachine()

        // TIME_ONLY: reel limit reached, but time limit not reached -> should NOT trigger limit
        val timeOnlyConfig = defaultConfig.copy(triggerPolicy = TriggerPolicy.TIME_ONLY)
        val resTimeOnly = machine.evaluate(timeOnlyConfig, 600L, 25)
        assertEquals(LimitStatus.BELOW_WARNING, resTimeOnly.status)

        // REELS_ONLY: time limit reached, but reel limit not reached -> should NOT trigger limit
        val reelsOnlyConfig = defaultConfig.copy(triggerPolicy = TriggerPolicy.REELS_ONLY)
        val resReelsOnly = machine.evaluate(reelsOnlyConfig, 2000L, 5)
        assertEquals(LimitStatus.BELOW_WARNING, resReelsOnly.status)

        // BOTH_REQUIRED: time limit reached AND reel limit reached -> triggers limit
        val bothConfig = defaultConfig.copy(triggerPolicy = TriggerPolicy.BOTH_REQUIRED)
        val resBothNotYet = machine.evaluate(bothConfig, 2000L, 5)
        assertEquals(LimitStatus.BELOW_WARNING, resBothNotYet.status)

        val resBothMet = machine.evaluate(bothConfig, 2000L, 20)
        assertEquals(LimitStatus.LIMIT_REACHED, resBothMet.status)
    }
}
