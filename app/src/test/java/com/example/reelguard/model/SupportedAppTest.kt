package com.example.reelguard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAppTest {

    @Test
    fun allPackages_containsAllThreeApps() {
        val packages = SupportedApp.ALL_PACKAGES
        assertEquals(3, packages.size)
        assertTrue(packages.contains("com.instagram.android"))
        assertTrue(packages.contains("com.google.android.youtube"))
        assertTrue(packages.contains("com.facebook.katana"))
    }

    @Test
    fun fromPackageName_returnsCorrectEnum() {
        assertEquals(SupportedApp.INSTAGRAM, SupportedApp.fromPackageName("com.instagram.android"))
        assertEquals(SupportedApp.YOUTUBE, SupportedApp.fromPackageName("com.google.android.youtube"))
        assertEquals(SupportedApp.FACEBOOK, SupportedApp.fromPackageName("com.facebook.katana"))
    }

    @Test
    fun fromPackageName_handlesUnknownOrNull() {
        assertNull(SupportedApp.fromPackageName(null))
        assertNull(SupportedApp.fromPackageName("com.twitter.android"))
        assertNull(SupportedApp.fromPackageName(""))
    }

    @Test
    fun isSupported_identifiesCorrectly() {
        assertTrue(SupportedApp.isSupported("com.instagram.android"))
        assertTrue(SupportedApp.isSupported("com.google.android.youtube"))
        assertTrue(SupportedApp.isSupported("com.facebook.katana"))
        assertFalse(SupportedApp.isSupported("com.unknown.app"))
        assertFalse(SupportedApp.isSupported(null))
    }

    @Test
    fun shortVideoName_matchesPlatformTerm() {
        assertEquals("Reels", SupportedApp.INSTAGRAM.shortVideoName)
        assertEquals("Shorts", SupportedApp.YOUTUBE.shortVideoName)
        assertEquals("Reels", SupportedApp.FACEBOOK.shortVideoName)
    }
}
