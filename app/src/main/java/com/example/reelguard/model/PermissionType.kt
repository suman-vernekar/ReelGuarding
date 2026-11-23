package com.example.reelguard.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.reelguard.util.PermissionHelper

enum class PermissionType(
    val title: String,
    val rationale: String,
    val isCritical: Boolean,
    val securityDisclosure: String,
    val settingsAction: String
) {
    USAGE_ACCESS(
        title = "Usage Access",
        rationale = "Allows ReelGuard to track foreground active time in Instagram, YouTube, and Facebook.",
        isCritical = true,
        securityDisclosure = "ReelGuard only monitors target packages to calculate screen time. No other application usage is stored or shared.",
        settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
    ),
    OVERLAY(
        title = "Display Over Other Apps",
        rationale = "Allows the floating counter overlay and gentle break prompts to appear while watching videos.",
        isCritical = true,
        securityDisclosure = "The overlay is small, unobtrusive, and transparent. It never captures keystrokes or covers touch controls maliciously.",
        settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
    ),
    ACCESSIBILITY(
        title = "Accessibility Service (Feed Detection)",
        rationale = "Detects active transitions inside social feeds to help you monitor screen time.",
        isCritical = true,
        securityDisclosure = "ReelGuard runs 100% on-device. It NEVER reads messages, passwords, personal chats, or searches. Screen content is never uploaded.",
        settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
    ),
    NOTIFICATIONS(
        title = "Notifications",
        rationale = "Displays the active foreground service indicator and threshold limit warnings.",
        isCritical = false,
        securityDisclosure = "Notifications are only posted locally to alert you when daily viewing limits are approached or reached.",
        settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS
    );

    fun isGranted(context: Context): Boolean {
        return when (this) {
            USAGE_ACCESS -> PermissionHelper.hasUsageAccess(context)
            OVERLAY -> PermissionHelper.hasOverlayPermission(context)
            ACCESSIBILITY -> PermissionHelper.isAccessibilityServiceEnabled(context)
            NOTIFICATIONS -> PermissionHelper.hasNotificationPermission(context)
        }
    }

    fun createSettingsIntent(packageName: String): Intent {
        return when (this) {
            USAGE_ACCESS -> Intent(settingsAction)
            OVERLAY -> Intent(settingsAction, Uri.parse("package:$packageName"))
            ACCESSIBILITY -> Intent(settingsAction)
            NOTIFICATIONS -> Intent(settingsAction).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
    }
}

data class PermissionState(
    val type: PermissionType,
    val isGranted: Boolean
) {
    val statusLabel: String
        get() = if (isGranted) "Granted" else if (type.isCritical) "Action Needed" else "Optional"
}
