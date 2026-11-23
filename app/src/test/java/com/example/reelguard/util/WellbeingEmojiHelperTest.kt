package com.example.reelguard.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WellbeingEmojiHelperTest {

    @Test
    fun getTimeWellbeingStatus_handlesPermissionMissing() {
        val status = WellbeingEmojiHelper.getTimeWellbeingStatus(
            totalSecondsUsed = 100L,
            dailyLimitMinutes = 60,
            hasPermission = false
        )
        assertEquals("😐", status.emoji)
        assertEquals("Usage data unavailable", status.title)
    }

    @Test
    fun getTimeWellbeingStatus_handlesNoLimitConfigured() {
        val status = WellbeingEmojiHelper.getTimeWellbeingStatus(
            totalSecondsUsed = 100L,
            dailyLimitMinutes = 0,
            hasPermission = true
        )
        assertEquals("😊", status.emoji)
        assertEquals("Set your daily goal", status.title)
    }

    @Test
    fun getTimeWellbeingStatus_handlesAllThresholdRanges() {
        // Daily limit 60 mins = 3600 secs
        // 1. 0–24% -> 😊 Great start (e.g. 10 mins = 600s = 16.6%)
        val s1 = WellbeingEmojiHelper.getTimeWellbeingStatus(600L, 60, true)
        assertEquals("😊", s1.emoji)
        assertEquals("Great start", s1.title)

        // 2. 25–49% -> 🙂 Nicely balanced (e.g. 20 mins = 1200s = 33.3%)
        val s2 = WellbeingEmojiHelper.getTimeWellbeingStatus(1200L, 60, true)
        assertEquals("🙂", s2.emoji)
        assertEquals("Nicely balanced", s2.title)

        // 3. 50–74% -> 😐 Stay mindful (e.g. 36 mins = 2160s = 60%)
        val s3 = WellbeingEmojiHelper.getTimeWellbeingStatus(2160L, 60, true)
        assertEquals("😐", s3.emoji)
        assertEquals("Stay mindful", s3.title)

        // 4. 75–89% -> 😟 Getting close (e.g. 48 mins = 2880s = 80%)
        val s4 = WellbeingEmojiHelper.getTimeWellbeingStatus(2880L, 60, true)
        assertEquals("😟", s4.emoji)
        assertEquals("Getting close", s4.title)

        // 5. 90–99% -> 😰 Almost there (e.g. 57 mins = 3420s = 95%)
        val s5 = WellbeingEmojiHelper.getTimeWellbeingStatus(3420L, 60, true)
        assertEquals("😰", s5.emoji)
        assertEquals("Almost there", s5.title)

        // 6. 100%+ -> 😔 Limit reached (e.g. 60 mins = 3600s = 100%)
        val s6 = WellbeingEmojiHelper.getTimeWellbeingStatus(3600L, 60, true)
        assertEquals("😔", s6.emoji)
        assertEquals("Limit reached", s6.title)

        // 6b. Exceeded (e.g. 70 mins = 4200s)
        val s6b = WellbeingEmojiHelper.getTimeWellbeingStatus(4200L, 60, true)
        assertEquals("😔", s6b.emoji)
        assertEquals("Limit reached", s6b.title)
    }

    @Test
    fun getReelCountStatus_handlesRangesAndNoLimit() {
        // No limit
        val s0 = WellbeingEmojiHelper.getReelCountStatus(5, 0)
        assertEquals("😕", s0.emoji)
        assertEquals("No limit set", s0.title)

        // 0-49% -> 😊
        val s1 = WellbeingEmojiHelper.getReelCountStatus(5, 20)
        assertEquals("😊", s1.emoji)
        assertEquals("Comfortable", s1.title)

        // 50-74% -> 🙂
        val s2 = WellbeingEmojiHelper.getReelCountStatus(12, 20)
        assertEquals("🙂", s2.emoji)
        assertEquals("Progressing", s2.title)

        // 75-89% -> 😐
        val s3 = WellbeingEmojiHelper.getReelCountStatus(16, 20)
        assertEquals("😐", s3.emoji)
        assertEquals("Halfway", s3.title)

        // 90-99% -> 😟
        val s4 = WellbeingEmojiHelper.getReelCountStatus(19, 20)
        assertEquals("😟", s4.emoji)
        assertEquals("Approaching", s4.title)

        // 100%+ -> 😔
        val s5 = WellbeingEmojiHelper.getReelCountStatus(20, 20)
        assertEquals("😔", s5.emoji)
        assertEquals("Limit reached", s5.title)
    }
}
