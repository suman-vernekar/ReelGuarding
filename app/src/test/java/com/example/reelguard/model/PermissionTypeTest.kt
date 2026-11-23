package com.example.reelguard.model

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTypeTest {

    @Test
    fun permissionTypes_containAllFourRequiredPermissions() {
        val types = PermissionType.entries
        assertEquals(4, types.size)
        assertTrue(types.contains(PermissionType.USAGE_ACCESS))
        assertTrue(types.contains(PermissionType.OVERLAY))
        assertTrue(types.contains(PermissionType.ACCESSIBILITY))
        assertTrue(types.contains(PermissionType.NOTIFICATIONS))
    }

    @Test
    fun criticalClassification_isCorrect() {
        assertTrue(PermissionType.USAGE_ACCESS.isCritical)
        assertTrue(PermissionType.OVERLAY.isCritical)
        assertTrue(PermissionType.ACCESSIBILITY.isCritical)
        assertFalse(PermissionType.NOTIFICATIONS.isCritical)
    }

    @Test
    fun rationalesAndDisclosures_areInformative() {
        for (type in PermissionType.entries) {
            assertTrue(type.title.isNotBlank())
            assertTrue(type.rationale.isNotBlank())
            assertTrue(type.securityDisclosure.isNotBlank())
        }
    }

    @Test
    fun settingsActions_matchAndroidSettingsConstants() {
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, PermissionType.USAGE_ACCESS.settingsAction)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, PermissionType.OVERLAY.settingsAction)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, PermissionType.ACCESSIBILITY.settingsAction)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, PermissionType.NOTIFICATIONS.settingsAction)
    }

    @Test
    fun permissionState_statusLabels() {
        val grantedCritical = PermissionState(PermissionType.USAGE_ACCESS, isGranted = true)
        assertEquals("Granted", grantedCritical.statusLabel)

        val deniedCritical = PermissionState(PermissionType.USAGE_ACCESS, isGranted = false)
        assertEquals("Action Needed", deniedCritical.statusLabel)

        val deniedOptional = PermissionState(PermissionType.NOTIFICATIONS, isGranted = false)
        assertEquals("Optional", deniedOptional.statusLabel)
    }
}
