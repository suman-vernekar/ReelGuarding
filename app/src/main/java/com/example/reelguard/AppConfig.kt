package com.example.reelguard

object AppConfig {
    // Package names can vary by app version, device, or regional build.
    val trackedPackages = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana"
    )

    const val DEFAULT_DAILY_LIMIT_MINUTES = 30
    const val DEFAULT_DAILY_REEL_LIMIT = 20
    const val PREFS = "reelguard_prefs"
}
