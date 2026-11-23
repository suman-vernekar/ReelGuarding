package com.example.reelguard.overlay

import com.example.reelguard.model.AppUsageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateControllerTest {

    @Test
    fun shouldDisplayOverlay_followsSessionLifecycle() {
        val controller = OverlayStateController()
        assertFalse(controller.shouldDisplayOverlay())

        // App foregrounded
        controller.onPackageForegrounded("com.instagram.android", 10_000L)
        assertTrue(controller.shouldDisplayOverlay())
        assertEquals("com.instagram.android", controller.currentPackage)

        // User dismisses overlay for this session
        controller.dismissCurrentSession()
        assertFalse(controller.shouldDisplayOverlay())
        assertTrue(controller.isDismissedForSession)

        // Switching to YouTube resets dismissal for new session
        controller.onPackageForegrounded("com.google.android.youtube", 20_000L)
        assertTrue(controller.shouldDisplayOverlay())
        assertFalse(controller.isDismissedForSession)
        assertEquals("com.google.android.youtube", controller.currentPackage)

        // App backgrounded
        controller.onPackageBackgrounded()
        assertFalse(controller.shouldDisplayOverlay())
    }

    @Test
    fun shouldDisplayOverlay_respectsGlobalToggle() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.instagram.android", 10_000L)
        assertTrue(controller.shouldDisplayOverlay())

        controller.setOverlayEnabled(false)
        assertFalse(controller.shouldDisplayOverlay())

        controller.setOverlayEnabled(true)
        assertTrue(controller.shouldDisplayOverlay())
    }

    @Test
    fun computeUiModel_calculatesSessionDurationAndNormalStatus() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.instagram.android", 10_000L)

        val usage = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 300L,
            reelsCountToday = 5,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )

        // 30 seconds into session (at 40_000L)
        val model = controller.computeUiModel(usage, 40_000L)
        assertNotNull(model)
        assertEquals(30L, model!!.sessionSeconds)
        assertEquals("30s", model.sessionDurationText)
        assertEquals(OverlayStatusLevel.NORMAL, model.statusLevel)
        assertTrue(model.fullDisplayText.contains("Instagram"))
        assertEquals("5 / 20 Reels", model.reelsProgressText)
        assertTrue(model.fullDisplayText.contains("30s"))
        assertTrue(model.fullDisplayText.contains("5m / 30m"))
        assertFalse(model.fullDisplayText.contains("Reels"))
        assertEquals("😊", model.faceEmoji)
    }

    @Test
    fun computeUiModel_warningAndLimitReachedLevels() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.google.android.youtube", 10_000L)

        // Warning state
        val usageWarning = AppUsageData(
            packageName = "com.google.android.youtube",
            secondsToday = 1600L, // 200s remaining <= 300s threshold
            reelsCountToday = 5,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20,
            warningThresholdMinutes = 5
        )
        val modelWarning = controller.computeUiModel(usageWarning, 15_000L)
        assertNotNull(modelWarning)
        assertEquals(OverlayStatusLevel.WARNING, modelWarning!!.statusLevel)
        assertTrue(modelWarning.fullDisplayText.contains("⚠️"))

        // Limit reached state
        val usageReached = AppUsageData(
            packageName = "com.google.android.youtube",
            secondsToday = 1800L, // limit reached
            reelsCountToday = 20,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )
        val modelReached = controller.computeUiModel(usageReached, 20_000L)
        assertNotNull(modelReached)
        assertEquals(OverlayStatusLevel.LIMIT_REACHED, modelReached!!.statusLevel)
        assertTrue(modelReached.fullDisplayText.contains("🛑 Limit Reached"))
    }

    @Test
    fun computeUiModel_returnsNullWhenDismissedOrDisabled() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.facebook.katana", 10_000L)
        controller.dismissCurrentSession()

        val usage = AppUsageData(packageName = "com.facebook.katana")
        assertNull(controller.computeUiModel(usage, 15_000L))
    }

    @Test
    fun computeUiModel_limitReachedWarningIsNotDismissible() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.instagram.android", 10_000L)

        // User dismissed the overlay during normal usage
        controller.dismissCurrentSession()
        assertTrue(controller.isDismissedForSession)

        // When limit is reached, warning overrides dismissal
        val usageReached = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 1800L, // 30m reached
            dailyTimeLimitMinutes = 30
        )
        val model = controller.computeUiModel(usageReached, 25_000L)

        // The limit-reached warning must still be displayed and not dismissed
        assertNotNull(model)
        assertEquals(OverlayStatusLevel.LIMIT_REACHED, model!!.statusLevel)
        assertEquals("😔", model.faceEmoji)
        assertEquals("😔 Instagram", model.titleText)
        assertTrue(model.subtitleText.contains("Daily limit reached"))
    }

    @Test
    fun computeUiModel_displaysRemainingTimeAndEmojiPerApp() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.instagram.android", 10_000L)

        val usage = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 720L, // 12 mins used out of 30 mins -> 18 mins remaining
            dailyTimeLimitMinutes = 30
        )
        val model = controller.computeUiModel(usage, 15_000L)
        assertNotNull(model)
        assertEquals("18 minutes remaining", model!!.subtitleText)
        assertEquals("🙂", model.faceEmoji)
        assertEquals("🙂 Instagram", model.titleText)
        assertEquals(18L, model.remainingMinutes)
        assertEquals(1080L, model.remainingSeconds)

        // When plenty of time remaining (< 25% used, e.g. 5m / 30m)
        val usagePlenty = usage.copy(secondsToday = 300L)
        val modelPlenty = controller.computeUiModel(usagePlenty, 15_000L)
        assertNotNull(modelPlenty)
        assertEquals("😊", modelPlenty!!.faceEmoji)
        assertEquals("😊 Instagram", modelPlenty.titleText)
        assertEquals("25 minutes remaining", modelPlenty.subtitleText)
    }

    @Test
    fun computeUiModel_whenLimitReached_remainingSecondsIsZeroAndNoPositiveTimeShown() {
        val controller = OverlayStateController()
        controller.onPackageForegrounded("com.instagram.android", 10_000L)

        // Used 35 mins out of 30 mins (exceeded limit)
        val usageExceeded = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 2100L,
            dailyTimeLimitMinutes = 30
        )
        val model = controller.computeUiModel(usageExceeded, 15_000L)
        assertNotNull(model)
        assertEquals(OverlayStatusLevel.LIMIT_REACHED, model!!.statusLevel)
        assertEquals(0L, model.remainingSeconds)
        assertEquals(0L, model.remainingMinutes)
        assertEquals("Daily limit reached", model.remainingFormattedText)
        assertFalse(model.remainingFormattedText.contains("remaining"))
        assertFalse(model.remainingFormattedText.contains("minute"))
        assertEquals("😔", model.faceEmoji)
    }
}
