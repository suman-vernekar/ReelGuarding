package com.example.reelguard.tracking

data class UsageEventModel(
    val packageName: String,
    val timestamp: Long,
    val eventType: Int
) {
    companion object {
        const val MOVE_TO_FOREGROUND = 1
        const val MOVE_TO_BACKGROUND = 2
        const val ACTIVITY_RESUMED = 7
        const val ACTIVITY_PAUSED = 8
        const val SCREEN_INTERACTIVE = 15
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_SHOWN = 17
        const val KEYGUARD_HIDDEN = 18

        fun isForeground(eventType: Int): Boolean =
            eventType == MOVE_TO_FOREGROUND || eventType == ACTIVITY_RESUMED

        fun isBackground(eventType: Int): Boolean =
            eventType == MOVE_TO_BACKGROUND || eventType == ACTIVITY_PAUSED

        fun isScreenLock(eventType: Int): Boolean =
            eventType == SCREEN_NON_INTERACTIVE || eventType == KEYGUARD_SHOWN

        fun isScreenUnlock(eventType: Int): Boolean =
            eventType == SCREEN_INTERACTIVE || eventType == KEYGUARD_HIDDEN
    }
}
