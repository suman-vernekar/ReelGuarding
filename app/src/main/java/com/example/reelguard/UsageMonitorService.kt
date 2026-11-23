package com.example.reelguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.example.reelguard.tracking.UsageEventModel
import com.example.reelguard.tracking.UsageTrackerEngine
import com.example.reelguard.util.PermissionHelper
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground usage monitoring service.
 * Continuously tracks foreground usage for configured social apps using UsageStatsManager.
 *
 * NOTE ON HARDWARE/OEM BEHAVIOR:
 * On certain OEM Android distributions (e.g. Xiaomi MIUI/HyperOS, Samsung OneUI background battery policies),
 * UsageStats events may be delivered in delayed batches or throttled when battery saver is active.
 * ReelGuard uses an off-thread sliding window to correlate events and gracefully handles missing/delayed ticks.
 */
class UsageMonitorService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundExecutor: ScheduledExecutorService? = null

    private var activePackage: String? = null
    private var lastTickMs = 0L
    private var lastObservedDate: LocalDate = LocalDate.now()
    private var isScreenInteractive = true
    private val breakManager = com.example.reelguard.breakremind.BreakExperienceManager()

    companion object {
        private const val CHANNEL_ID = "reelguard_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1000L
        private const val QUERY_WINDOW_MS = 15 * 60_000L
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    com.example.reelguard.tracking.ActiveAppTracker.reset()
                    mainHandler.post { OverlayController.hide() }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    lastTickMs = System.currentTimeMillis()
                }
            }
        }
    }

    private val reelListener = UsageStore.OnReelCountChangeListener { pkg, _ ->
        mainHandler.post {
            updateNotificationContent(activePackage ?: pkg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerScreenReceiver()
        UsageStore.addReelCountListener(reelListener)
        lastTickMs = System.currentTimeMillis()
        lastObservedDate = LocalDate.now()

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor?.scheduleWithFixedDelay(
            { performMonitoringTick() },
            0L,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun performMonitoringTick() {
        if (!isScreenInteractive) {
            return
        }

        if (!PermissionHelper.hasUsageAccess(this)) {
            mainHandler.post { OverlayController.hide() }
            return
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Handle daily reset across midnight boundaries
        if (UsageTrackerEngine.isDateChanged(lastObservedDate, today)) {
            lastObservedDate = today
        }

        val currentPackage = queryCurrentForegroundPackage(now)

        if (activePackage != null && activePackage == currentPackage) {
            val elapsedSeconds = UsageTrackerEngine.calculateElapsedSeconds(
                previousTickMs = lastTickMs,
                currentTickMs = now
            )
            if (elapsedSeconds > 0) {
                UsageStore.addSeconds(this, activePackage!!, elapsedSeconds)
            }
        }

        activePackage = currentPackage
        lastTickMs = now

        if (currentPackage != null) {
            val limitEval = LimitEnforcer.evaluate(this, currentPackage, today)
            if (limitEval.shouldTriggerLimitAlert && breakManager.isEligibleForBreakPrompt(currentPackage, now)) {
                val usage = UsageStore.getAppUsage(this, currentPackage)
                val breakIntent = breakManager.createBreakIntent(
                    context = this,
                    packageName = currentPackage,
                    timeUsedSeconds = usage.secondsToday,
                    reelsCount = usage.reelsCountToday,
                    timeLimitMinutes = usage.dailyTimeLimitMinutes,
                    reelLimit = usage.dailyReelLimit
                )
                mainHandler.post {
                    try {
                        startActivity(breakIntent)
                    } catch (_: Exception) {}
                }
            }
        }

        mainHandler.post {
            if (currentPackage != null && Settings.canDrawOverlays(this)) {
                OverlayController.show(this, currentPackage)
            } else {
                OverlayController.hide()
            }
            updateNotificationContent(currentPackage)
        }
    }

    private fun updateNotificationContent(currentPackage: String?) {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val title: String
            val text: String
            if (currentPackage != null) {
                val app = com.example.reelguard.model.SupportedApp.fromPackageName(currentPackage)
                val name = app?.displayName ?: currentPackage
                val secs = UsageStore.getSeconds(this, currentPackage)
                val limit = UsageStore.limitMinutes(this)
                val status = com.example.reelguard.util.WellbeingEmojiHelper.getTimeWellbeingStatus(secs, limit, true)
                title = "${status.emoji} ReelGuard: $name"
                text = "Today: ${com.example.reelguard.model.AppUsageData.formatDuration(secs)} screen time • ${status.title}"
            } else {
                val igSecs = UsageStore.getSeconds(this, "com.instagram.android")
                val ytSecs = UsageStore.getSeconds(this, "com.google.android.youtube")
                val fbSecs = UsageStore.getSeconds(this, "com.facebook.katana")
                val totalSecs = igSecs + ytSecs + fbSecs
                val limit = UsageStore.limitMinutes(this)
                val status = com.example.reelguard.util.WellbeingEmojiHelper.getTimeWellbeingStatus(totalSecs, limit, true)
                title = "${status.emoji} ReelGuard Active"
                text = "Today: ${com.example.reelguard.model.AppUsageData.formatDuration(totalSecs)} total usage"
            }

            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun queryCurrentForegroundPackage(now: Long): String? {
        val active = activePackage
        if (active != null && com.example.reelguard.tracking.ActiveAppTracker.isRecentlyActive(active, now, timeoutMs = 8_000L)) {
            return active
        }
        val accessPkg = com.example.reelguard.tracking.ActiveAppTracker.activeAccessibilityPackage
        if (accessPkg != null && com.example.reelguard.tracking.ActiveAppTracker.isRecentlyActive(accessPkg, now, timeoutMs = 5_000L)) {
            return accessPkg
        }

        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return active
        val start = now - QUERY_WINDOW_MS
        val events = try {
            usm.queryEvents(start, now)
        } catch (_: Exception) {
            return active
        }

        val eventList = mutableListOf<UsageEventModel>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            eventList.add(
                UsageEventModel(
                    packageName = pkg,
                    timestamp = event.timeStamp,
                    eventType = event.eventType
                )
            )
        }

        // Delegate to pure testable tracker engine
        val detected = UsageTrackerEngine.determineForegroundPackage(
            events = eventList,
            trackedPackages = AppConfig.trackedPackages,
            currentTimestamp = now
        )

        // Resilient fallback for OEM skins or steady in-app viewing:
        if (detected == null && activePackage != null) {
            if (com.example.reelguard.tracking.ActiveAppTracker.isRecentlyActive(activePackage!!, now, timeoutMs = 15_000L)) {
                return activePackage
            }
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
            val lastUsed = stats?.maxByOrNull { it.lastTimeUsed }
            if (lastUsed != null && lastUsed.packageName == activePackage &&
                (now - lastUsed.lastTimeUsed) < 120_000L) {
                return activePackage
            }
        }

        return detected
    }

    private fun startInForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ReelGuard Monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Monitors digital wellbeing usage for tracked apps"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ReelGuard Active")
            .setContentText("Monitoring screen time for Instagram, YouTube, and Facebook.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        try {
            UsageStore.removeReelCountListener(reelListener)
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        }
        backgroundExecutor?.shutdownNow()
        backgroundExecutor = null
        mainHandler.post { OverlayController.hide() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
