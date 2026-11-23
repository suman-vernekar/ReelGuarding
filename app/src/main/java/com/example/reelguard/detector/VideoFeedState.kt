package com.example.reelguard.detector

/**
 * Explicit state machine representing the user's presence inside or outside
 * the dedicated short-form video viewing feed for a supported platform.
 */
enum class VideoFeedState {
    /** The user is currently outside the supported application (launcher, other app, lockscreen). */
    OUTSIDE_SUPPORTED_APP,

    /** The supported app is open, but the user is on a non-video screen (home feed, profile, chats, search). */
    APP_OPEN_NON_VIDEO_SCREEN,

    /** The dedicated vertical short-video feed is actively displayed in the foreground. */
    VIDEO_FEED_ACTIVE,

    /** A rapid swipe or transition gesture is in progress and pending debounce verification. */
    VIDEO_TRANSITION_PENDING,

    /** Screen state cannot be determined with confidence. */
    UNKNOWN
}
