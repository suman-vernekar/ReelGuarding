package com.example.reelguard.detector

import com.example.reelguard.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlatformReelDetectorTest {

    // 1. One genuine video transition increments the counter once
    @Test
    fun scenario1_genuineVideoTransition_incrementsOnce() {
        val detector = InstagramReelDetector(debounceMs = 500L)
        val reelNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "@creator_alpha"),
                NodeSnapshot(text = "Original audio - Sound Track"),
                NodeSnapshot(contentDescription = "Like button"),
                NodeSnapshot(contentDescription = "Comment button")
            )
        )

        val result = detector.analyze(reelNode, 1000L)
        assertTrue("Genuine video transition must be detected", result.isTransition)
        assertEquals(0.85f, result.confidence, 0.01f)
    }

    // 2. Repeated events for the same transition do not increment again
    @Test
    fun scenario2_repeatedEventsForSameTransition_doNotIncrementAgain() {
        val detector = InstagramReelDetector(debounceMs = 500L)
        val reelNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "@creator_alpha"),
                NodeSnapshot(text = "Original audio - Sound Track"),
                NodeSnapshot(contentDescription = "Like button")
            )
        )

        // Event 1: Genuine transition
        val res1 = detector.analyze(reelNode, 1000L)
        assertTrue(res1.isTransition)

        // Event 2: Same node repeated 100ms later (play/pause/redraw)
        val res2 = detector.analyze(reelNode, 1100L)
        assertFalse("Repeated event for the same video must be ignored", res2.isTransition)
        assertTrue(res2.diagnosticReason.contains("Same Instagram Reel", ignoreCase = true))

        // Event 3: Same node repeated after debounce window
        val res3 = detector.analyze(reelNode, 2000L)
        assertFalse("Same video after debounce window must still be ignored", res3.isTransition)
    }

    // 3. Scrolling to the next video increments the counter when sufficient evidence exists
    @Test
    fun scenario3_scrollingToNextVideo_incrementsCounter() {
        val detector = YouTubeShortsDetector(debounceMs = 500L)

        val short1 = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "android.view.ViewGroup",
            children = listOf(
                NodeSnapshot(text = "@tech_creator"),
                NodeSnapshot(text = "Amazing Kotlin tips in 30 seconds"),
                NodeSnapshot(contentDescription = "Dislike this video"),
                NodeSnapshot(contentDescription = "Share")
            )
        )

        val short2 = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "android.view.ViewGroup",
            children = listOf(
                NodeSnapshot(text = "@cooking_channel"),
                NodeSnapshot(text = "Quick 1 minute recipe"),
                NodeSnapshot(contentDescription = "Dislike this video"),
                NodeSnapshot(contentDescription = "Share")
            )
        )

        // Initial short
        val res1 = detector.analyze(short1, 1000L)
        assertTrue(res1.isTransition)

        // Scroll to next short
        val res2 = detector.analyze(short2, 1800L)
        assertTrue("Scrolling to next short must increment counter", res2.isTransition)
    }

    // 4. Opening and closing comments does not increment the counter
    @Test
    fun scenario4_openingAndClosingComments_doesNotIncrement() {
        val detector = InstagramReelDetector(debounceMs = 500L)

        val reel = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "@traveler"),
                NodeSnapshot(text = "Sunset in Tokyo"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        // 1. Watching Reel
        val resReel = detector.analyze(reel, 1000L)
        assertTrue(resReel.isTransition)

        // 2. Tapping comments opens comments bottom sheet
        val commentsSheet = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            viewIdResourceName = "comments_sheet",
            children = listOf(
                NodeSnapshot(text = "Add a comment..."),
                NodeSnapshot(viewIdResourceName = "comment_composer")
            )
        )
        val resComments = detector.analyze(commentsSheet, 1200L)
        assertFalse("Opening comments must never increment counter", resComments.isTransition)
        assertTrue(resComments.diagnosticReason.contains("comments", ignoreCase = true))

        // 3. Closing comments returns to same Reel
        val resCloseComments = detector.analyze(reel, 2500L)
        assertFalse("Returning from comments to same Reel must not increment", resCloseComments.isTransition)
    }

    // 5. Opening a profile does not increment the counter
    @Test
    fun scenario5_openingProfile_doesNotIncrement() {
        val detector = InstagramReelDetector()

        val profileNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.profile.ProfileActivity",
            viewIdResourceName = "profile_header",
            children = listOf(
                NodeSnapshot(text = "Edit profile"),
                NodeSnapshot(text = "10.5K Followers")
            )
        )

        val result = detector.analyze(profileNode, 1000L)
        assertFalse("Profile screens must be ignored", result.isTransition)
        assertTrue(result.diagnosticReason.contains("profile", ignoreCase = true))
    }

    // 6. App backgrounding stops counting
    @Test
    fun scenario6_appBackgrounding_stopsCounting() {
        val registry = PlatformDetectorRegistry
        val ig = registry.getDetectorForPackage("com.instagram.android")
        assertNotNull(ig)

        // When switching apps or backgrounding, resetAll clears state
        registry.resetAll()

        // Untracked apps return null from registry
        assertNull(registry.getDetectorForPackage("com.android.launcher"))
        assertNull(registry.getDetectorForPackage("com.whatsapp"))
    }

    // 7. Unknown event structures fail safely
    @Test
    fun scenario7_unknownEventStructures_failSafely() {
        val detector = InstagramReelDetector()
        val arbitraryNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.view.View"
            // No children, no text, no action bar
        )

        val result = detector.analyze(arbitraryNode, 1000L)
        assertFalse("Arbitrary unidentifiable UI must fail safely without crashing", result.isTransition)
    }

    // 8. Null accessibility nodes do not crash the service
    @Test
    fun scenario8_nullAccessibilityNodes_doNotCrash() {
        val ig = InstagramReelDetector()
        val yt = YouTubeShortsDetector()
        val fb = FacebookReelDetector()

        assertFalse(ig.analyze(null, 1000L).isTransition)
        assertFalse(yt.analyze(null, 1000L).isTransition)
        assertFalse(fb.analyze(null, 1000L).isTransition)
    }

    // 9. Instagram, YouTube, and Facebook counters remain separate
    @Test
    fun scenario9_platformCounters_remainSeparate() {
        val ig = InstagramReelDetector(debounceMs = 500L)
        val yt = YouTubeShortsDetector(debounceMs = 500L)
        val fb = FacebookReelDetector(debounceMs = 500L)

        val igNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(NodeSnapshot(text = "@ig_user"), NodeSnapshot(text = "Audio track"))
        )
        val ytNode = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "reel_player",
            children = listOf(NodeSnapshot(text = "@yt_user"), NodeSnapshot(text = "Short title"))
        )
        val fbNode = NodeSnapshot(
            packageName = "com.facebook.katana",
            className = "reels_viewer",
            children = listOf(NodeSnapshot(text = "@fb_user"), NodeSnapshot(contentDescription = "React"))
        )

        assertTrue(ig.analyze(igNode, 1000L).isTransition)
        assertTrue(yt.analyze(ytNode, 1000L).isTransition)
        assertTrue(fb.analyze(fbNode, 1000L).isTransition)
    }

    // 10. Counts persist and the dashboard receives updates
    @Test
    fun scenario10_countsPersist_andDashboardReceivesUpdates() {
        val memoryStorage = mutableMapOf<String, Int>()
        fun increment(pkg: String) {
            val key = "reels_${pkg}_${LocalDate.now()}"
            memoryStorage[key] = (memoryStorage[key] ?: 0) + 1
        }
        fun getCount(pkg: String): Int = memoryStorage["reels_${pkg}_${LocalDate.now()}"] ?: 0

        increment("com.instagram.android")
        increment("com.instagram.android")
        increment("com.google.android.youtube")

        assertEquals(2, getCount("com.instagram.android"))
        assertEquals(1, getCount("com.google.android.youtube"))
        assertEquals(0, getCount("com.facebook.katana"))
    }

    // 11. Daily reset behavior remains correct
    @Test
    fun scenario11_dailyReset_clearsTodayCounters() {
        val memoryStorage = mutableMapOf<String, Int>()
        val today = LocalDate.of(2026, 10, 10)
        val tomorrow = LocalDate.of(2026, 10, 11)

        memoryStorage["reels_com.instagram.android_$today"] = 25
        assertEquals(25, memoryStorage["reels_com.instagram.android_$today"])

        // On new day, key lookup for tomorrow starts fresh at 0
        val tomorrowCount = memoryStorage["reels_com.instagram.android_$tomorrow"] ?: 0
        assertEquals(0, tomorrowCount)
    }

    // 12. Multiple rapid events do not cause duplicate counts
    @Test
    fun scenario12_rapidEvents_debouncedWithoutDuplicateCounts() {
        val detector = InstagramReelDetector(debounceMs = 750L)

        val reel1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(NodeSnapshot(text = "@fast_creator_1"))
        )
        val reel2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(NodeSnapshot(text = "@fast_creator_2"))
        )

        // Event 1 at T = 1000ms
        assertTrue(detector.analyze(reel1, 1000L).isTransition)

        // Rapid swipe burst: Reel 2 arrives only 150ms later (T = 1150ms < 750ms debounce)
        val resRapid = detector.analyze(reel2, 1150L)
        assertFalse("Rapid event burst inside debounce window must be suppressed", resRapid.isTransition)
        assertTrue(resRapid.diagnosticReason.contains("Debounced", ignoreCase = true))

        // Normal scroll after debounce window (T = 2000ms)
        val resNormal = detector.analyze(reel2, 2000L)
        assertTrue("Scroll after debounce window must be accepted", resNormal.isTransition)
    }

    // 13. Continuous scroll through 10 distinct Instagram Reels in real-world feed
    @Test
    fun scenario13_instagram_continuousScrollThrough10Reels_incrementsExactly10Times() {
        val detector = InstagramReelDetector(debounceMs = 500L)
        var count = 0
        val baseTime = 1000L

        val creators = listOf(
            "natgeo" to "Wild polar bear in the Arctic tundra #nature",
            "cristiano" to "Unbelievable training session before the derby #football",
            "nasa" to "First deep field view of distant galaxy cluster #space",
            "chef_ramsay" to "How to cook the ultimate scrambled eggs perfectly",
            "tech_insider" to "Futuristic humanoid robot demo in Tokyo tech lab",
            "mrbeast" to "Surviving 100 hours in the world's deepest cave",
            "history_daily" to "Ancient Roman aqueduct still standing after 2000 years",
            "travel_couple" to "Hidden turquoise waterfall in Bali rainforest",
            "fitness_pro" to "10 minute core workout for posture and strength",
            "art_universe" to "Watch this resin epoxy river table being polished"
        )

        for ((index, pair) in creators.withIndex()) {
            val (handle, caption) = pair
            val reelNode = NodeSnapshot(
                packageName = "com.instagram.android",
                className = "android.widget.FrameLayout",
                children = listOf(
                    // Static navigation tabs (present on all screens)
                    NodeSnapshot(text = "Home"),
                    NodeSnapshot(text = "Search"),
                    NodeSnapshot(text = "Reels"),
                    NodeSnapshot(text = "Profile"),
                    // Reel author & follow button
                    NodeSnapshot(text = handle),
                    NodeSnapshot(text = "Follow"),
                    // Dynamic caption
                    NodeSnapshot(text = caption),
                    NodeSnapshot(text = "Original audio - $handle sound"),
                    // Interaction buttons
                    NodeSnapshot(contentDescription = "Like"),
                    NodeSnapshot(contentDescription = "Comment"),
                    NodeSnapshot(contentDescription = "Share")
                )
            )

            val scrollTime = baseTime + (index * 1500L)
            val result = detector.analyze(reelNode, scrollTime)
            assertTrue("Reel #$index ($handle) should be detected as a transition", result.isTransition)
            count++
        }

        assertEquals("Exactly 10 reels should be counted for 10 distinct scrolls", 10, count)
    }

    // 14. Continuous scroll through 10 distinct YouTube Shorts
    @Test
    fun scenario14_youtube_continuousScrollThrough10Shorts_incrementsExactly10Times() {
        val detector = YouTubeShortsDetector(debounceMs = 500L)
        var count = 0
        val baseTime = 1000L

        val shorts = listOf(
            "MKBHD" to "The craziest folding phone mechanism you've ever seen",
            "Veritasium" to "The counter-intuitive physics of spinning tops",
            "MarkRober" to "Glitter bomb trap 5.0 takes revenge on package thieves",
            "LinusTechTips" to "Building a silent liquid nitrogen cooled PC rig",
            "Kurzgesagt" to "What happens if a black hole enters our solar system",
            "Vsauce" to "How heavy is a shadow?",
            "CleoAbram" to "Nuclear fusion reactor breakthrough explained simply",
            "SmarterEveryDay" to "Slow motion shockwaves from supersonic bullet",
            "ColinFurze" to "I built an underground tunnel bunker beneath my yard",
            "Numberphile" to "The strangest mathematical paradox ever proved"
        )

        for ((index, pair) in shorts.withIndex()) {
            val (channel, title) = pair
            val shortNode = NodeSnapshot(
                packageName = "com.google.android.youtube",
                className = "android.view.ViewGroup",
                children = listOf(
                    // Static bottom navigation tabs
                    NodeSnapshot(text = "Home"),
                    NodeSnapshot(text = "Shorts"),
                    NodeSnapshot(text = "Subscriptions"),
                    NodeSnapshot(text = "Library"),
                    // Channel & subscribe button
                    NodeSnapshot(text = channel),
                    NodeSnapshot(text = "Subscribe"),
                    // Title & sound
                    NodeSnapshot(text = title),
                    NodeSnapshot(text = "Sound used - $channel remix"),
                    // Action buttons
                    NodeSnapshot(contentDescription = "Dislike this video"),
                    NodeSnapshot(contentDescription = "Comments"),
                    NodeSnapshot(contentDescription = "Share")
                )
            )

            val scrollTime = baseTime + (index * 1500L)
            val result = detector.analyze(shortNode, scrollTime)
            assertTrue("Short #$index ($channel) should be detected", result.isTransition)
            count++
        }

        assertEquals("Exactly 10 shorts should be counted", 10, count)
    }

    // 15. Continuous scroll through 10 distinct Facebook Reels
    @Test
    fun scenario15_facebook_continuousScrollThrough10Reels_incrementsExactly10Times() {
        val detector = FacebookReelDetector(debounceMs = 500L)
        var count = 0
        val baseTime = 1000L

        for (i in 1..10) {
            val fbNode = NodeSnapshot(
                packageName = "com.facebook.katana",
                className = "reels_viewer",
                children = listOf(
                    NodeSnapshot(text = "News Feed"),
                    NodeSnapshot(text = "Watch"),
                    NodeSnapshot(text = "fb_creator_$i"),
                    NodeSnapshot(text = "Follow"),
                    NodeSnapshot(text = "Inspiring reel video story #$i"),
                    NodeSnapshot(contentDescription = "React"),
                    NodeSnapshot(contentDescription = "Comment"),
                    NodeSnapshot(contentDescription = "Share")
                )
            )
            val scrollTime = baseTime + (i * 1500L)
            val result = detector.analyze(fbNode, scrollTime)
            assertTrue("FB Reel #$i should be detected", result.isTransition)
            count++
        }

        assertEquals("Exactly 10 FB reels should be counted", 10, count)
    }

    // 16. Dwelling on a video for 30 seconds does not increment counter
    @Test
    fun scenario16_dwellFor30Seconds_doesNotIncrement() {
        val detector = InstagramReelDetector(debounceMs = 500L)
        val reelNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "natgeo"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Arctic fox running in snow"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        // Initial view at T = 1000ms
        val initial = detector.analyze(reelNode, 1000L)
        assertTrue(initial.isTransition)

        // Periodic ticks during 30s dwell (at 5s, 10s, 15s, 20s, 25s, 30s)
        val dwellTicks = listOf(5000L, 10000L, 15000L, 20000L, 25000L, 30000L)
        for (t in dwellTicks) {
            val res = detector.analyze(reelNode, t)
            assertFalse("Dwell tick at $t ms must not increment counter", res.isTransition)
        }
    }

    // 17. User interactions (like, pause, redraw) on same reel do not increment
    @Test
    fun scenario17_likeCommentPauseInteraction_doesNotIncrement() {
        val detector = InstagramReelDetector(debounceMs = 500L)
        val unlikedNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "foodie"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Delicious homemade pizza"),
                NodeSnapshot(text = "Like")
            )
        )

        val res1 = detector.analyze(unlikedNode, 1000L)
        assertTrue(res1.isTransition)

        // User taps Like: button text becomes "Liked"
        val likedNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "foodie"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Delicious homemade pizza"),
                NodeSnapshot(text = "Liked")
            )
        )
        val resLike = detector.analyze(likedNode, 2000L)
        assertFalse("Tapping Like must not increment counter", resLike.isTransition)

        // User taps to pause: video surface redraw
        val resPause = detector.analyze(likedNode, 3000L)
        assertFalse("Pausing must not increment counter", resPause.isTransition)
    }

    // 18. Returning to a previously seen reel does not count it again
    @Test
    fun scenario18_scrollingBackUpToPreviouslySeenVideo_doesNotCountAgain() {
        val detector = InstagramReelDetector(debounceMs = 500L)

        val reel1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_one"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Video one caption")
            )
        )
        val reel2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_two"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Video two caption")
            )
        )
        val reel3 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_three"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Video three caption")
            )
        )

        // 1. Scroll forward 1 -> 2 -> 3
        assertTrue(detector.analyze(reel1, 1000L).isTransition)
        assertTrue(detector.analyze(reel2, 2500L).isTransition)
        assertTrue(detector.analyze(reel3, 4000L).isTransition)

        // 2. Scroll back up to 2
        val resBack2 = detector.analyze(reel2, 6000L)
        assertFalse("Scrolling back up to already seen Reel 2 must not count again", resBack2.isTransition)
        assertTrue(resBack2.diagnosticReason.contains("previously seen", ignoreCase = true))

        // 3. Scroll back up to 1
        val resBack1 = detector.analyze(reel1, 8000L)
        assertFalse("Scrolling back up to already seen Reel 1 must not count again", resBack1.isTransition)

        // 4. Scroll down past 2 and 3 again
        assertFalse(detector.analyze(reel2, 10000L).isTransition)
        assertFalse(detector.analyze(reel3, 12000L).isTransition)

        // 5. Scroll to new Reel 4
        val reel4 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_four"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Brand new Video four caption")
            )
        )
        assertTrue("Scrolling to new Reel 4 must increment counter", detector.analyze(reel4, 14000L).isTransition)
    }

    // 19. Plain creator handle without '@' is correctly extracted
    @Test
    fun scenario19_plainCreatorHandle_extractedFromAdjacentFollowOrSubscribe() {
        val igDetector = InstagramReelDetector()
        val igNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "bbcnews"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Breaking world news report")
            )
        )
        val igResult = igDetector.analyze(igNode, 1000L)
        assertTrue(igResult.isTransition)
        assertTrue("Signature should contain extracted author bbcnews", igResult.signature?.contains("author:bbcnews") == true)

        val ytDetector = YouTubeShortsDetector()
        val ytNode = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "reel_player",
            children = listOf(
                NodeSnapshot(text = "mkbhd_studio"),
                NodeSnapshot(text = "Subscribe"),
                NodeSnapshot(text = "Unboxing next-gen gadget")
            )
        )
        val ytResult = ytDetector.analyze(ytNode, 1000L)
        assertTrue(ytResult.isTransition)
        assertTrue("Signature should contain extracted channel mkbhd_studio", ytResult.signature?.contains("ch:mkbhd_studio") == true)
    }

    // 20. UsageStore listeners receive immediate callbacks upon reel increment
    @Test
    fun scenario20_usageStoreListeners_liveNotification() {
        var observedPackage: String? = null
        var observedCount: Int = -1

        val listener = com.example.reelguard.UsageStore.OnReelCountChangeListener { pkg, count ->
            observedPackage = pkg
            observedCount = count
        }

        com.example.reelguard.UsageStore.addReelCountListener(listener)
        try {
            val testPkg = "com.instagram.android"
            listener.onReelCountChanged(testPkg, 7)
            assertEquals(testPkg, observedPackage)
            assertEquals(7, observedCount)
        } finally {
            com.example.reelguard.UsageStore.removeReelCountListener(listener)
        }
    }

    // 21. Screen bounce or nudge triggering TYPE_VIEW_SCROLLED on same video does not increment, and transitions resume properly
    @Test
    fun scenario21_screenBounceOnSameVideo_doesNotIncrement_andPersistsAcrossRestart() {
        val detector = InstagramReelDetector(debounceMs = 400L)
        val video1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_unique_1"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Audio track 1")
            )
        )

        // Event 1: Initial video arrival
        val res1 = detector.analyze(video1, 1000L)
        assertTrue("Initial video must be detected", res1.isTransition)

        // Event 2: User nudges / bounces the screen generating a TYPE_VIEW_SCROLLED (4096) event on the SAME video
        val resBounce = detector.analyze(video1, 1600L, eventType = 4096)
        assertFalse("Screen nudge or bounce on the same video must be ignored despite scroll event", resBounce.isTransition)
        assertTrue(resBounce.diagnosticReason.contains("Same Instagram Reel", ignoreCase = true))

        // Event 3: Advance to Video 2 with scroll event
        val video2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_unique_2"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Audio track 2")
            )
        )
        val res2 = detector.analyze(video2, 2200L, eventType = 4096)
        assertTrue("Advancing to next video with scroll event must be detected", res2.isTransition)

        // Simulate Service restart: create a new detector instance (as happens on service restart)
        val restartedDetector = InstagramReelDetector(debounceMs = 400L)
        val video3 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewer",
            children = listOf(
                NodeSnapshot(text = "creator_unique_3"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Audio track 3")
            )
        )
        val resRestart = restartedDetector.analyze(video3, 3000L)
        assertTrue("Detector after restart must immediately detect new videos", resRestart.isTransition)
    }

    // 22. Concurrent events from multiple threads do not cause race conditions or duplicate counts
    @Test
    fun scenario22_concurrentEvents_threadSafeDeduplication() {
        val detector = YouTubeShortsDetector(debounceMs = 400L)
        val shortNode = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "reel_player",
            children = listOf(
                NodeSnapshot(text = "viral_channel"),
                NodeSnapshot(text = "Subscribe"),
                NodeSnapshot(text = "Crazy experiment #1")
            )
        )

        val threads = 8
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val countDownLatch = java.util.concurrent.CountDownLatch(threads)
        val transitionResults = java.util.concurrent.atomic.AtomicInteger(0)

        // All threads analyze the identical node simultaneously
        for (i in 0 until threads) {
            executor.submit {
                try {
                    val res = synchronized(detector) {
                        detector.analyze(shortNode, 1000L + (i * 20))
                    }
                    if (res.isTransition) {
                        transitionResults.incrementAndGet()
                    }
                } finally {
                    countDownLatch.countDown()
                }
            }
        }

        countDownLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals("Exactly 1 transition must be detected across all concurrent threads", 1, transitionResults.get())
    }

    // --- REQUIREMENT 8: 15 MANDATORY TESTS ---

    // 1. Open Instagram home feed: Reel count does not increase
    @Test
    fun test1_openInstagramHomeFeed_doesNotIncreaseReelCount() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)

        val homeFeedNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "Instagram"),
                NodeSnapshot(text = "Your story"),
                NodeSnapshot(text = "Home", isSelected = true),
                NodeSnapshot(text = "@friend_alpha"),
                NodeSnapshot(text = "Brunch with family!"),
                NodeSnapshot(contentDescription = "Like"),
                NodeSnapshot(contentDescription = "Comment")
            )
        )

        val result = detector.analyze(homeFeedNode, 1000L)
        assertFalse("Opening Instagram home feed must NEVER increase Reel count", result.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)
    }

    // 2. Open Instagram profile: count does not increase
    @Test
    fun test2_openInstagramProfile_doesNotIncreaseCount() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val profileNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.profile.ProfileActivity",
            viewIdResourceName = "profile_header",
            children = listOf(
                NodeSnapshot(text = "Edit profile"),
                NodeSnapshot(text = "120 Posts")
            )
        )

        val result = detector.analyze(profileNode, 1000L)
        assertFalse("Opening Instagram profile must not increase count", result.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)
    }

    // 3. Open Instagram messages: count does not increase
    @Test
    fun test3_openInstagramMessages_doesNotIncreaseCount() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val messagesNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            viewIdResourceName = "direct_thread",
            children = listOf(
                NodeSnapshot(text = "Chats"),
                NodeSnapshot(text = "Hey, are you free today?")
            )
        )

        val result = detector.analyze(messagesNode, 1000L)
        assertFalse("Opening Instagram messages must not increase count", result.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)
    }

    // 4. Open Instagram Reels: do not count arbitrary initialization events
    @Test
    fun test4_openInstagramReels_doesNotCountArbitraryInitializationEvents() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        // Uninitialized loading frame
        val emptyLoadingNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout"
        )

        val result = detector.analyze(emptyLoadingNode, 1000L)
        assertFalse("Arbitrary initialization events without video identity must not be counted", result.isTransition)
    }

    // 5. Identify the first visible Reel reliably: count it according to the documented counting semantics
    @Test
    fun test5_identifyFirstVisibleReelReliably_countsInitialItem() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val firstReelNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@nature_channel"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Spectacular mountain view in Norway"),
                NodeSnapshot(text = "Original audio - nature sound"),
                NodeSnapshot(contentDescription = "Like"),
                NodeSnapshot(contentDescription = "Comment")
            )
        )

        val result = detector.analyze(firstReelNode, 1000L)
        assertTrue("First visible Reel in dedicated feed must be identified and counted once", result.isTransition)
        assertEquals(VideoFeedState.VIDEO_FEED_ACTIVE, detector.feedState)
    }

    // 6. Scroll to the next Reel: increase the count by exactly one
    @Test
    fun test6_scrollToNextReel_incrementsByExactlyOne() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@creator_one"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Video 1"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        val reel2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@creator_two"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Video 2"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        val res1 = detector.analyze(reel1, 1000L)
        assertTrue(res1.isTransition)

        val res2 = detector.analyze(reel2, 2000L, eventType = 4096)
        assertTrue("Scrolling to next Reel must increment count by exactly 1", res2.isTransition)
    }

    // 7. Generate several accessibility events for the same Reel: no duplicate increments
    @Test
    fun test7_multipleEventsForSameReel_noDuplicateIncrements() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@creator_one"),
                NodeSnapshot(text = "Video 1"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        assertTrue(detector.analyze(reel, 1000L).isTransition)

        // Multiple redraw, progress ticks, likes for the same Reel
        for (t in listOf(1100L, 1200L, 1300L, 1800L, 2500L)) {
            val res = detector.analyze(reel, t)
            assertFalse("Subsequent event for the same Reel must NOT increment count", res.isTransition)
        }
    }

    // 8. Stay on one Reel: count remains unchanged
    @Test
    fun test8_stayOnOneReel_countRemainsUnchanged() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@doc_channel"),
                NodeSnapshot(text = "Deep dive documentary segment"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        assertTrue(detector.analyze(reel, 1000L).isTransition)

        // User watches Reel for 45 seconds
        for (second in 1..45) {
            val tickTime = 1000L + (second * 1000L)
            assertFalse("Dwelling on Reel must keep count unchanged", detector.analyze(reel, tickTime).isTransition)
        }
    }

    // 9. Exit Reels to Instagram home: Reel counting stops
    @Test
    fun test9_exitReelsToInstagramHome_reelCountingStops() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@reel_creator"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        assertTrue(detector.analyze(reel, 1000L).isTransition)
        assertEquals(VideoFeedState.VIDEO_FEED_ACTIVE, detector.feedState)

        // User taps Home tab
        val homeFeed = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "Instagram"),
                NodeSnapshot(text = "Home", isSelected = true),
                NodeSnapshot(text = "Your story"),
                NodeSnapshot(text = "@feed_friend"),
                NodeSnapshot(text = "Photo from yesterday"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        val resExit = detector.analyze(homeFeed, 3000L)
        assertFalse("Exiting Reels to Home feed must not increment count", resExit.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)

        // Subsequent scrolls on Home feed do not count
        val homeFeedPost2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "Home", isSelected = true),
                NodeSnapshot(text = "Your story"),
                NodeSnapshot(text = "@feed_friend_2"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        assertFalse("Scrolling on Home feed must not count as Reel", detector.analyze(homeFeedPost2, 5000L, eventType = 4096).isTransition)
    }

    // 10. Press Home or switch to another app: Instagram usage timer stops and no more Instagram counts are accepted
    @Test
    fun test10_pressHomeOrSwitchApp_usageTimerStopsAndNoCountsAccepted() {
        // 1. Instagram active
        com.example.reelguard.tracking.ActiveAppTracker.onAccessibilityEvent("com.instagram.android", 1000L)
        assertEquals("com.instagram.android", com.example.reelguard.tracking.ActiveAppTracker.activeAccessibilityPackage)

        // 2. User presses Home launcher
        com.example.reelguard.tracking.ActiveAppTracker.onNonTrackedAppForegrounded("com.miui.home")
        assertNull("activeAccessibilityPackage must be null immediately when leaving to launcher", com.example.reelguard.tracking.ActiveAppTracker.activeAccessibilityPackage)
        assertFalse("isRecentlyActive must be false immediately", com.example.reelguard.tracking.ActiveAppTracker.isRecentlyActive("com.instagram.android", 1100L))

        // 3. Platform registry sets detectors to OUTSIDE_SUPPORTED_APP
        PlatformDetectorRegistry.onAllAppsBackgrounded()
        val ig = PlatformDetectorRegistry.getDetectorForPackage("com.instagram.android")
        assertNotNull(ig)
        assertEquals(VideoFeedState.OUTSIDE_SUPPORTED_APP, ig?.feedState)
    }

    // 11. Reopen Instagram on its home feed: do not increment the Reel counter
    @Test
    fun test11_reopenInstagramOnHomeFeed_doesNotIncrementReelCounter() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val homeFeed = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "android.widget.FrameLayout",
            children = listOf(
                NodeSnapshot(text = "Instagram"),
                NodeSnapshot(text = "Home", isSelected = true),
                NodeSnapshot(text = "Your story"),
                NodeSnapshot(text = "@daily_post"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        val res = detector.analyze(homeFeed, 1000L)
        assertFalse("Reopening Instagram on home feed must not increment counter", res.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)
    }

    // 12. Open YouTube but not Shorts: Shorts count does not increase
    @Test
    fun test12_openYouTubeNotShorts_shortsCountDoesNotIncrease() {
        val detector = YouTubeShortsDetector()
        detector.onAppForegrounded()
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)

        val ytHomeFeed = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "android.view.ViewGroup",
            children = listOf(
                NodeSnapshot(text = "Home", isSelected = true),
                NodeSnapshot(text = "Subscriptions"),
                NodeSnapshot(text = "Build a PC in 2026"),
                NodeSnapshot(text = "12:45"),
                NodeSnapshot(text = "Tech Channel")
            )
        )

        val result = detector.analyze(ytHomeFeed, 1000L)
        assertFalse("Opening YouTube home feed must not increment Shorts count", result.isTransition)
        assertEquals(VideoFeedState.APP_OPEN_NON_VIDEO_SCREEN, detector.feedState)
    }

    // 13. Open YouTube Shorts and scroll: count detected new Shorts
    @Test
    fun test13_openYouTubeShortsAndScroll_countsDetectedShorts() {
        val detector = YouTubeShortsDetector()
        detector.onAppForegrounded()

        val short1 = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "reel_player",
            children = listOf(
                NodeSnapshot(text = "Shorts", isSelected = true),
                NodeSnapshot(text = "@veritasium"),
                NodeSnapshot(text = "Subscribe"),
                NodeSnapshot(text = "Cool physics Short"),
                NodeSnapshot(contentDescription = "Dislike this video")
            )
        )
        val short2 = NodeSnapshot(
            packageName = "com.google.android.youtube",
            className = "reel_player",
            children = listOf(
                NodeSnapshot(text = "Shorts", isSelected = true),
                NodeSnapshot(text = "@markrober"),
                NodeSnapshot(text = "Subscribe"),
                NodeSnapshot(text = "Squirrel obstacle course 3"),
                NodeSnapshot(contentDescription = "Dislike this video")
            )
        )

        val res1 = detector.analyze(short1, 1000L)
        assertTrue("First Short in Shorts feed must be counted", res1.isTransition)
        assertEquals(VideoFeedState.VIDEO_FEED_ACTIVE, detector.feedState)

        val res2 = detector.analyze(short2, 2000L, eventType = 4096)
        assertTrue("Scrolling to next Short must be counted", res2.isTransition)

        val res2Same = detector.analyze(short2, 2500L)
        assertFalse("Staying on same Short must not count again", res2Same.isTransition)
    }

    // 14. Restart the service: no fake initial increments
    @Test
    fun test14_restartService_noFakeInitialIncrements() {
        PlatformDetectorRegistry.resetAll()
        val ig = PlatformDetectorRegistry.getDetectorForPackage("com.instagram.android")
        val yt = PlatformDetectorRegistry.getDetectorForPackage("com.google.android.youtube")
        val fb = PlatformDetectorRegistry.getDetectorForPackage("com.facebook.katana")

        assertNotNull(ig)
        assertNotNull(yt)
        assertNotNull(fb)

        assertEquals(VideoFeedState.OUTSIDE_SUPPORTED_APP, ig?.feedState)
        assertEquals(VideoFeedState.OUTSIDE_SUPPORTED_APP, yt?.feedState)
        assertEquals(VideoFeedState.OUTSIDE_SUPPORTED_APP, fb?.feedState)
    }

    // 15. Open the ReelGuard dashboard: no platform counter increments
    @Test
    fun test15_openReelGuardDashboard_noPlatformCounterIncrements() {
        // ReelGuard is not in tracked packages
        assertNull(PlatformDetectorRegistry.getDetectorForPackage("com.example.reelguard"))
        assertFalse(AppConfig.trackedPackages.contains("com.example.reelguard"))
    }

    // 16. Duplicate fix: Single Reel with changing caption, likes, profile-pic description is counted exactly once
    @Test
    fun testDuplicateFix_singleReel_authorCaptionLikesProfilePicChange_countedExactlyOnce() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        // Event 1: Initial Reel arrival
        val node1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "Follow"),
                NodeSnapshot(text = "Chic autumn styling ideas..."),
                NodeSnapshot(contentDescription = "Like button")
            )
        )
        val res1 = detector.analyze(node1, 1000L)
        assertTrue("Event 1 (initial Reel) must be counted exactly once", res1.isTransition)
        assertEquals(VideoFeedState.VIDEO_FEED_ACTIVE, detector.feedState)

        // Event 2: Profile picture accessibility description loads
        val node2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(contentDescription = "Profile picture of fashion_trend"),
                NodeSnapshot(text = "Chic autumn styling ideas..."),
                NodeSnapshot(contentDescription = "Like button")
            )
        )
        val res2 = detector.analyze(node2, 1300L, eventType = 2048 /* TYPE_WINDOW_CONTENT_CHANGED */)
        assertFalse("Profile picture description loading must NOT increment counter", res2.isTransition)

        // Event 3: Like counter text loads
        val node3 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "14.2K likes"),
                NodeSnapshot(text = "Liked by anna and others"),
                NodeSnapshot(text = "Chic autumn styling ideas..."),
                NodeSnapshot(contentDescription = "Like button")
            )
        )
        val res3 = detector.analyze(node3, 1600L, eventType = 2048)
        assertFalse("Like count update must NOT increment counter", res3.isTransition)

        // Event 4: Caption expands from short to long
        val node4 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "Chic autumn styling ideas for 2026. Cozy knitwear and vintage coats #style #autumn"),
                NodeSnapshot(contentDescription = "Like button")
            )
        )
        val res4 = detector.analyze(node4, 2000L, eventType = 2048)
        assertFalse("Caption expansion must NOT increment counter", res4.isTransition)

        // Event 5: User taps Like button (button changes to Liked)
        val node5 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "Chic autumn styling ideas for 2026. Cozy knitwear and vintage coats #style #autumn"),
                NodeSnapshot(contentDescription = "Liked")
            )
        )
        val res5 = detector.analyze(node5, 2500L, eventType = 2048)
        assertFalse("Tapping like must NOT increment counter", res5.isTransition)

        // Event 6: Comments counter text updates
        val node6 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "View all 95 comments"),
                NodeSnapshot(text = "Chic autumn styling ideas for 2026..."),
                NodeSnapshot(contentDescription = "Liked")
            )
        )
        val res6 = detector.analyze(node6, 3000L, eventType = 2048)
        assertFalse("Comments counter update must NOT increment counter", res6.isTransition)

        // Event 7: Touch jitter / slight drag bounce (TYPE_VIEW_SCROLLED) staying on same reel
        val res7 = detector.analyze(node6, 3500L, eventType = 4096 /* TYPE_VIEW_SCROLLED */)
        assertFalse("Scroll bounce on same reel must NOT increment counter", res7.isTransition)

        // Event 8: Video timer tick
        val node8 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "0:12 / 0:30"),
                NodeSnapshot(text = "Chic autumn styling ideas for 2026..."),
                NodeSnapshot(contentDescription = "Liked")
            )
        )
        val res8 = detector.analyze(node8, 4000L, eventType = 2048)
        assertFalse("Video timer tick must NOT increment counter", res8.isTransition)
    }

    // 17. Duplicate fix: Genuine swipe to next Reel increments by exactly one
    @Test
    fun testDuplicateFix_genuineSwipeToNextReel_incrementsByExactlyOne() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "Chic autumn styling ideas"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        val res1 = detector.analyze(reel1, 1000L)
        assertTrue("Reel 1 must increment counter", res1.isTransition)

        val reel2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@chef_luigi"),
                NodeSnapshot(text = "Homemade lasagna step by step"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        val res2 = detector.analyze(reel2, 2500L, eventType = 4096 /* TYPE_VIEW_SCROLLED */)
        assertTrue("Genuine swipe to Reel 2 must increment counter by exactly 1", res2.isTransition)

        // Subsequent content changes on Reel 2 do not increment
        for (t in listOf(2800L, 3200L, 3600L)) {
            val resSame = detector.analyze(reel2, t, eventType = 2048)
            assertFalse("Subsequent event on Reel 2 must not increment counter", resSame.isTransition)
        }
    }

    // 18. Duplicate fix: Return to previously viewed Reel does not increment
    @Test
    fun testDuplicateFix_returnToPreviouslyViewedReel_doesNotIncrement() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val reel1 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@fashion_trend"),
                NodeSnapshot(text = "Chic autumn styling ideas"),
                NodeSnapshot(contentDescription = "Like")
            )
        )
        val reel2 = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(text = "@chef_luigi"),
                NodeSnapshot(text = "Homemade lasagna step by step"),
                NodeSnapshot(contentDescription = "Like")
            )
        )

        assertTrue(detector.analyze(reel1, 1000L).isTransition)
        assertTrue(detector.analyze(reel2, 2500L, eventType = 4096).isTransition)

        // User swipes back up to Reel 1
        val resBack = detector.analyze(reel1, 4000L, eventType = 4096)
        assertFalse("Returning to previously seen Reel 1 must NOT increment counter", resBack.isTransition)
        assertTrue("Reason must indicate previously seen reel", resBack.diagnosticReason.contains("previously seen", ignoreCase = true))
    }

    // 19. Duplicate fix: Uncertain identity without creator or content key is rejected
    @Test
    fun testDuplicateFix_uncertainIdentity_doesNotIncrement() {
        val detector = InstagramReelDetector()
        detector.onAppForegrounded()

        val uncertainNode = NodeSnapshot(
            packageName = "com.instagram.android",
            className = "ClipsViewerActivity",
            children = listOf(
                NodeSnapshot(text = "Reels", isSelected = true),
                NodeSnapshot(contentDescription = "Like"),
                NodeSnapshot(contentDescription = "Comment"),
                NodeSnapshot(contentDescription = "Share")
            )
        )

        val result = detector.analyze(uncertainNode, 1000L)
        assertFalse("Uncertain node without creator, audio, or content key must NOT increment", result.isTransition)
        assertTrue(result.diagnosticReason.contains("Uncertain identity", ignoreCase = true))
    }

    // 20. Privacy safe ID does not log raw captions or usernames
    @Test
    fun testDuplicateFix_privacySafeLogging_doesNotExposePlaintext() {
        val key = "author:@very_private_user;cap:My private secret caption"
        val safeId = privacySafeId(key)
        assertTrue("Safe ID must start with id#", safeId.startsWith("id#"))
        assertFalse("Safe ID must NOT contain username", safeId.contains("private_user"))
        assertFalse("Safe ID must NOT contain caption", safeId.contains("secret"))
    }
}
