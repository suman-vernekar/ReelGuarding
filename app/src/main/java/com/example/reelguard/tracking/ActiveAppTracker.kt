package com.example.reelguard.tracking

import com.example.reelguard.AppConfig

/**
 * Thread-safe tracker correlating real-time UI/accessibility interactions
 * with foreground lifecycle states.
 *
 * Ensures the floating counter and tracking never randomly turn off while
 * the user is actively viewing short videos in a supported app.
 */
object ActiveAppTracker {

    @Volatile
    var activeAccessibilityPackage: String? = null
        private set

    @Volatile
    var lastInteractionTimeMs: Long = 0L
        private set

    /**
     * Called whenever an accessibility event is received for a package.
     */
    fun onAccessibilityEvent(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        if (packageName == "com.example.reelguard") {
            // ReelGuard's internal overlay/dialogs should not alter active app state
            return
        }
        if (packageName in AppConfig.trackedPackages) {
            activeAccessibilityPackage = packageName
            lastInteractionTimeMs = nowMs
        } else {
            // User navigated to launcher, lockscreen, or untracked app - stop active status immediately
            activeAccessibilityPackage = null
            lastInteractionTimeMs = 0L
        }
    }

    /**
     * Explicitly called when an untracked app, home launcher, or system dialog is focused.
     */
    fun onNonTrackedAppForegrounded(packageName: String) {
        if (packageName != "com.example.reelguard") {
            activeAccessibilityPackage = null
            lastInteractionTimeMs = 0L
        }
    }

    /**
     * Returns true if the package was interacted with recently via accessibility.
     */
    fun isRecentlyActive(packageName: String, nowMs: Long = System.currentTimeMillis(), timeoutMs: Long = 5_000L): Boolean {
        if (activeAccessibilityPackage != packageName) return false
        val delta = nowMs - lastInteractionTimeMs
        return delta in 0..timeoutMs
    }

    /**
     * Clears tracked accessibility activity (e.g. on screen off or explicit switch).
     */
    fun reset() {
        activeAccessibilityPackage = null
        lastInteractionTimeMs = 0L
    }
}
