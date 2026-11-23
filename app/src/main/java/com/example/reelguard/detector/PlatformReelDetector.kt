package com.example.reelguard.detector

interface PlatformReelDetector {
    val feedState: VideoFeedState
    fun analyze(snapshot: NodeSnapshot?, nowMs: Long, eventType: Int = 0): DetectionResult
    fun onAppForegrounded()
    fun onAppBackgrounded()
    fun reset()
}

internal val STATIC_NAVIGATION_TERMS = setOf(
    "home", "search", "explore", "reels", "shorts", "subscriptions",
    "library", "you", "create", "notifications", "activity", "messages",
    "direct", "profile", "menu", "feed", "watch", "marketplace",
    "groups", "gaming", "back", "more", "less", "close", "camera", "settings",
    "like", "liked", "like this video", "dislike", "disliked", "dislike this video",
    "comment", "comments", "share", "share this video", "send", "save", "saved",
    "remix", "follow", "following", "subscribe", "subscribed", "join", "joined",
    "audio", "original audio", "use audio", "sound", "sound used", "reply", "replies",
    "views", "add a comment...", "write a comment...", "write a public comment...",
    "top comments", "sort by", "more options", "open menu", "pause", "play",
    "show more", "show less", "tap to unmute", "tap to mute", "original",
    "tap for audio", "react", "view comments"
)

internal fun isProfilePictureText(text: String): Boolean {
    val clean = text.trim().lowercase()
    return clean.contains("profile picture") ||
        clean.contains("profile photo") ||
        clean.contains("profile avatar") ||
        clean.contains("profile image") ||
        clean.contains("double tap to view profile") ||
        clean.endsWith("'s profile") ||
        clean.endsWith("profile picture")
}

internal fun isEngagementOrMetricText(text: String): Boolean {
    val clean = text.trim().lowercase()
    // Pure numbers or metric abbreviations e.g. "1.2M", "540K", "10K", "99+", "1,234"
    if (clean.matches(Regex("^[0-9,.]+[kmb]?$"))) return true
    // Like counters and like-related text e.g. "1.2k likes", "12 likes", "liked by bob and others"
    if (clean.matches(Regex(".*\\b([0-9,.]+[kmb]?\\s+likes?|liked by\\b.*|likes?\\s+button).*"))) return true
    // Comment counters e.g. "view all 42 comments", "15 comments"
    if (clean.matches(Regex(".*\\b(view all\\s+[0-9,.]+\\s+comments?|[0-9,.]+[kmb]?\\s+comments?).*"))) return true
    // View / share / remix counters e.g. "1.5m views", "10 shares"
    if (clean.matches(Regex(".*\\b([0-9,.]+[kmb]?\\s+(views?|shares?|remixes?)).*"))) return true
    // Relative timestamps or video timer e.g. "2h ago", "5m", "3 days ago", "0:15 / 0:30"
    if (clean.matches(Regex("^[0-9]+[smhdwy](\\s+ago)?$")) || clean.matches(Regex("^[0-9]+:[0-9]+(\\s*/\\s*[0-9]+:[0-9]+)?$"))) return true
    if (clean.matches(Regex(".*\\b(ago|hours? ago|minutes? ago|days? ago|weeks? ago)\\b.*"))) return true
    return false
}

internal fun isStaticOrActionText(text: String): Boolean {
    val clean = text.trim().lowercase()
    if (clean.length <= 1) return true
    if (STATIC_NAVIGATION_TERMS.contains(clean)) return true
    if (isProfilePictureText(clean)) return true
    if (isEngagementOrMetricText(clean)) return true
    return false
}

internal fun normalizeContentKey(raw: String): String {
    return raw.lowercase()
        .replace(Regex("[^a-z0-9]"), "")
        .take(20)
}

internal fun isContentMatching(key1: String?, key2: String?): Boolean {
    if (key1.isNullOrBlank() || key2.isNullOrBlank()) return true
    if (key1 == key2) return true
    if (key1.startsWith(key2) || key2.startsWith(key1)) return true
    return false
}

internal fun privacySafeId(canonicalKey: String?): String {
    if (canonicalKey.isNullOrBlank()) return "none"
    val crc = java.util.zip.CRC32()
    crc.update(canonicalKey.toByteArray(Charsets.UTF_8))
    return "id#" + java.lang.Long.toHexString(crc.value)
}

data class ReelIdentity(
    val author: String? = null,
    val audioTrack: String? = null,
    val contentKey: String? = null,
    val structuralHash: String? = null,
    val platformPrefix: String = "author"
) {
    val canonicalKey: String by lazy {
        val parts = mutableListOf<String>()
        if (!author.isNullOrBlank()) parts.add("$platformPrefix:$author")
        if (!audioTrack.isNullOrBlank()) {
            val audioKey = if (platformPrefix == "ch") "snd" else "audio"
            parts.add("$audioKey:$audioTrack")
        }
        if (!contentKey.isNullOrBlank()) {
            val capKey = if (platformPrefix == "ch") "title" else "cap"
            parts.add("$capKey:$contentKey")
        }
        if (parts.isEmpty() && !structuralHash.isNullOrBlank()) parts.add("leaf:$structuralHash")
        parts.joinToString(";")
    }

    val privacySafeId: String
        get() = privacySafeId(canonicalKey)

    val isReliable: Boolean
        get() = !author.isNullOrBlank() || !audioTrack.isNullOrBlank() || !contentKey.isNullOrBlank()
}

internal fun isSameReel(active: ReelIdentity, candidate: ReelIdentity, scrollOccurred: Boolean): Boolean {
    // 1. Author / Channel comparison
    if (!active.author.isNullOrBlank() && !candidate.author.isNullOrBlank()) {
        if (active.author.equals(candidate.author, ignoreCase = true)) {
            // Authors are identical!
            // If no scroll occurred, it is definitively the same visible Reel.
            if (!scrollOccurred) {
                return true
            }
            // A scroll occurred: check if content key or audio still matches (e.g. bounce-back)
            if (isContentMatching(active.contentKey, candidate.contentKey)) {
                return true
            }
            if (!active.audioTrack.isNullOrBlank() && !candidate.audioTrack.isNullOrBlank() &&
                active.audioTrack.equals(candidate.audioTrack, ignoreCase = true)) {
                return true
            }
            // Scrolled to a different video by same author with distinct content!
            return false
        } else {
            // Different authors!
            return false
        }
    }

    // 2. Active had author, but candidate lacks author (partial snapshot / redraw / interaction).
    if (!active.author.isNullOrBlank() && candidate.author.isNullOrBlank()) {
        return true
    }

    // 3. Active lacked author, but candidate now has author (metadata enrichment).
    if (active.author.isNullOrBlank() && !candidate.author.isNullOrBlank()) {
        if (isContentMatching(active.contentKey, candidate.contentKey)) {
            return true
        }
    }

    // 4. Audio track comparison
    if (!active.audioTrack.isNullOrBlank() && !candidate.audioTrack.isNullOrBlank()) {
        if (active.audioTrack.equals(candidate.audioTrack, ignoreCase = true)) {
            if (isContentMatching(active.contentKey, candidate.contentKey)) {
                return true
            }
        }
    }

    // 5. Content key comparison
    if (!active.contentKey.isNullOrBlank() && !candidate.contentKey.isNullOrBlank()) {
        return isContentMatching(active.contentKey, candidate.contentKey)
    }

    // 6. Structural hash comparison
    if (!active.structuralHash.isNullOrBlank() && !candidate.structuralHash.isNullOrBlank()) {
        return active.structuralHash == candidate.structuralHash
    }

    return true // Default to same Reel if uncertain
}

internal fun isStrongSignature(sig: String): Boolean {
    return sig.startsWith("author:") || sig.startsWith("ch:") ||
        sig.contains(";cap:") || sig.contains(";title:") ||
        sig.startsWith("cap:") || sig.startsWith("title:") ||
        sig.contains(";author:") || sig.contains(";ch:")
}

private fun logCounter(msg: String) {
    try {
        android.util.Log.d("ReelGuardCounter", msg)
    } catch (_: Throwable) {
        // Safe for unit test environments without Android framework
    }
}

class InstagramReelDetector(
    private val debounceMs: Long = 350L
) : PlatformReelDetector {

    override var feedState: VideoFeedState = VideoFeedState.OUTSIDE_SUPPORTED_APP
        private set

    private var currentCountedIdentity: ReelIdentity? = null
    private var scrollDetectedSinceLastCount: Boolean = false
    private var lastCountTimestamp: Long = 0L
    private val recentIdentities = LinkedHashSet<String>()

    private fun setFeedState(newState: VideoFeedState, reason: String) {
        if (feedState != newState) {
            val oldState = feedState
            feedState = newState
            logCounter("[StateTransition] Instagram state: $oldState -> $newState (reason: $reason)")
        }
    }

    override fun onAppForegrounded() {
        setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Instagram foregrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun onAppBackgrounded() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "Instagram backgrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun reset() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "Detector reset")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun analyze(snapshot: NodeSnapshot?, nowMs: Long, eventType: Int): DetectionResult {
        if (snapshot == null) {
            setFeedState(VideoFeedState.UNKNOWN, "Null accessibility snapshot")
            return DetectionResult.NO_TRANSITION
        }

        val oldSafeId = currentCountedIdentity?.privacySafeId ?: "none"

        // 1. Explicit Non-Reels screens (Comments sheet, Profile, Messages, Search)
        if (snapshot.containsAny("comment_composer", "comments_sheet", "comments_list", "comment_row") ||
            snapshot.matchesAnyText("Add a comment...", "Reply to")) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Comments sheet opened")
            logCounter("[FeedState] Instagram comments opened (oldId=$oldSafeId, newId=none, reason=Comments sheet active)")
            return DetectionResult.ignored("Instagram comments opened")
        }

        if (snapshot.containsAny("profile_header", "edit_profile", "direct_thread", "message_composer", "direct_inbox", "search_edit_text") ||
            snapshot.matchesAnyText("Edit profile", "Share profile", "Messages", "Chats")) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Profile, search, or messages active")
            currentCountedIdentity = null
            recentIdentities.clear()
            scrollDetectedSinceLastCount = false
            logCounter("[FeedState] Instagram profile/messages active (oldId=$oldSafeId, newId=none, reason=Profile or messages active)")
            return DetectionResult.ignored("Instagram profile or message screen active")
        }

        // 2. Home Feed evidence (Home tab selected, Stories tray, "Your story", Instagram logo header)
        val isHomeTabSelected = snapshot.isTabSelected("Home")
        val hasStoriesTray = snapshot.containsAny("story_ring", "reel_tray") ||
            snapshot.matchesAnyText("Stories", "Your story")
        val hasHomeBranding = snapshot.containsAny("action_bar_title", "action_bar_root") ||
            snapshot.matchesAnyText("Suggested for you")

        if (isHomeTabSelected || hasStoriesTray || hasHomeBranding) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Home feed active")
            currentCountedIdentity = null
            recentIdentities.clear()
            scrollDetectedSinceLastCount = false
            logCounter("[FeedState] Non-Reels screen: Instagram Home Feed active (oldId=$oldSafeId, newId=none, reason=Home feed active)")
            return DetectionResult.ignored("Active screen is non-Reels (Instagram Home feed active)")
        }

        // 3. Positive evidence of dedicated Reels feed
        val isReelsTabSelected = snapshot.isTabSelected("Reels")
        val hasClipsViewerContainer = snapshot.containsAny("ClipsViewer", "ClipsViewerActivity", "clips_viewer", "reel_viewer", "clips_item", "clips_media")
        val hasSocialActions = snapshot.matchesAnyText("Like", "Liked", "Comment", "Share", "Send", "Save", "Remix", "Audio", "Original", "Sound", "Follow", "Following") ||
            snapshot.containsAny("comment", "like", "share", "button")
        val hasCreatorTag = snapshot.matchesAnyText("Follow", "Following") ||
            snapshot.collectAllTexts(20).any { it.startsWith("@") }
        val hasVideoSurface = snapshot.containsAny("TextureView", "SurfaceView", "Player", "VideoView", "MediaView", "LithoView")
        val hasReelsKeywords = snapshot.containsAny("clips", "reel", "reels", "video", "media")
        val hasAudioOrRemix = snapshot.matchesAnyText("audio", "original audio", "remix", "sound")
        val isScrollEvent = eventType == 4096 /* TYPE_VIEW_SCROLLED */ || eventType == 4 /* TYPE_VIEW_SELECTED */

        val hasPositiveReelsEvidence = isReelsTabSelected || hasClipsViewerContainer ||
            ((hasVideoSurface || hasReelsKeywords || hasAudioOrRemix || hasCreatorTag) && hasSocialActions)

        if (!hasPositiveReelsEvidence && !isScrollEvent) {
            setFeedState(VideoFeedState.UNKNOWN, "Screen state uncertain - no positive Reels evidence")
            logCounter("[FeedState] Ignored: No positive evidence of active Instagram Reels feed (oldId=$oldSafeId, newId=none, reason=No Reels feed evidence)")
            return DetectionResult.ignored("Not in Instagram Reels viewer")
        }

        // Dedicated Reels feed is active!
        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "Reels viewer positively identified")

        // 4. Record scroll signal if scroll event
        if (isScrollEvent) {
            scrollDetectedSinceLastCount = true
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Scroll event detected, pending confirmation")
        }

        // 5. Extract stable item identity
        val candidate = buildInstagramIdentity(snapshot)
        if (!candidate.isReliable) {
            setFeedState(VideoFeedState.UNKNOWN, "Candidate identity uncertain")
            logCounter("[Identity] Rejected: Uncertain identity (oldId=$oldSafeId, newId=none, reason=Missing creator, audio, or content key)")
            return DetectionResult.ignored("Uncertain identity")
        }

        val candidateSafeId = candidate.privacySafeId

        // 6. Check against currently active counted Reel
        val active = currentCountedIdentity
        if (active != null) {
            if (isSameReel(active, candidate, scrollDetectedSinceLastCount)) {
                logCounter("[Duplicate] Rejected: Same Reel active (oldId=$oldSafeId, newId=$candidateSafeId, reason=Same Reel identity match)")
                return DetectionResult.ignored("Same Instagram Reel replay/redraw")
            }
        }

        // 7. Check if returning to a previously seen Reel (sliding window, not permanent history)
        if (recentIdentities.contains(candidate.canonicalKey)) {
            currentCountedIdentity = candidate
            scrollDetectedSinceLastCount = false
            logCounter("[History] Rejected: Returning to previously seen Reel (oldId=$oldSafeId, newId=$candidateSafeId, reason=Previously seen in session)")
            return DetectionResult.ignored("Returning to previously seen Instagram Reel")
        }

        // 8. Debounce rapid scroll bursts during gesture animation
        if (active != null && nowMs - lastCountTimestamp < debounceMs) {
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Debouncing rapid scroll burst")
            logCounter("[Debounce] Suppressed rapid scroll burst (oldId=$oldSafeId, newId=$candidateSafeId, reason=${nowMs - lastCountTimestamp}ms < ${debounceMs}ms)")
            return DetectionResult.ignored("Debounced rapid scroll burst")
        }

        // 9. Genuine Reel transition confirmed!
        currentCountedIdentity = candidate
        if (recentIdentities.size >= 5) {
            val oldest = recentIdentities.first()
            recentIdentities.remove(oldest)
        }
        recentIdentities.add(candidate.canonicalKey)
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = nowMs
        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "New Reel confirmed active")

        logCounter("[Transition] Confirmed: oldId=$oldSafeId, newId=$candidateSafeId, reason=Sufficient evidence of new active Reel (state=VIDEO_FEED_ACTIVE)")
        return DetectionResult.transition(
            confidence = 0.85f,
            reason = "Instagram Reel transition confirmed",
            signature = candidate.canonicalKey
        )
    }

    private fun buildInstagramIdentity(root: NodeSnapshot): ReelIdentity {
        val texts = root.collectAllTexts(50)

        // Find creator handle or author info
        var handle = texts.firstOrNull { it.startsWith("@") && !isStaticOrActionText(it) }
        if (handle == null) {
            val adjacent = root.findTextAdjacentTo("Follow") ?: root.findTextAdjacentTo("Following")
            if (adjacent != null && !isStaticOrActionText(adjacent)) {
                handle = adjacent
            }
        }
        if (handle == null) {
            val profileText = texts.firstOrNull { isProfilePictureText(it) && it.contains("@") }
            if (profileText != null) {
                val match = Regex("@[a-zA-Z0-9_.]+").find(profileText)
                if (match != null) handle = match.value
            }
        }
        if (handle == null) {
            val firstMeaningful = texts.firstOrNull { !isStaticOrActionText(it) && !it.contains("•") && !it.contains("audio", ignoreCase = true) }
            if (firstMeaningful != null && firstMeaningful.length in 2..30 && !firstMeaningful.contains(" ")) {
                handle = firstMeaningful
            }
        }

        // Find audio track info
        val audio = texts.firstOrNull { t ->
            !isStaticOrActionText(t) && (t.contains("audio", ignoreCase = true) || t.contains("original", ignoreCase = true) || t.contains("•"))
        }?.take(30)

        // Find caption / title text
        val caption = texts.firstOrNull { t ->
            !isStaticOrActionText(t) &&
                (handle == null || !t.equals(handle, ignoreCase = true)) &&
                !t.startsWith("@") &&
                (audio == null || !t.equals(audio, ignoreCase = true))
        }
        val contentKey = if (caption != null) normalizeContentKey(caption) else null

        var leafHash: String? = null
        if (handle == null && audio == null && contentKey == null) {
            leafHash = buildStructuralLeafHash(root)
        }

        return ReelIdentity(
            author = handle,
            audioTrack = audio,
            contentKey = contentKey,
            structuralHash = leafHash,
            platformPrefix = "author"
        )
    }
}

class YouTubeShortsDetector(
    private val debounceMs: Long = 350L
) : PlatformReelDetector {

    override var feedState: VideoFeedState = VideoFeedState.OUTSIDE_SUPPORTED_APP
        private set

    private var currentCountedIdentity: ReelIdentity? = null
    private var scrollDetectedSinceLastCount: Boolean = false
    private var lastCountTimestamp: Long = 0L
    private val recentIdentities = LinkedHashSet<String>()

    private fun setFeedState(newState: VideoFeedState, reason: String) {
        if (feedState != newState) {
            val oldState = feedState
            feedState = newState
            logCounter("[StateTransition] YouTube state: $oldState -> $newState (reason: $reason)")
        }
    }

    override fun onAppForegrounded() {
        setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "YouTube foregrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun onAppBackgrounded() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "YouTube backgrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun reset() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "Detector reset")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun analyze(snapshot: NodeSnapshot?, nowMs: Long, eventType: Int): DetectionResult {
        if (snapshot == null) {
            setFeedState(VideoFeedState.UNKNOWN, "Null snapshot")
            return DetectionResult.NO_TRANSITION
        }

        val oldSafeId = currentCountedIdentity?.privacySafeId ?: "none"

        // 1. Comments & description bottom sheets
        if (snapshot.containsAny("comment_composer", "comment_section", "description_bottom_sheet", "comments_sheet") ||
            snapshot.matchesAnyText("Add a comment...", "Top comments")) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Comments sheet opened")
            return DetectionResult.ignored("YouTube comments/description sheet opened")
        }

        // 2. Non-Shorts tabs (Home, Subscriptions, Library, You)
        val isHomeTabSelected = snapshot.isTabSelected("Home")
        val isSubscriptionsSelected = snapshot.isTabSelected("Subscriptions")
        val isLibrarySelected = snapshot.isTabSelected("Library") || snapshot.isTabSelected("You")

        val hasShortsActions = snapshot.matchesAnyText("Dislike this video", "Dislike", "Remix", "Sound used") ||
            snapshot.containsAny("shorts_player", "reel_player")
        val isStandardWatchPanel = snapshot.containsAny("watch_panel", "description_text", "time_bar") && !hasShortsActions

        if (isHomeTabSelected || isSubscriptionsSelected || isLibrarySelected || isStandardWatchPanel) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Home or long-form video active")
            currentCountedIdentity = null
            scrollDetectedSinceLastCount = false
            recentIdentities.clear()
            logCounter("[FeedState] Non-Shorts screen: YouTube Home, Subscriptions, or long-form video active")
            return DetectionResult.ignored("Active screen is non-Shorts (YouTube home or long-form video)")
        }

        // 3. Positive evidence of Shorts
        val isShortsTabSelected = snapshot.isTabSelected("Shorts")
        val isShortsContext = snapshot.containsAny("shorts", "reel_player", "shorts_player", "reel_recycler", "shorts_container", "pivot")
        val hasVideoSurface = snapshot.containsAny("SurfaceView", "TextureView", "Player", "VideoView", "LithoView")
        val isScrollEvent = eventType == 4096 /* TYPE_VIEW_SCROLLED */ || eventType == 4 /* TYPE_VIEW_SELECTED */

        val hasPositiveShortsEvidence = isShortsTabSelected || isShortsContext ||
            hasShortsActions || hasVideoSurface

        if (!hasPositiveShortsEvidence && !isScrollEvent) {
            setFeedState(VideoFeedState.UNKNOWN, "Not in Shorts player")
            return DetectionResult.ignored("Not in YouTube Shorts player")
        }

        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "Shorts player positively identified")

        if (isScrollEvent) {
            scrollDetectedSinceLastCount = true
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Scroll event detected")
        }

        val candidate = buildShortsIdentity(snapshot)
        if (!candidate.isReliable) {
            setFeedState(VideoFeedState.UNKNOWN, "Incomplete Shorts identity")
            return DetectionResult.ignored("Incomplete Shorts signature")
        }

        val candidateSafeId = candidate.privacySafeId
        val active = currentCountedIdentity
        if (active != null) {
            if (isSameReel(active, candidate, scrollDetectedSinceLastCount)) {
                logCounter("[Duplicate] Ignored identical YouTube Short: oldId=$oldSafeId, newId=$candidateSafeId")
                return DetectionResult.ignored("Same YouTube Short replay/redraw")
            }
        }

        if (recentIdentities.contains(candidate.canonicalKey)) {
            currentCountedIdentity = candidate
            scrollDetectedSinceLastCount = false
            logCounter("[History] Ignored returning to previously seen YouTube Short: oldId=$oldSafeId, newId=$candidateSafeId")
            return DetectionResult.ignored("Returning to previously seen YouTube Short")
        }

        if (active != null && nowMs - lastCountTimestamp < debounceMs) {
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Debouncing rapid Shorts scroll")
            logCounter("[Debounce] Suppressed rapid YouTube Shorts scroll burst (${nowMs - lastCountTimestamp}ms < ${debounceMs}ms)")
            return DetectionResult.ignored("Debounced rapid Shorts scroll")
        }

        currentCountedIdentity = candidate
        if (recentIdentities.size >= 5) {
            val oldest = recentIdentities.first()
            recentIdentities.remove(oldest)
        }
        recentIdentities.add(candidate.canonicalKey)
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = nowMs
        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "New Short confirmed active")

        logCounter("[Transition] Confirmed YouTube Shorts transition: oldId=$oldSafeId, newId=$candidateSafeId (state=VIDEO_FEED_ACTIVE)")
        return DetectionResult.transition(
            confidence = 0.90f,
            reason = "YouTube Shorts transition confirmed",
            signature = candidate.canonicalKey
        )
    }

    private fun buildShortsIdentity(root: NodeSnapshot): ReelIdentity {
        val texts = root.collectAllTexts(50)

        var channel = texts.firstOrNull { it.startsWith("@") && !isStaticOrActionText(it) }
        if (channel == null) {
            val adjacent = root.findTextAdjacentTo("Subscribe") ?: root.findTextAdjacentTo("Subscribed")
            if (adjacent != null && !isStaticOrActionText(adjacent)) {
                channel = adjacent
            }
        }
        if (channel == null) {
            val firstMeaningful = texts.firstOrNull { !isStaticOrActionText(it) && !it.contains("sound", ignoreCase = true) && !it.contains("remix", ignoreCase = true) }
            if (firstMeaningful != null && firstMeaningful.length in 2..30 && !firstMeaningful.contains(" ")) {
                channel = firstMeaningful
            }
        }

        val sound = texts.firstOrNull { t ->
            !isStaticOrActionText(t) && (t.contains("sound", ignoreCase = true) || t.contains("remix", ignoreCase = true))
        }?.take(30)

        val title = texts.firstOrNull { t ->
            !isStaticOrActionText(t) &&
                (channel == null || !t.equals(channel, ignoreCase = true)) &&
                !t.startsWith("@") &&
                (sound == null || !t.equals(sound, ignoreCase = true))
        }
        val contentKey = if (title != null) normalizeContentKey(title) else null

        var leafHash: String? = null
        if (channel == null && sound == null && contentKey == null) {
            leafHash = buildStructuralLeafHash(root)
        }

        return ReelIdentity(
            author = channel,
            audioTrack = sound,
            contentKey = contentKey,
            structuralHash = leafHash,
            platformPrefix = "ch"
        )
    }
}

class FacebookReelDetector(
    private val debounceMs: Long = 350L
) : PlatformReelDetector {

    override var feedState: VideoFeedState = VideoFeedState.OUTSIDE_SUPPORTED_APP
        private set

    private var currentCountedIdentity: ReelIdentity? = null
    private var scrollDetectedSinceLastCount: Boolean = false
    private var lastCountTimestamp: Long = 0L
    private val recentIdentities = LinkedHashSet<String>()

    private fun setFeedState(newState: VideoFeedState, reason: String) {
        if (feedState != newState) {
            val oldState = feedState
            feedState = newState
            logCounter("[StateTransition] Facebook state: $oldState -> $newState (reason: $reason)")
        }
    }

    override fun onAppForegrounded() {
        setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Facebook foregrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun onAppBackgrounded() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "Facebook backgrounded")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun reset() {
        setFeedState(VideoFeedState.OUTSIDE_SUPPORTED_APP, "Detector reset")
        currentCountedIdentity = null
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = 0L
        recentIdentities.clear()
    }

    override fun analyze(snapshot: NodeSnapshot?, nowMs: Long, eventType: Int): DetectionResult {
        if (snapshot == null) {
            setFeedState(VideoFeedState.UNKNOWN, "Null snapshot")
            return DetectionResult.NO_TRANSITION
        }

        val oldSafeId = currentCountedIdentity?.privacySafeId ?: "none"

        // 1. Filter out comments dialog
        if (snapshot.containsAny("flyout_comment", "ufi_comments", "comments_container") ||
            snapshot.matchesAnyText("Write a comment", "Write a public comment")) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "Facebook comments sheet opened")
            return DetectionResult.ignored("Facebook comments sheet opened")
        }

        // 2. News Feed, Marketplace, Groups, Profile
        val isNewsFeed = snapshot.isTabSelected("News Feed") || snapshot.isTabSelected("Feed") ||
            snapshot.containsAny("marketplace_feed", "group_feed", "profile_timeline") ||
            snapshot.matchesAnyText("What's on your mind?")

        if (isNewsFeed) {
            setFeedState(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, "News feed active")
            currentCountedIdentity = null
            scrollDetectedSinceLastCount = false
            recentIdentities.clear()
            logCounter("[FeedState] Non-Reels screen: Facebook News Feed or Groups active")
            return DetectionResult.ignored("Active screen is non-Reels (Facebook News Feed)")
        }

        // 3. Positive evidence of Facebook Reels
        val isReelsContext = snapshot.containsAny("fb_shorts", "reels_viewer", "video_chaining", "reel_container", "reels")
        val hasVideoSurface = snapshot.containsAny("SurfaceView", "TextureView", "Player", "VideoView")
        val hasActions = snapshot.matchesAnyText("Comment", "Share", "Send", "Like", "React", "Follow", "Following")
        val isScrollEvent = eventType == 4096 || eventType == 4

        val hasPositiveFbEvidence = isReelsContext || (hasVideoSurface && hasActions) || hasActions
        if (!hasPositiveFbEvidence && !isScrollEvent) {
            setFeedState(VideoFeedState.UNKNOWN, "Not in Facebook Reels viewer")
            return DetectionResult.ignored("Not in Facebook Reels viewer")
        }

        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "Reels viewer positively identified")

        if (isScrollEvent) {
            scrollDetectedSinceLastCount = true
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Scroll event detected")
        }

        val candidate = buildFbIdentity(snapshot)
        if (!candidate.isReliable) {
            setFeedState(VideoFeedState.UNKNOWN, "Incomplete FB Reels identity")
            return DetectionResult.ignored("Incomplete Facebook Reels signature")
        }

        val candidateSafeId = candidate.privacySafeId
        val active = currentCountedIdentity
        if (active != null) {
            if (isSameReel(active, candidate, scrollDetectedSinceLastCount)) {
                logCounter("[Duplicate] Ignored identical Facebook Reel: oldId=$oldSafeId, newId=$candidateSafeId")
                return DetectionResult.ignored("Same Facebook Reel replay/redraw")
            }
        }

        if (recentIdentities.contains(candidate.canonicalKey)) {
            currentCountedIdentity = candidate
            scrollDetectedSinceLastCount = false
            logCounter("[History] Ignored returning to previously seen Facebook Reel: oldId=$oldSafeId, newId=$candidateSafeId")
            return DetectionResult.ignored("Returning to previously seen Facebook Reel")
        }

        if (active != null && nowMs - lastCountTimestamp < debounceMs) {
            setFeedState(VideoFeedState.VIDEO_TRANSITION_PENDING, "Debouncing rapid FB Reels scroll")
            logCounter("[Debounce] Suppressed rapid Facebook Reels scroll burst (${nowMs - lastCountTimestamp}ms < ${debounceMs}ms)")
            return DetectionResult.ignored("Debounced rapid Facebook Reels scroll")
        }

        currentCountedIdentity = candidate
        if (recentIdentities.size >= 5) {
            val oldest = recentIdentities.first()
            recentIdentities.remove(oldest)
        }
        recentIdentities.add(candidate.canonicalKey)
        scrollDetectedSinceLastCount = false
        lastCountTimestamp = nowMs
        setFeedState(VideoFeedState.VIDEO_FEED_ACTIVE, "New FB Reel confirmed active")

        logCounter("[Transition] Confirmed Facebook Reel transition: oldId=$oldSafeId, newId=$candidateSafeId (state=VIDEO_FEED_ACTIVE)")
        return DetectionResult.transition(
            confidence = 0.80f,
            reason = "Facebook Reel transition confirmed",
            signature = candidate.canonicalKey
        )
    }

    private fun buildFbIdentity(root: NodeSnapshot): ReelIdentity {
        val texts = root.collectAllTexts(50)

        var handle = texts.firstOrNull { it.startsWith("@") && !isStaticOrActionText(it) }
        if (handle == null) {
            val adjacent = root.findTextAdjacentTo("Follow") ?: root.findTextAdjacentTo("Following")
            if (adjacent != null && !isStaticOrActionText(adjacent)) {
                handle = adjacent
            }
        }
        if (handle == null) {
            val firstMeaningful = texts.firstOrNull { !isStaticOrActionText(it) && !it.contains("•") && !it.contains("audio", ignoreCase = true) }
            if (firstMeaningful != null && firstMeaningful.length in 2..30 && !firstMeaningful.contains(" ")) {
                handle = firstMeaningful
            }
        }

        val audio = texts.firstOrNull { t ->
            !isStaticOrActionText(t) && (t.contains("audio", ignoreCase = true) || t.contains("original", ignoreCase = true) || t.contains("•"))
        }?.take(30)

        val caption = texts.filterNot { t ->
            isStaticOrActionText(t) || t == handle || t.startsWith("@")
        }.firstOrNull()
        val contentKey = if (caption != null) normalizeContentKey(caption) else null

        var leafHash: String? = null
        if (handle == null && audio == null && contentKey == null) {
            leafHash = buildStructuralLeafHash(root)
        }

        return ReelIdentity(
            author = handle,
            audioTrack = audio,
            contentKey = contentKey,
            structuralHash = leafHash,
            platformPrefix = "author"
        )
    }
}

/**
 * Builds a structural fingerprint from leaf nodes when text content descriptions are obscured.
 */
private fun buildStructuralLeafHash(root: NodeSnapshot): String {
    val b = StringBuilder()
    fun walk(node: NodeSnapshot, depth: Int) {
        if (depth > 24) return
        if (node.children.isEmpty()) {
            b.append(node.className.substringAfterLast('.')).append(':')
            if (!node.viewIdResourceName.isNullOrBlank()) {
                b.append(node.viewIdResourceName.substringAfterLast('/')).append(':')
            }
            if (!node.contentDescription.isNullOrBlank()) {
                b.append(node.contentDescription.hashCode().toString(16)).append(':')
            }
        }
        for (child in node.children) {
            walk(child, depth + 1)
        }
    }
    walk(root, 0)
    return b.toString().take(160).ifBlank { root.className }
}

object PlatformDetectorRegistry {
    private val instagramDetector = InstagramReelDetector()
    private val youTubeDetector = YouTubeShortsDetector()
    private val facebookDetector = FacebookReelDetector()

    fun getDetectorForPackage(packageName: String?): PlatformReelDetector? {
        return when {
            packageName?.startsWith("com.instagram") == true -> instagramDetector
            packageName?.startsWith("com.google.android.youtube") == true -> youTubeDetector
            packageName?.startsWith("com.facebook") == true -> facebookDetector
            else -> null
        }
    }

    fun onAppForegrounded(packageName: String?) {
        getDetectorForPackage(packageName)?.onAppForegrounded()
    }

    fun onAllAppsBackgrounded() {
        instagramDetector.onAppBackgrounded()
        youTubeDetector.onAppBackgrounded()
        facebookDetector.onAppBackgrounded()
    }

    fun resetAll() {
        instagramDetector.reset()
        youTubeDetector.reset()
        facebookDetector.reset()
    }
}
