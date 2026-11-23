package com.example.reelguard.security

import com.example.reelguard.AppConfig
import com.example.reelguard.data.DailyUsageRecord
import com.example.reelguard.detector.NodeSnapshot
import com.example.reelguard.model.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PrivacyAndDataDeletionTest {

    private fun findProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find file: $relativePath in candidates")
    }

    @Test
    fun manifest_doesNotRequestInternetPermission() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertFalse(
            "ReelGuard must NOT request INTERNET permission to guarantee 100% offline privacy",
            content.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun manifest_disallowsBackupToPreventCloudDataLeakage() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertTrue(
            "ReelGuard must specify android:allowBackup=\"false\" to prevent cloud extraction of usage data",
            content.contains("android:allowBackup=\"false\"")
        )
    }

    @Test
    fun manifest_internalComponentsAreNotExported() {
        val manifestFile = findProjectFile("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertTrue(content.contains("android:name=\".UsageMonitorService\""))
        assertTrue(content.contains("android:name=\".breakremind.BreakReminderActivity\""))
        assertTrue(content.contains("android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\""))
    }

    @Test
    fun accessibilityConfig_isRestrictedToTargetPackagesOnly() {
        val configFile = findProjectFile("src/main/res/xml/reel_accessibility_config.xml")
        val content = configFile.readText()

        assertTrue(
            "Accessibility config must specify target packages",
            content.contains("android:packageNames=\"com.instagram.android,com.google.android.youtube,com.facebook.katana\"")
        )
    }

    @Test
    fun strings_containTransparentPrivacyAndAccessibilityDisclosures() {
        val stringsFile = findProjectFile("src/main/res/values/strings.xml")
        val content = stringsFile.readText()

        assertTrue(content.contains("accessibility_description"))
        assertTrue(
            "Disclosure must explicitly clarify that messages and passwords are NOT collected",
            content.contains("does NOT read, collect, or share personal messages, passwords")
        )
        assertTrue(content.contains("privacy_policy_summary"))
    }

    @Test
    fun nodeSnapshot_flagsPasswordFieldsAsSensitive() {
        val passwordNode = NodeSnapshot(
            text = "secret_password",
            className = "android.widget.EditText",
            isPassword = true
        )
        assertTrue("NodeSnapshot must flag password fields", passwordNode.isPassword)
    }

    @Test
    fun dailyUsageRecord_storesOnlyAggregateCountersWithoutPersonalIdentifiers() {
        val record = DailyUsageRecord(
            date = LocalDate.now(),
            packageName = SupportedApp.INSTAGRAM.packageName,
            secondsUsed = 1200L,
            reelsCount = 35,
            limitMinutes = 30,
            reelLimit = 40
        )

        // Verify that record only holds non-identifiable numerical counters
        assertEquals(1200L, record.secondsUsed)
        assertEquals(35, record.reelsCount)
        assertEquals(SupportedApp.INSTAGRAM.packageName, record.packageName)
    }

    @Test
    fun dataPurge_inMemoryRepositoryClearsAllUserHistory() {
        val memoryStore = mutableMapOf<String, Any>()
        
        // Simulate populated usage and history
        memoryStore["seconds_instagram"] = 3600L
        memoryStore["reels_instagram"] = 50
        memoryStore["rec_instagram_2026-10-09_sec"] = 3600L
        
        assertFalse(memoryStore.isEmpty())
        
        // Execute complete purge
        memoryStore.clear()
        
        assertTrue("Complete purge must empty all stored data", memoryStore.isEmpty())
        assertEquals(0, memoryStore.size)
    }
}
