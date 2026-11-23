package com.example.reelguard.tracking

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveAppTrackerTest {

    @Before
    fun setUp() {
        ActiveAppTracker.reset()
    }

    @After
    fun tearDown() {
        ActiveAppTracker.reset()
    }

    @Test
    fun onAccessibilityEvent_recordsTrackedPackage() {
        val now = 100_000L
        ActiveAppTracker.onAccessibilityEvent("com.instagram.android", now)

        assertEquals("com.instagram.android", ActiveAppTracker.activeAccessibilityPackage)
        assertEquals(now, ActiveAppTracker.lastInteractionTimeMs)
    }

    @Test
    fun isRecentlyActive_returnsTrueWithinTimeoutWindow() {
        val now = 100_000L
        ActiveAppTracker.onAccessibilityEvent("com.google.android.youtube", now)

        assertTrue(ActiveAppTracker.isRecentlyActive("com.google.android.youtube", now + 1000L, timeoutMs = 5000L))
        assertTrue(ActiveAppTracker.isRecentlyActive("com.google.android.youtube", now + 5000L, timeoutMs = 5000L))
        assertFalse(ActiveAppTracker.isRecentlyActive("com.google.android.youtube", now + 5001L, timeoutMs = 5000L))
    }

    @Test
    fun isRecentlyActive_returnsFalseForDifferentPackage() {
        val now = 100_000L
        ActiveAppTracker.onAccessibilityEvent("com.instagram.android", now)

        assertFalse(ActiveAppTracker.isRecentlyActive("com.google.android.youtube", now + 1000L))
    }

    @Test
    fun onAccessibilityEvent_ignoresUntrackedPackageWhenNotCurrent() {
        ActiveAppTracker.onAccessibilityEvent("com.unknown.app", 100_000L)
        assertNull(ActiveAppTracker.activeAccessibilityPackage)
    }

    @Test
    fun reset_clearsActiveState() {
        ActiveAppTracker.onAccessibilityEvent("com.facebook.katana", 100_000L)
        assertEquals("com.facebook.katana", ActiveAppTracker.activeAccessibilityPackage)

        ActiveAppTracker.reset()
        assertNull(ActiveAppTracker.activeAccessibilityPackage)
        assertEquals(0L, ActiveAppTracker.lastInteractionTimeMs)
    }
}
