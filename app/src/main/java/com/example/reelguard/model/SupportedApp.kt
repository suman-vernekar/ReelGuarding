package com.example.reelguard.model

enum class SupportedApp(
    val packageName: String,
    val displayName: String,
    val shortName: String,
    val shortVideoName: String
) {
    INSTAGRAM(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        shortName = "Instagram",
        shortVideoName = "Reels"
    ),
    YOUTUBE(
        packageName = "com.google.android.youtube",
        displayName = "YouTube",
        shortName = "YouTube",
        shortVideoName = "Shorts"
    ),
    FACEBOOK(
        packageName = "com.facebook.katana",
        displayName = "Facebook",
        shortName = "Facebook",
        shortVideoName = "Reels"
    );

    companion object {
        val ALL_PACKAGES: Set<String> = entries.map { it.packageName }.toSet()

        fun fromPackageName(packageName: String?): SupportedApp? {
            if (packageName == null) return null
            if (packageName.startsWith("com.instagram")) return INSTAGRAM
            if (packageName.startsWith("com.google.android.youtube")) return YOUTUBE
            if (packageName.startsWith("com.facebook")) return FACEBOOK
            return entries.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
        }

        fun isSupported(packageName: String?): Boolean {
            return fromPackageName(packageName) != null
        }
    }
}
