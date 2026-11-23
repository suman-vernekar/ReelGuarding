package com.example.reelguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.reelguard.detector.DetectionResult
import com.example.reelguard.detector.NodeSnapshot
import com.example.reelguard.detector.PlatformDetectorRegistry
import com.example.reelguard.detector.PlatformReelDetector

/**
 * Accessibility service tracking short-video feed changes locally.
 * Uses isolated platform detectors (Instagram, YouTube, Facebook)
 * to observe feed scroll transitions rather than screen redraws.
 *
 * PRIVACY GUARANTEE:
 * Does not inspect or log passwords, personal chats, or searches.
 * Screen content is processed exclusively in-memory and never transmitted.
 */
class ReelAccessibilityService : AccessibilityService() {

    private var lastContentCheckTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100L
            info.packageNames = AppConfig.trackedPackages.toTypedArray()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (_: Exception) {}

        try {
            if (com.example.reelguard.util.PermissionHelper.hasUsageAccess(this)) {
                startForegroundService(android.content.Intent(this, UsageMonitorService::class.java))
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "ReelGuardCounter"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in AppConfig.trackedPackages) {
            if (pkg != "com.example.reelguard") {
                com.example.reelguard.tracking.ActiveAppTracker.onNonTrackedAppForegrounded(pkg)
                com.example.reelguard.detector.PlatformDetectorRegistry.onAllAppsBackgrounded()
                OverlayController.onPackageBackgrounded()
            }
            return
        }

        com.example.reelguard.tracking.ActiveAppTracker.onAccessibilityEvent(pkg)

        // Inform registry that tracked app is foregrounded and update overlay
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            com.example.reelguard.detector.PlatformDetectorRegistry.onAppForegrounded(pkg)
            if (android.provider.Settings.canDrawOverlays(this)) {
                OverlayController.show(this, pkg)
            }
        }

        // Early-filter irrelevant event types to keep Instagram 60/120fps smooth
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val now = SystemClock.elapsedRealtime()

        // Content changes fire dozens of times per second during video playback (timebar, animation).
        // Throttle to at most once per 350ms to eliminate UI lag.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheckTime < 350L) {
                return
            }
            lastContentCheckTime = now
        }

        val eventTypeName = try {
            AccessibilityEvent.eventTypeToString(event.eventType)
        } catch (_: Exception) {
            "EVENT_${event.eventType}"
        }
        Log.d(TAG, "[Event] pkg=$pkg, eventType=$eventTypeName (${event.eventType})")

        val startTime = SystemClock.elapsedRealtime()
        val targetRoot = findTargetRoot(pkg, event)
        if (targetRoot == null) {
            Log.d(TAG, "[TargetRoot] Target window root not available for $pkg, skipping event")
            return
        }

        val detector = PlatformDetectorRegistry.getDetectorForPackage(pkg)
        if (detector == null) {
            Log.w(TAG, "[Detector] No detector registered for $pkg")
            return
        }

        val snapshot = snapshotNode(targetRoot)
        val result = detector.analyze(snapshot, now, event.eventType)
        val durationMs = SystemClock.elapsedRealtime() - startTime

        tryRecordReelTransition(pkg, detector, result, durationMs)
    }

    private var lastProcessedTransitionSignature: String? = null
    private var lastProcessedTransitionPackage: String? = null

    /**
     * Centralized counting function: the ONLY place in the application permitted
     * to increment the Reel counter.
     * Enforces the 5 mandatory prerequisites before persisting or broadcasting a count:
     * 1. Supported app is in the foreground.
     * 2. Video/Reels viewer is positively identified.
     * 3. Candidate represents a genuine new visible Reel rather than a redraw.
     * 4. Current screen state still permits counting.
     * 5. The same transition has not already been processed.
     */
    @Synchronized
    private fun tryRecordReelTransition(
        pkg: String,
        detector: PlatformReelDetector,
        result: DetectionResult,
        durationMs: Long
    ): Boolean {
        val currentFeedState = detector.feedState

        // 1. Supported app is in the foreground
        val isForeground = pkg in AppConfig.trackedPackages &&
            com.example.reelguard.tracking.ActiveAppTracker.isRecentlyActive(pkg)
        if (!isForeground) {
            Log.d(TAG, "[CentralCounter] Rejected: App $pkg is not in foreground")
            return false
        }

        // 2. The actual video/Reels viewer is positively identified
        val isViewerActive = currentFeedState == com.example.reelguard.detector.VideoFeedState.VIDEO_FEED_ACTIVE ||
            currentFeedState == com.example.reelguard.detector.VideoFeedState.VIDEO_TRANSITION_PENDING
        if (!isViewerActive) {
            Log.d(TAG, "[CentralCounter] Rejected: Viewer is not active (state=$currentFeedState)")
            return false
        }

        // 3. The candidate represents a new visible Reel rather than a redraw
        if (!result.isTransition) {
            Log.d(TAG, "[Decision] pkg=$pkg, transition=false, state=$currentFeedState, reason=${result.diagnosticReason}, elapsed=${durationMs}ms")
            return false
        }

        // 4. Current screen state still permits counting
        if (currentFeedState == com.example.reelguard.detector.VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN ||
            currentFeedState == com.example.reelguard.detector.VideoFeedState.OUTSIDE_SUPPORTED_APP ||
            currentFeedState == com.example.reelguard.detector.VideoFeedState.UNKNOWN) {
            Log.d(TAG, "[CentralCounter] Rejected: Screen state $currentFeedState does not permit counting")
            return false
        }

        val signature = result.signature.orEmpty()
        val safeId = if (signature.isNotBlank()) {
            val crc = java.util.zip.CRC32()
            crc.update(signature.toByteArray(Charsets.UTF_8))
            "id#" + java.lang.Long.toHexString(crc.value)
        } else "none"

        // 5. Verify the same transition has not already been processed
        if (lastProcessedTransitionSignature == signature && lastProcessedTransitionPackage == pkg) {
            Log.d(TAG, "[CentralCounter] Rejected: Transition already processed for $pkg (id=$safeId)")
            return false
        }

        // All 5 preconditions satisfied! Execute centralized increment
        val oldCount = UsageStore.getReelCount(this, pkg)
        val newCount = UsageStore.incrementReels(this, pkg, signature)
        lastProcessedTransitionSignature = signature
        lastProcessedTransitionPackage = pkg

        Log.i(TAG, "[Transition] pkg=$pkg, transition=true, state=$currentFeedState, reason=${result.diagnosticReason}, id=$safeId, elapsed=${durationMs}ms")
        Log.i(TAG, "[Counter] pkg=$pkg counter incremented: $oldCount -> $newCount")

        // Instantly refresh the on-screen overlay HUD with the incremented count
        if (android.provider.Settings.canDrawOverlays(this)) {
            OverlayController.show(this, pkg)
        }
        Log.d(TAG, "[UI] Live UI update published to overlay HUD and dashboard listeners (count=$newCount)")

        // Immediately check and trigger limit enforcement if limit reached
        val limitEval = LimitEnforcer.evaluate(this, pkg)
        if (limitEval.shouldTriggerLimitAlert) {
            val usage = UsageStore.getAppUsage(this, pkg)
            val breakManager = com.example.reelguard.breakremind.BreakExperienceManager()
            if (breakManager.isEligibleForBreakPrompt(pkg, System.currentTimeMillis())) {
                val breakIntent = breakManager.createBreakIntent(
                    context = this,
                    packageName = pkg,
                    timeUsedSeconds = usage.secondsToday,
                    reelsCount = usage.reelsCountToday,
                    timeLimitMinutes = usage.dailyTimeLimitMinutes,
                    reelLimit = usage.dailyReelLimit
                )
                breakIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(breakIntent)
                } catch (_: Exception) {}
            }
        }
        return true
    }

    /**
     * Resolves the target app's node subtree for inspection without being shadowed
     * by ReelGuard's floating overlay window.
     * Prioritizes the consistent top-level window container of targetPkg.
     */
    private fun findTargetRoot(targetPkg: String, event: AccessibilityEvent): AccessibilityNodeInfo? {
        // 1. Prefer rootInActiveWindow if it matches targetPkg to inspect consistent window root
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null && activeRoot.packageName?.toString() == targetPkg) {
                return activeRoot
            }
        } catch (_: Exception) {}

        // 2. Search interactive windows for target app window
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (w in windowList) {
                    val r = w.root ?: continue
                    if (r.packageName?.toString() == targetPkg) {
                        return r
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: climb event.source to the top container within targetPkg
        try {
            val src = event.source
            if (src != null) {
                val srcPkg = src.packageName?.toString()
                if (srcPkg == targetPkg || srcPkg.isNullOrBlank()) {
                    var curr: AccessibilityNodeInfo = src
                    while (true) {
                        val p = curr.parent ?: break
                        val pPkg = p.packageName?.toString()
                        if (pPkg == targetPkg || pPkg.isNullOrBlank()) {
                            curr = p
                        } else {
                            break
                        }
                    }
                    return curr
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback: event.source directly
        try {
            val src = event.source
            if (src != null && event.packageName?.toString() == targetPkg) {
                return src
            }
        } catch (_: Exception) {}

        return null
    }

    private fun snapshotNode(node: AccessibilityNodeInfo?, depth: Int = 0, nodeCounter: IntArray = intArrayOf(0)): NodeSnapshot {
        if (node == null) return NodeSnapshot()

        // Never process password or protected input fields
        val isPassword = try { node.isPassword } catch (_: Exception) { false }
        if (isPassword) {
            return NodeSnapshot(className = "PasswordProtected", isPassword = true)
        }

        val pkgName = try { node.packageName?.toString().orEmpty() } catch (_: Exception) { "" }
        val clsName = try { node.className?.toString().orEmpty() } catch (_: Exception) { "" }
        val desc = try { node.contentDescription?.toString() } catch (_: Exception) { null }
        val resId = try { node.viewIdResourceName } catch (_: Exception) { null }
        val txt = try { node.text?.toString() } catch (_: Exception) { null }
        val scrollable = try { node.isScrollable } catch (_: Exception) { false }
        val selected = try { node.isSelected } catch (_: Exception) { false }
        val cCount = try { node.childCount } catch (_: Exception) { 0 }

        if (depth > 12 || nodeCounter[0] >= 60) {
            return NodeSnapshot(
                packageName = pkgName,
                className = clsName,
                contentDescription = desc,
                viewIdResourceName = resId,
                text = txt,
                isScrollable = scrollable,
                isSelected = selected,
                childCount = cCount
            )
        }

        nodeCounter[0]++

        val children = mutableListOf<NodeSnapshot>()
        for (i in 0 until cCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            children.add(snapshotNode(child, depth + 1, nodeCounter))
        }

        return NodeSnapshot(
            packageName = pkgName,
            className = clsName,
            contentDescription = desc,
            viewIdResourceName = resId,
            text = txt,
            isScrollable = scrollable,
            isSelected = selected,
            childCount = cCount,
            children = children
        )
    }

    override fun onDestroy() {
        PlatformDetectorRegistry.resetAll()
        super.onDestroy()
    }

    override fun onInterrupt() {
        PlatformDetectorRegistry.resetAll()
    }
}
