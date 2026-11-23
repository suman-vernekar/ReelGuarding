package com.example.reelguard.data

import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.SupportedApp
import java.time.LocalDate

data class DailyUsageRecord(
    val date: LocalDate,
    val packageName: String,
    val secondsUsed: Long,
    val reelsCount: Int = 0,
    val limitMinutes: Int = 30,
    val reelLimit: Int = 20,
    val isLimitEnabled: Boolean = true,
    val dataStatus: String = "OK"
) {
    val appDisplayName: String
        get() = SupportedApp.fromPackageName(packageName)?.displayName ?: packageName

    val videoTypeName: String
        get() = SupportedApp.fromPackageName(packageName)?.shortVideoName ?: "Reels"

    val formattedTimeUsed: String
        get() = AppUsageData.formatDuration(secondsUsed)

    val formattedLimit: String
        get() = AppUsageData.formatDuration(limitMinutes * 60L)
}

data class WeeklySummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalSeconds: Long,
    val totalReels: Int,
    val dailyAverageSeconds: Long,
    val dailyAverageReels: Int,
    val appBreakdown: Map<String, Long>, // packageName -> totalSeconds
    val appReelBreakdown: Map<String, Int> // packageName -> totalReels
) {
    val formattedTotalTime: String
        get() = AppUsageData.formatDuration(totalSeconds)

    val formattedDailyAverageTime: String
        get() = AppUsageData.formatDuration(dailyAverageSeconds)

    val mostUsedPackage: String?
        get() = appBreakdown.maxByOrNull { it.value }?.key

    val mostUsedAppName: String
        get() = mostUsedPackage?.let { SupportedApp.fromPackageName(it)?.displayName ?: it } ?: "None"

    companion object {
        fun fromRecords(
            records: List<DailyUsageRecord>,
            startDate: LocalDate,
            endDate: LocalDate
        ): WeeklySummary {
            val totalSec = records.sumOf { it.secondsUsed }
            val totalR = records.sumOf { it.reelsCount }
            val daysCount = 7.coerceAtLeast(1)

            val avgSec = totalSec / daysCount
            val avgReels = totalR / daysCount

            val appBreakdown = mutableMapOf<String, Long>()
            val appReelBreakdown = mutableMapOf<String, Int>()

            for (record in records) {
                appBreakdown[record.packageName] = (appBreakdown[record.packageName] ?: 0L) + record.secondsUsed
                appReelBreakdown[record.packageName] = (appReelBreakdown[record.packageName] ?: 0) + record.reelsCount
            }

            return WeeklySummary(
                startDate = startDate,
                endDate = endDate,
                totalSeconds = totalSec,
                totalReels = totalR,
                dailyAverageSeconds = avgSec,
                dailyAverageReels = avgReels,
                appBreakdown = appBreakdown,
                appReelBreakdown = appReelBreakdown
            )
        }
    }
}

data class MonthlySummary(
    val year: Int,
    val month: Int,
    val totalSeconds: Long,
    val dailyAverageSeconds: Long,
    val mostUsedPackage: String?,
    val appBreakdown: Map<String, Long>,
    val changePercentageVsPreviousMonth: Float? = null
) {
    val formattedTotalTime: String
        get() = AppUsageData.formatDuration(totalSeconds)

    val formattedDailyAverageTime: String
        get() = AppUsageData.formatDuration(dailyAverageSeconds)

    val mostUsedAppName: String
        get() = mostUsedPackage?.let { SupportedApp.fromPackageName(it)?.displayName ?: it } ?: "None"
}

data class AnalyticsSummary(
    val totalSecondsToday: Long,
    val totalSecondsThisWeek: Long,
    val totalSecondsThisMonth: Long,
    val dailyAverageSeconds: Long,
    val mostUsedPackage: String?,
    val changePercentageVsPreviousWeek: Float? = null
) {
    val formattedTodayTime: String
        get() = AppUsageData.formatDuration(totalSecondsToday)

    val formattedWeekTime: String
        get() = AppUsageData.formatDuration(totalSecondsThisWeek)

    val formattedMonthTime: String
        get() = AppUsageData.formatDuration(totalSecondsThisMonth)

    val formattedDailyAverageTime: String
        get() = AppUsageData.formatDuration(dailyAverageSeconds)

    val mostUsedAppName: String
        get() = mostUsedPackage?.let { SupportedApp.fromPackageName(it)?.displayName ?: it } ?: "None"
}
