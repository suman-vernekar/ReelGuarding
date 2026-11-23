package com.example.reelguard.data

import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.SupportedApp
import com.example.reelguard.ui.ChartBarEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsStorageTest {

    @Test
    fun dailyUsageRecord_supportsIndependentLimitsAndStatus() {
        val record = DailyUsageRecord(
            date = LocalDate.of(2026, 10, 11),
            packageName = "com.instagram.android",
            secondsUsed = 1800L,
            reelsCount = 25,
            limitMinutes = 30,
            reelLimit = 20,
            isLimitEnabled = true,
            dataStatus = "OK"
        )

        assertEquals("Instagram", record.appDisplayName)
        assertEquals(30, record.limitMinutes)
        assertTrue(record.isLimitEnabled)
        assertEquals("OK", record.dataStatus)
        assertEquals("30m", record.formattedTimeUsed)
    }

    @Test
    fun monthlySummary_formatsTimeAndIdentifiesMostUsedApp() {
        val breakdown = mapOf(
            "com.instagram.android" to 7200L,  // 2h
            "com.google.android.youtube" to 14400L, // 4h
            "com.facebook.katana" to 3600L     // 1h
        )
        val summary = MonthlySummary(
            year = 2026,
            month = 10,
            totalSeconds = 25200L, // 7h
            dailyAverageSeconds = 840L,
            mostUsedPackage = "com.google.android.youtube",
            appBreakdown = breakdown,
            changePercentageVsPreviousMonth = -12.5f
        )

        assertEquals("7h", summary.formattedTotalTime)
        assertEquals("14m", summary.formattedDailyAverageTime)
        assertEquals("YouTube", summary.mostUsedAppName)
        assertEquals(-12.5f, summary.changePercentageVsPreviousMonth)
    }

    @Test
    fun analyticsSummary_computesCorrectly() {
        val summary = AnalyticsSummary(
            totalSecondsToday = 1800L,
            totalSecondsThisWeek = 12600L,
            totalSecondsThisMonth = 54000L,
            dailyAverageSeconds = 1800L,
            mostUsedPackage = "com.instagram.android",
            changePercentageVsPreviousWeek = 5.0f
        )

        assertEquals("30m", summary.formattedTodayTime)
        assertEquals("3h 30m", summary.formattedWeekTime)
        assertEquals("15h", summary.formattedMonthTime)
        assertEquals("30m", summary.formattedDailyAverageTime)
        assertEquals("Instagram", summary.mostUsedAppName)
    }

    @Test
    fun chartBarEntry_handlesAppSecondsAndFiltering() {
        val date = LocalDate.of(2026, 10, 11)
        val appSecs = mapOf(
            "com.instagram.android" to 1200L,
            "com.google.android.youtube" to 600L
        )
        val entry = ChartBarEntry(
            date = date,
            label = "10/11",
            appSeconds = appSecs,
            totalSeconds = 1800L,
            hasData = true
        )

        assertEquals(date, entry.date)
        assertEquals("10/11", entry.label)
        assertEquals(1800L, entry.totalSeconds)
        assertTrue(entry.hasData)
        assertEquals(1200L, entry.appSeconds["com.instagram.android"])
        assertEquals(600L, entry.appSeconds["com.google.android.youtube"])

        val emptyEntry = ChartBarEntry(
            date = date.minusDays(1),
            label = "10/10",
            appSeconds = emptyMap(),
            totalSeconds = 0L,
            hasData = false
        )
        assertFalse(emptyEntry.hasData)
    }

    @Test
    fun csvExportFormat_conformsToSpecification() {
        val records = listOf(
            DailyUsageRecord(
                date = LocalDate.of(2026, 10, 10),
                packageName = "com.instagram.android",
                secondsUsed = 1800L,
                limitMinutes = 30,
                dataStatus = "OK"
            ),
            DailyUsageRecord(
                date = LocalDate.of(2026, 10, 11),
                packageName = "com.google.android.youtube",
                secondsUsed = 3600L,
                limitMinutes = 60,
                dataStatus = "OK"
            )
        )

        val sb = StringBuilder()
        sb.append("Date,App,UsageSeconds,UsageMinutes,DailyLimitMinutes,DataStatus\n")
        for (r in records) {
            val minutes = r.secondsUsed / 60
            val limitStr = if (r.isLimitEnabled) r.limitMinutes.toString() else "Disabled"
            sb.append("${r.date},${r.appDisplayName},${r.secondsUsed},$minutes,$limitStr,${r.dataStatus}\n")
        }

        val csv = sb.toString()
        assertTrue(csv.startsWith("Date,App,UsageSeconds,UsageMinutes,DailyLimitMinutes,DataStatus\n"))
        assertTrue(csv.contains("2026-10-10,Instagram,1800,30,30,OK"))
        assertTrue(csv.contains("2026-10-11,YouTube,3600,60,60,OK"))
    }
}
