package com.example.reelguard.tracking

import java.time.LocalDate

object UsageTrackerEngine {

    const val DEFAULT_MAX_TICK_WINDOW_MS = 5000L

    /**
     * Determines which tracked package is currently actively in the foreground.
     * Handles app switching, screen lock/sleep states, duplicate events, and invalid timestamps.
     */
    fun determineForegroundPackage(
        events: List<UsageEventModel>,
        trackedPackages: Set<String>,
        currentTimestamp: Long
    ): String? {
        if (events.isEmpty()) return null

        // Discard invalid timestamps
        val validEvents = events
            .filter { it.timestamp in 1..currentTimestamp + 5000L }
            .sortedBy { it.timestamp }

        if (validEvents.isEmpty()) return null

        val lastFgTime = mutableMapOf<String, Long>()
        val lastBgTime = mutableMapOf<String, Long>()
        var lastLockTime = 0L
        var lastUnlockTime = 0L

        for (event in validEvents) {
            val pkg = event.packageName
            val time = event.timestamp

            when {
                UsageEventModel.isScreenLock(event.eventType) -> {
                    if (time > lastLockTime) lastLockTime = time
                }
                UsageEventModel.isScreenUnlock(event.eventType) -> {
                    if (time > lastUnlockTime) lastUnlockTime = time
                }
                UsageEventModel.isForeground(event.eventType) -> {
                    lastFgTime[pkg] = time
                }
                UsageEventModel.isBackground(event.eventType) -> {
                    lastBgTime[pkg] = time
                }
            }
        }

        var latestForegroundPkg: String? = null
        var latestForegroundTime = 0L

        for ((pkg, fgTime) in lastFgTime) {
            val bgTime = lastBgTime[pkg] ?: 0L
            if (fgTime >= bgTime && fgTime > latestForegroundTime) {
                latestForegroundTime = fgTime
                latestForegroundPkg = pkg
            }
        }

        // If screen was locked after or simultaneously with the latest foreground event, device is locked
        if (lastLockTime > lastUnlockTime && lastLockTime >= latestForegroundTime) {
            return null
        }

        return latestForegroundPkg?.takeIf { it in trackedPackages }
    }

    /**
     * Calculates valid elapsed seconds accrued between monitoring ticks.
     * Clamps the delta to [maxTickWindowMs] to prevent sleep/suspend jumps from accumulating false hours.
     */
    fun calculateElapsedSeconds(
        previousTickMs: Long,
        currentTickMs: Long,
        maxTickWindowMs: Long = DEFAULT_MAX_TICK_WINDOW_MS
    ): Long {
        if (previousTickMs <= 0 || currentTickMs <= previousTickMs) return 0L
        val rawDelta = currentTickMs - previousTickMs
        val clampedDelta = rawDelta.coerceAtMost(maxTickWindowMs)
        return clampedDelta / 1000L
    }

    /**
     * Detects if calendar date has rolled over (midnight boundary or timezone shift).
     */
    fun isDateChanged(previousDate: LocalDate, currentDate: LocalDate): Boolean {
        return previousDate != currentDate
    }

    /**
     * Calculates remaining allowed foreground seconds.
     */
    fun calculateRemainingSeconds(totalSecondsUsed: Long, limitMinutes: Int): Long {
        val limitSeconds = limitMinutes * 60L
        return (limitSeconds - totalSecondsUsed).coerceAtLeast(0L)
    }

    /**
     * Checks if configured time limit has been crossed.
     */
    fun isTimeLimitReached(totalSecondsUsed: Long, limitMinutes: Int): Boolean {
        return totalSecondsUsed >= limitMinutes * 60L
    }
}
