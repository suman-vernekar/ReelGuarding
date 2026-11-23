package com.example.reelguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklySummaryTest {

    @Test
    fun weeklySummary_aggregatesTotalsAndAveragesCorrectly() {
        val start = LocalDate.of(2026, 10, 3)
        val end = LocalDate.of(2026, 10, 9)

        val records = listOf(
            DailyUsageRecord(date = start, packageName = "com.instagram.android", secondsUsed = 700L, reelsCount = 14),
            DailyUsageRecord(date = start, packageName = "com.google.android.youtube", secondsUsed = 1400L, reelsCount = 21),
            DailyUsageRecord(date = end, packageName = "com.instagram.android", secondsUsed = 1400L, reelsCount = 28),
            DailyUsageRecord(date = end, packageName = "com.facebook.katana", secondsUsed = 700L, reelsCount = 7)
        )

        val summary = WeeklySummary.fromRecords(records, start, end)

        assertEquals(4200L, summary.totalSeconds) // 700 + 1400 + 1400 + 700 = 4200s (70 mins)
        assertEquals(70, summary.totalReels) // 14 + 21 + 28 + 7 = 70 reels
        assertEquals(600L, summary.dailyAverageSeconds) // 4200 / 7 = 600s (10 mins/day)
        assertEquals(10, summary.dailyAverageReels) // 70 / 7 = 10 reels/day

        assertEquals("1h 10m", summary.formattedTotalTime)
        assertEquals("10m", summary.formattedDailyAverageTime)

        // Per-app breakdown
        assertEquals(2100L, summary.appBreakdown["com.instagram.android"])
        assertEquals(1400L, summary.appBreakdown["com.google.android.youtube"])
        assertEquals(700L, summary.appBreakdown["com.facebook.katana"])

        assertEquals(42, summary.appReelBreakdown["com.instagram.android"])
        assertEquals(21, summary.appReelBreakdown["com.google.android.youtube"])
        assertEquals(7, summary.appReelBreakdown["com.facebook.katana"])
    }

    @Test
    fun weeklySummary_handlesEmptyRecordsGracefully() {
        val start = LocalDate.of(2026, 10, 3)
        val end = LocalDate.of(2026, 10, 9)

        val summary = WeeklySummary.fromRecords(emptyList(), start, end)

        assertEquals(0L, summary.totalSeconds)
        assertEquals(0, summary.totalReels)
        assertEquals(0L, summary.dailyAverageSeconds)
        assertEquals(0, summary.dailyAverageReels)
        assertEquals("0s", summary.formattedTotalTime)
        assertEquals("0s", summary.formattedDailyAverageTime)
        assertTrue(summary.appBreakdown.isEmpty())
        assertTrue(summary.appReelBreakdown.isEmpty())
    }

    @Test
    fun dailyUsageRecord_formattedHelpers() {
        val record = DailyUsageRecord(
            date = LocalDate.of(2026, 10, 9),
            packageName = "com.instagram.android",
            secondsUsed = 1800L,
            reelsCount = 20
        )

        assertEquals("Instagram", record.appDisplayName)
        assertEquals("Reels", record.videoTypeName)
        assertEquals("30m", record.formattedTimeUsed)
    }
}
