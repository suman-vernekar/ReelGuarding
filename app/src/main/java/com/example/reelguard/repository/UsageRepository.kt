package com.example.reelguard.repository

import android.content.Context
import com.example.reelguard.AppConfig
import com.example.reelguard.UsageStore
import com.example.reelguard.data.AnalyticsSummary
import com.example.reelguard.data.DailyUsageRecord
import com.example.reelguard.data.MonthlySummary
import com.example.reelguard.data.WeeklySummary
import java.time.LocalDate

interface UsageRepository {
    fun saveRecord(record: DailyUsageRecord)
    fun getRecord(date: LocalDate, packageName: String): DailyUsageRecord
    fun getHistory(days: Int = 180, untilDate: LocalDate = LocalDate.now()): List<DailyUsageRecord>
    fun getDateRangeHistory(startDate: LocalDate, endDate: LocalDate): List<DailyUsageRecord>
    fun getWeeklySummary(endDate: LocalDate = LocalDate.now()): WeeklySummary
    fun getMonthlySummary(year: Int, month: Int): MonthlySummary
    fun getAnalyticsSummary(currentDate: LocalDate = LocalDate.now()): AnalyticsSummary
    fun syncToday(date: LocalDate = LocalDate.now())
    fun clearHistory()
    fun exportToCsv(): String
}

/**
 * Robust SQLite-backed implementation supporting 6-month historical usage analytics,
 * date ranges, monthly summaries, and CSV backups.
 */
class SqliteUsageRepository(
    private val context: Context
) : UsageRepository {

    private val dbHelper = UsageDatabaseHelper(context).apply {
        migrateFromPreferencesIfNeeded()
    }

    override fun saveRecord(record: DailyUsageRecord) {
        dbHelper.saveOrUpdateRecord(record)
    }

    override fun getRecord(date: LocalDate, packageName: String): DailyUsageRecord {
        if (date == LocalDate.now()) {
            val usage = UsageStore.getAppUsage(context, packageName)
            val liveRecord = DailyUsageRecord(
                date = date,
                packageName = packageName,
                secondsUsed = usage.secondsToday,
                reelsCount = usage.reelsCountToday,
                limitMinutes = usage.dailyTimeLimitMinutes,
                reelLimit = usage.dailyReelLimit,
                isLimitEnabled = usage.isLimitEnabled,
                dataStatus = "OK"
            )
            // Keep database synchronized with live tick
            dbHelper.saveOrUpdateRecord(liveRecord)
            return liveRecord
        }

        return dbHelper.getRecord(date, packageName) ?: DailyUsageRecord(
            date = date,
            packageName = packageName,
            secondsUsed = 0L,
            reelsCount = 0,
            limitMinutes = UsageStore.getAppLimitMinutes(context, packageName),
            isLimitEnabled = UsageStore.isAppLimitEnabled(context, packageName),
            dataStatus = "NO_DATA"
        )
    }

    override fun getHistory(days: Int, untilDate: LocalDate): List<DailyUsageRecord> {
        syncToday(LocalDate.now())
        return dbHelper.getHistoryRecords(days, untilDate)
    }

    override fun getDateRangeHistory(startDate: LocalDate, endDate: LocalDate): List<DailyUsageRecord> {
        syncToday(LocalDate.now())
        return dbHelper.getDateRangeRecords(startDate, endDate)
    }

    override fun getWeeklySummary(endDate: LocalDate): WeeklySummary {
        syncToday(LocalDate.now())
        return dbHelper.getWeeklySummary(endDate)
    }

    override fun getMonthlySummary(year: Int, month: Int): MonthlySummary {
        syncToday(LocalDate.now())
        return dbHelper.getMonthlySummary(year, month)
    }

    override fun getAnalyticsSummary(currentDate: LocalDate): AnalyticsSummary {
        syncToday(currentDate)
        return dbHelper.getAnalyticsSummary(currentDate)
    }

    override fun syncToday(date: LocalDate) {
        for (pkg in AppConfig.trackedPackages) {
            val usage = UsageStore.getAppUsage(context, pkg)
            val record = DailyUsageRecord(
                date = date,
                packageName = pkg,
                secondsUsed = usage.secondsToday,
                reelsCount = usage.reelsCountToday,
                limitMinutes = usage.dailyTimeLimitMinutes,
                reelLimit = usage.dailyReelLimit,
                isLimitEnabled = usage.isLimitEnabled,
                dataStatus = "OK"
            )
            dbHelper.saveOrUpdateRecord(record)
        }
    }

    override fun clearHistory() {
        dbHelper.clearHistory()
    }

    override fun exportToCsv(): String {
        syncToday(LocalDate.now())
        return dbHelper.exportToCsv()
    }
}

/**
 * SharedPreferences-based implementation preserved for backward compatibility and lightweight testing.
 */
class PreferencesUsageRepository(
    private val context: Context
) : UsageRepository {

    private fun prefs() =
        context.applicationContext.getSharedPreferences("reelguard_history", Context.MODE_PRIVATE)

    private fun recordKey(date: LocalDate, packageName: String) =
        "rec_${packageName}_$date"

    override fun saveRecord(record: DailyUsageRecord) {
        val editor = prefs().edit()
        val key = recordKey(record.date, record.packageName)
        editor.putLong("${key}_sec", record.secondsUsed)
        editor.putInt("${key}_reels", record.reelsCount)
        editor.putInt("${key}_lim_min", record.limitMinutes)
        editor.putInt("${key}_lim_reels", record.reelLimit)
        editor.putBoolean("${key}_lim_en", record.isLimitEnabled)
        editor.apply()
    }

    override fun getRecord(date: LocalDate, packageName: String): DailyUsageRecord {
        if (date == LocalDate.now()) {
            val usage = UsageStore.getAppUsage(context, packageName)
            return DailyUsageRecord(
                date = date,
                packageName = packageName,
                secondsUsed = usage.secondsToday,
                reelsCount = usage.reelsCountToday,
                limitMinutes = usage.dailyTimeLimitMinutes,
                reelLimit = usage.dailyReelLimit,
                isLimitEnabled = usage.isLimitEnabled
            )
        }

        val p = prefs()
        val key = recordKey(date, packageName)
        val sec = p.getLong("${key}_sec", 0L)
        val reels = p.getInt("${key}_reels", 0)
        val limMin = p.getInt("${key}_lim_min", AppConfig.DEFAULT_DAILY_LIMIT_MINUTES)
        val limR = p.getInt("${key}_lim_reels", AppConfig.DEFAULT_DAILY_REEL_LIMIT)
        val limEn = p.getBoolean("${key}_lim_en", true)

        return DailyUsageRecord(
            date = date,
            packageName = packageName,
            secondsUsed = sec,
            reelsCount = reels,
            limitMinutes = limMin,
            reelLimit = limR,
            isLimitEnabled = limEn
        )
    }

    override fun getHistory(days: Int, untilDate: LocalDate): List<DailyUsageRecord> {
        val records = mutableListOf<DailyUsageRecord>()
        for (i in 0 until days) {
            val date = untilDate.minusDays(i.toLong())
            for (pkg in AppConfig.trackedPackages) {
                records.add(getRecord(date, pkg))
            }
        }
        return records
    }

    override fun getDateRangeHistory(startDate: LocalDate, endDate: LocalDate): List<DailyUsageRecord> {
        val records = mutableListOf<DailyUsageRecord>()
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            for (pkg in AppConfig.trackedPackages) {
                records.add(getRecord(curr, pkg))
            }
            curr = curr.plusDays(1)
        }
        return records
    }

    override fun getWeeklySummary(endDate: LocalDate): WeeklySummary {
        val startDate = endDate.minusDays(6)
        val records = getHistory(days = 7, untilDate = endDate)
        return WeeklySummary.fromRecords(records, startDate, endDate)
    }

    override fun getMonthlySummary(year: Int, month: Int): MonthlySummary {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        val records = getDateRangeHistory(startDate, endDate)
        val totalSec = records.sumOf { it.secondsUsed }
        val daysInMonth = startDate.lengthOfMonth().coerceAtLeast(1)
        val breakdown = mutableMapOf<String, Long>()
        for (r in records) {
            breakdown[r.packageName] = (breakdown[r.packageName] ?: 0L) + r.secondsUsed
        }
        val mostUsed = breakdown.maxByOrNull { it.value }?.key
        return MonthlySummary(
            year = year,
            month = month,
            totalSeconds = totalSec,
            dailyAverageSeconds = totalSec / daysInMonth,
            mostUsedPackage = mostUsed,
            appBreakdown = breakdown
        )
    }

    override fun getAnalyticsSummary(currentDate: LocalDate): AnalyticsSummary {
        val today = getDateRangeHistory(currentDate, currentDate).sumOf { it.secondsUsed }
        val week = getDateRangeHistory(currentDate.minusDays(6), currentDate).sumOf { it.secondsUsed }
        val month = getDateRangeHistory(currentDate.minusDays(29), currentDate).sumOf { it.secondsUsed }
        return AnalyticsSummary(
            totalSecondsToday = today,
            totalSecondsThisWeek = week,
            totalSecondsThisMonth = month,
            dailyAverageSeconds = week / 7L,
            mostUsedPackage = null,
            changePercentageVsPreviousWeek = null
        )
    }

    override fun syncToday(date: LocalDate) {
        for (pkg in AppConfig.trackedPackages) {
            val rec = getRecord(date, pkg)
            saveRecord(rec)
        }
    }

    override fun clearHistory() {
        prefs().edit().clear().apply()
    }

    override fun exportToCsv(): String {
        val records = getHistory(days = 180, untilDate = LocalDate.now())
        val sb = StringBuilder("Date,App,PackageName,UsageMinutes,UsageSeconds,DailyLimitMinutes,Status\n")
        for (r in records) {
            sb.append("${r.date},${r.appDisplayName},${r.packageName},${r.secondsUsed / 60},${r.secondsUsed},${r.limitMinutes},${r.dataStatus}\n")
        }
        return sb.toString()
    }
}
