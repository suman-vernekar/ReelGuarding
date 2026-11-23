package com.example.reelguard.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.reelguard.AppConfig
import com.example.reelguard.data.AnalyticsSummary
import com.example.reelguard.data.DailyUsageRecord
import com.example.reelguard.data.MonthlySummary
import com.example.reelguard.data.WeeklySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * SQLite-backed persistent storage guaranteeing at least 6 months (180+ days)
 * of durable, atomic usage history records per application.
 *
 * Survives app restarts, device reboots, and daily counter resets.
 */
class UsageDatabaseHelper(private val context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "reelguard_analytics.db"
        const val DATABASE_VERSION = 1

        const val TABLE_USAGE = "daily_usage_history"
        const val COL_ID = "id"
        const val COL_DATE = "date"
        const val COL_PACKAGE = "package_name"
        const val COL_SECONDS = "seconds_used"
        const val COL_REELS = "reels_count"
        const val COL_LIMIT_MIN = "limit_minutes"
        const val COL_LIMIT_ENABLED = "limit_enabled"
        const val COL_DATA_STATUS = "data_status"

        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE $TABLE_USAGE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DATE TEXT NOT NULL,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_SECONDS INTEGER NOT NULL DEFAULT 0,
                $COL_REELS INTEGER NOT NULL DEFAULT 0,
                $COL_LIMIT_MIN INTEGER NOT NULL DEFAULT 30,
                $COL_LIMIT_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_DATA_STATUS TEXT NOT NULL DEFAULT 'OK',
                UNIQUE($COL_DATE, $COL_PACKAGE) ON CONFLICT REPLACE
            )
        """.trimIndent()
        db.execSQL(createSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_date ON $TABLE_USAGE($COL_DATE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_pkg ON $TABLE_USAGE($COL_PACKAGE);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe incremental migrations if schema changes in future versions
    }

    fun saveOrUpdateRecord(record: DailyUsageRecord) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DATE, record.date.format(DATE_FORMATTER))
            put(COL_PACKAGE, record.packageName)
            put(COL_SECONDS, record.secondsUsed)
            put(COL_REELS, record.reelsCount)
            put(COL_LIMIT_MIN, record.limitMinutes)
            put(COL_LIMIT_ENABLED, if (record.isLimitEnabled) 1 else 0)
            put(COL_DATA_STATUS, record.dataStatus)
        }
        db.insertWithOnConflict(TABLE_USAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getRecord(date: LocalDate, packageName: String): DailyUsageRecord? {
        val db = readableDatabase
        val dateStr = date.format(DATE_FORMATTER)
        val cursor = db.query(
            TABLE_USAGE,
            null,
            "$COL_DATE = ? AND $COL_PACKAGE = ?",
            arrayOf(dateStr, packageName),
            null,
            null,
            null
        ) ?: return null

        return cursor.use {
            if (it.moveToFirst()) {
                val sec = it.getLong(it.getColumnIndexOrThrow(COL_SECONDS))
                val reels = it.getInt(it.getColumnIndexOrThrow(COL_REELS))
                val limMin = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_MIN))
                val limEn = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_ENABLED)) == 1
                val status = it.getString(it.getColumnIndexOrThrow(COL_DATA_STATUS)) ?: "OK"
                DailyUsageRecord(
                    date = date,
                    packageName = packageName,
                    secondsUsed = sec,
                    reelsCount = reels,
                    limitMinutes = limMin,
                    isLimitEnabled = limEn,
                    dataStatus = status
                )
            } else null
        }
    }

    fun getDateRangeRecords(startDate: LocalDate, endDate: LocalDate): List<DailyUsageRecord> {
        val db = readableDatabase
        val startStr = startDate.format(DATE_FORMATTER)
        val endStr = endDate.format(DATE_FORMATTER)
        val list = mutableListOf<DailyUsageRecord>()

        val cursor = db.query(
            TABLE_USAGE,
            null,
            "$COL_DATE >= ? AND $COL_DATE <= ?",
            arrayOf(startStr, endStr),
            null,
            null,
            "$COL_DATE ASC, $COL_PACKAGE ASC"
        ) ?: return list

        cursor.use {
            while (it.moveToNext()) {
                val dStr = it.getString(it.getColumnIndexOrThrow(COL_DATE))
                val pkg = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE))
                val sec = it.getLong(it.getColumnIndexOrThrow(COL_SECONDS))
                val reels = it.getInt(it.getColumnIndexOrThrow(COL_REELS))
                val limMin = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_MIN))
                val limEn = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_ENABLED)) == 1
                val status = it.getString(it.getColumnIndexOrThrow(COL_DATA_STATUS)) ?: "OK"

                val parsedDate = try {
                    LocalDate.parse(dStr, DATE_FORMATTER)
                } catch (_: Exception) {
                    continue
                }

                list.add(
                    DailyUsageRecord(
                        date = parsedDate,
                        packageName = pkg,
                        secondsUsed = sec,
                        reelsCount = reels,
                        limitMinutes = limMin,
                        isLimitEnabled = limEn,
                        dataStatus = status
                    )
                )
            }
        }
        return list
    }

    fun getHistoryRecords(days: Int = 180, untilDate: LocalDate = LocalDate.now()): List<DailyUsageRecord> {
        val startDate = untilDate.minusDays(days.toLong() - 1)
        return getDateRangeRecords(startDate, untilDate)
    }

    fun getWeeklySummary(endDate: LocalDate = LocalDate.now()): WeeklySummary {
        val startDate = endDate.minusDays(6)
        val records = getDateRangeRecords(startDate, endDate)
        return WeeklySummary.fromRecords(records, startDate, endDate)
    }

    fun getMonthlySummary(year: Int, month: Int): MonthlySummary {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        val records = getDateRangeRecords(startDate, endDate)

        val totalSec = records.sumOf { it.secondsUsed }
        val daysInMonth = startDate.lengthOfMonth().coerceAtLeast(1)
        val dailyAvg = totalSec / daysInMonth

        val breakdown = mutableMapOf<String, Long>()
        for (r in records) {
            breakdown[r.packageName] = (breakdown[r.packageName] ?: 0L) + r.secondsUsed
        }
        val mostUsed = breakdown.maxByOrNull { it.value }?.key

        return MonthlySummary(
            year = year,
            month = month,
            totalSeconds = totalSec,
            dailyAverageSeconds = dailyAvg,
            mostUsedPackage = mostUsed,
            appBreakdown = breakdown
        )
    }

    fun getAnalyticsSummary(currentDate: LocalDate = LocalDate.now()): AnalyticsSummary {
        val todayRecords = getDateRangeRecords(currentDate, currentDate)
        val todaySec = todayRecords.sumOf { it.secondsUsed }

        // This week (last 7 days)
        val thisWeekRecords = getDateRangeRecords(currentDate.minusDays(6), currentDate)
        val thisWeekSec = thisWeekRecords.sumOf { it.secondsUsed }

        // Last week (days 7 to 13 ago)
        val lastWeekRecords = getDateRangeRecords(currentDate.minusDays(13), currentDate.minusDays(7))
        val lastWeekSec = lastWeekRecords.sumOf { it.secondsUsed }

        // This month (last 30 days)
        val thisMonthRecords = getDateRangeRecords(currentDate.minusDays(29), currentDate)
        val thisMonthSec = thisMonthRecords.sumOf { it.secondsUsed }

        val dailyAvg = thisWeekSec / 7L

        val appTotals = mutableMapOf<String, Long>()
        for (r in thisWeekRecords) {
            appTotals[r.packageName] = (appTotals[r.packageName] ?: 0L) + r.secondsUsed
        }
        val mostUsed = appTotals.maxByOrNull { it.value }?.key

        val changeVsLastWeek: Float? = if (lastWeekSec > 0) {
            ((thisWeekSec.toFloat() - lastWeekSec.toFloat()) / lastWeekSec.toFloat()) * 100f
        } else null

        return AnalyticsSummary(
            totalSecondsToday = todaySec,
            totalSecondsThisWeek = thisWeekSec,
            totalSecondsThisMonth = thisMonthSec,
            dailyAverageSeconds = dailyAvg,
            mostUsedPackage = mostUsed,
            changePercentageVsPreviousWeek = changeVsLastWeek
        )
    }

    fun exportToCsv(): String {
        val records = getHistoryRecords(days = 180, untilDate = LocalDate.now())
        val sb = StringBuilder()
        sb.append("Date,App,UsageSeconds,UsageMinutes,DailyLimitMinutes,DataStatus\n")

        for (r in records) {
            val minutes = r.secondsUsed / 60
            val limitStr = if (r.isLimitEnabled) r.limitMinutes.toString() else "Disabled"
            sb.append("${r.date},${r.appDisplayName},${r.secondsUsed},$minutes,$limitStr,${r.dataStatus}\n")
        }
        return sb.toString()
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_USAGE, null, null)
    }

    fun migrateFromPreferencesIfNeeded() {
        try {
            val db = readableDatabase
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_USAGE", null)
            val count = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }

            if (count == 0) {
                val oldPrefs = context.getSharedPreferences("reelguard_history", Context.MODE_PRIVATE)
                val allKeys = oldPrefs.all
                if (allKeys.isNotEmpty()) {
                    val imported = mutableMapOf<Pair<LocalDate, String>, Long>()
                    for ((k, v) in allKeys) {
                        if (k.endsWith("_sec")) {
                            // format: rec_package_date_sec
                            val parts = k.removeSuffix("_sec").removePrefix("rec_").split("_")
                            if (parts.size >= 2) {
                                val pkg = parts.dropLast(1).joinToString("_")
                                val dateStr = parts.last()
                                try {
                                    val date = LocalDate.parse(dateStr, DATE_FORMATTER)
                                    val sec = (v as? Number)?.toLong() ?: 0L
                                    imported[Pair(date, pkg)] = sec
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    for ((pair, sec) in imported) {
                        saveOrUpdateRecord(
                            DailyUsageRecord(
                                date = pair.first,
                                packageName = pair.second,
                                secondsUsed = sec
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
