package com.example.reelguard

import android.content.Context
import java.time.LocalDate

object UsageStore {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)

    private fun todayKey(prefix: String) = "${prefix}_${LocalDate.now()}"

    fun getSeconds(context: Context, packageName: String): Long =
        prefs(context).getLong(todayKey("seconds_$packageName"), 0L)

    fun interface OnUsageChangeListener {
        fun onUsageChanged(packageName: String)
    }

    fun interface OnReelCountChangeListener {
        fun onReelCountChanged(packageName: String, newCount: Int)
    }

    private val usageListeners = java.util.concurrent.CopyOnWriteArrayList<OnUsageChangeListener>()
    private val reelCountListeners = java.util.concurrent.CopyOnWriteArrayList<OnReelCountChangeListener>()

    fun addUsageListener(listener: OnUsageChangeListener) {
        usageListeners.add(listener)
    }

    fun removeUsageListener(listener: OnUsageChangeListener) {
        usageListeners.remove(listener)
    }

    fun addReelCountListener(listener: OnReelCountChangeListener) {
        reelCountListeners.add(listener)
    }

    fun removeReelCountListener(listener: OnReelCountChangeListener) {
        reelCountListeners.remove(listener)
    }

    fun addSeconds(context: Context, packageName: String, seconds: Long) {
        if (seconds <= 0) return
        val p = prefs(context)
        val key = todayKey("seconds_$packageName")
        val current = p.getLong(key, 0L)
        p.edit().putLong(key, current + seconds).apply()
        for (l in usageListeners) {
            try { l.onUsageChanged(packageName) } catch (_: Exception) {}
        }
    }

    private const val TAG = "ReelGuardCounter"

    fun getReelCount(context: Context, packageName: String): Int =
        prefs(context).getInt(todayKey("reels_$packageName"), 0)

    fun getLastCountTimestamp(context: Context, packageName: String): Long =
        prefs(context).getLong(todayKey("last_count_ts_$packageName"), 0L)

    fun getLastItemIdentity(context: Context, packageName: String): String? =
        prefs(context).getString(todayKey("last_identity_$packageName"), null)

    @Synchronized
    fun incrementReels(context: Context, packageName: String, itemIdentity: String? = null): Int {
        val p = prefs(context)
        val key = todayKey("reels_$packageName")
        val current = p.getInt(key, 0)
        val next = current + 1
        val now = System.currentTimeMillis()
        val editor = p.edit().putInt(key, next)
        editor.putLong(todayKey("last_count_ts_$packageName"), now)
        if (!itemIdentity.isNullOrBlank()) {
            editor.putString(todayKey("last_identity_$packageName"), itemIdentity)
        }
        editor.apply()

        android.util.Log.i(TAG, "[Storage] Counter persisted for $packageName: $current -> $next (identity=$itemIdentity)")

        for (l in reelCountListeners) {
            try { l.onReelCountChanged(packageName, next) } catch (_: Exception) {}
        }
        for (l in usageListeners) {
            try { l.onUsageChanged(packageName) } catch (_: Exception) {}
        }
        return next
    }

    fun setLimits(context: Context, minutes: Int, reels: Int) {
        prefs(context).edit()
            .putInt("limit_minutes", minutes.coerceAtLeast(1))
            .putInt("limit_reels", reels.coerceAtLeast(1))
            .apply()
    }

    fun limitMinutes(context: Context) =
        prefs(context).getInt("limit_minutes", AppConfig.DEFAULT_DAILY_LIMIT_MINUTES)

    fun limitReels(context: Context) =
        prefs(context).getInt("limit_reels", AppConfig.DEFAULT_DAILY_REEL_LIMIT)

    fun isLimitReached(context: Context, packageName: String): Boolean {
        val seconds = getSeconds(context, packageName)
        val reels = getReelCount(context, packageName)
        return seconds >= limitMinutes(context) * 60L || reels >= limitReels(context)
    }

    fun resetToday(context: Context) {
        val editor = prefs(context).edit()
        for (pkg in AppConfig.trackedPackages) {
            editor.remove(todayKey("seconds_$pkg"))
            editor.remove(todayKey("reels_$pkg"))
            editor.remove(todayKey("last_count_ts_$pkg"))
            editor.remove(todayKey("last_identity_$pkg"))
        }
        editor.apply()
        android.util.Log.i(TAG, "[Storage] Daily counts reset for all tracked packages")
        for (pkg in AppConfig.trackedPackages) {
            for (l in usageListeners) {
                try { l.onUsageChanged(pkg) } catch (_: Exception) {}
            }
        }
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getAppLimitMinutes(context: Context, packageName: String): Int {
        val p = prefs(context)
        return p.getInt("limit_minutes_$packageName", limitMinutes(context))
    }

    fun isAppLimitEnabled(context: Context, packageName: String): Boolean {
        val p = prefs(context)
        return p.getBoolean("limit_enabled_$packageName", true)
    }

    fun setAppLimit(context: Context, packageName: String, minutes: Int, enabled: Boolean) {
        val p = prefs(context)
        p.edit()
            .putInt("limit_minutes_$packageName", minutes.coerceAtLeast(1))
            .putBoolean("limit_enabled_$packageName", enabled)
            .apply()

        for (l in usageListeners) {
            try { l.onUsageChanged(packageName) } catch (_: Exception) {}
        }
    }

    fun getLimitConfig(context: Context, packageName: String): com.example.reelguard.model.AppLimitConfig {
        val p = prefs(context)
        val minutes = p.getInt("limit_minutes_$packageName", limitMinutes(context))
        val reels = p.getInt("limit_reels_$packageName", limitReels(context))
        val limitEnabled = p.getBoolean("limit_enabled_$packageName", true)
        val warningMin = p.getInt("warning_min_$packageName", 5)
        val warningReels = p.getInt("warning_reels_$packageName", 3)
        val overlay = p.getBoolean("overlay_enabled_$packageName", true)
        val reminder = p.getBoolean("reminder_enabled_$packageName", true)
        val policyName = p.getString("policy_$packageName", com.example.reelguard.model.TriggerPolicy.WHICHEVER_FIRST.name)
        val policy = try {
            com.example.reelguard.model.TriggerPolicy.valueOf(policyName ?: com.example.reelguard.model.TriggerPolicy.WHICHEVER_FIRST.name)
        } catch (_: Exception) {
            com.example.reelguard.model.TriggerPolicy.WHICHEVER_FIRST
        }
        return com.example.reelguard.model.AppLimitConfig(
            packageName = packageName,
            dailyTimeLimitMinutes = minutes,
            dailyReelLimit = reels,
            warningThresholdMinutes = warningMin,
            warningThresholdReels = warningReels,
            isLimitEnabled = limitEnabled,
            isOverlayEnabled = overlay,
            isBreakReminderEnabled = reminder,
            triggerPolicy = policy
        )
    }

    fun setLimitConfig(context: Context, config: com.example.reelguard.model.AppLimitConfig) {
        val pkg = config.packageName
        prefs(context).edit()
            .putInt("limit_minutes_$pkg", config.dailyTimeLimitMinutes.coerceAtLeast(1))
            .putInt("limit_reels_$pkg", config.dailyReelLimit.coerceAtLeast(1))
            .putBoolean("limit_enabled_$pkg", config.isLimitEnabled)
            .putInt("warning_min_$pkg", config.warningThresholdMinutes.coerceAtLeast(1))
            .putInt("warning_reels_$pkg", config.warningThresholdReels.coerceAtLeast(1))
            .putBoolean("overlay_enabled_$pkg", config.isOverlayEnabled)
            .putBoolean("reminder_enabled_$pkg", config.isBreakReminderEnabled)
            .putString("policy_$pkg", config.triggerPolicy.name)
            .apply()

        for (l in usageListeners) {
            try { l.onUsageChanged(pkg) } catch (_: Exception) {}
        }
    }

    fun getAppUsage(context: Context, packageName: String): com.example.reelguard.model.AppUsageData {
        val config = getLimitConfig(context, packageName)
        return com.example.reelguard.model.AppUsageData(
            packageName = packageName,
            secondsToday = getSeconds(context, packageName),
            reelsCountToday = getReelCount(context, packageName),
            dailyTimeLimitMinutes = config.dailyTimeLimitMinutes,
            dailyReelLimit = config.dailyReelLimit,
            warningThresholdMinutes = config.warningThresholdMinutes,
            warningThresholdReels = config.warningThresholdReels,
            isLimitEnabled = config.isLimitEnabled
        )
    }

    fun getAllAppsUsage(context: Context): List<com.example.reelguard.model.AppUsageData> {
        return AppConfig.trackedPackages.map { pkg ->
            getAppUsage(context, pkg)
        }
    }
}
