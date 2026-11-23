package com.example.reelguard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class WellbeingQuotesTest {

    @Before
    fun setup() {
        WellbeingQuotes.resetHistory()
    }

    @Test
    fun collections_areNotEmptyAndHaveNoReelsOrShortsJargon() {
        assertTrue(WellbeingQuotes.MOTIVATIONAL_QUOTES.size >= 10)
        assertTrue(WellbeingQuotes.WELLBEING_FACTS.size >= 5)
        assertEquals(
            WellbeingQuotes.MOTIVATIONAL_QUOTES.size + WellbeingQuotes.WELLBEING_FACTS.size,
            WellbeingQuotes.ALL_MESSAGES.size
        )

        for (msg in WellbeingQuotes.ALL_MESSAGES) {
            assertTrue("Message should not be blank", msg.isNotBlank())
            assertFalse(
                "Message should not contain Reels: $msg",
                msg.contains("Reel", ignoreCase = true)
            )
            // Ensure no references to YouTube Shorts feeds (short-form videos)
            assertFalse(
                "Message should not contain Shorts: $msg",
                msg.contains("Shorts", ignoreCase = true)
            )
        }
    }

    @Test
    fun getNextWarningQuote_neverRepeatsConsecutively() {
        var previous = WellbeingQuotes.getNextWarningQuote()
        for (i in 1..60) {
            val current = WellbeingQuotes.getNextWarningQuote()
            assertNotEquals("Consecutive quotes must not be identical", previous, current)
            previous = current
        }
    }

    @Test
    fun getNextWarningQuote_respectsHistoryCapacityWithoutImmediateRepeats() {
        WellbeingQuotes.historyCapacity = 4

        // Run through multiple items deterministically
        val selectedMessages = mutableListOf<String>()
        for (i in 0..10) {
            val quote = WellbeingQuotes.getNextWarningQuote()
            selectedMessages.add(quote)
        }

        // For any window of 4 consecutive items, all items in that window must be distinct
        for (i in 0 until selectedMessages.size - 3) {
            val window = selectedMessages.subList(i, i + 4)
            val distinctCount = window.distinct().size
            assertEquals(
                "Window of size 4 must contain all distinct quotes: $window",
                4,
                distinctCount
            )
        }
    }

    @Test
    fun getNextWarningQuote_allowsReappearanceAfterFourDisplays() {
        WellbeingQuotes.resetHistory()
        WellbeingQuotes.historyCapacity = 4

        // Pick specific indices deterministically
        // Call 1: candidates 0..14 -> select 0
        val q0 = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates -> candidates.first() }
        assertEquals(WellbeingQuotes.ALL_MESSAGES[0], q0)
        assertEquals(listOf(0), WellbeingQuotes.getRecentHistory())

        // Call 2: select candidates.first() -> which is 1
        val q1 = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates -> candidates.first() }
        assertEquals(WellbeingQuotes.ALL_MESSAGES[1], q1)
        assertEquals(listOf(0, 1), WellbeingQuotes.getRecentHistory())

        // Call 3: select candidates.first() -> which is 2
        val q2 = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates -> candidates.first() }
        assertEquals(WellbeingQuotes.ALL_MESSAGES[2], q2)
        assertEquals(listOf(0, 1, 2), WellbeingQuotes.getRecentHistory())

        // Call 4: select candidates.first() -> which is 3
        val q3 = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates -> candidates.first() }
        assertEquals(WellbeingQuotes.ALL_MESSAGES[3], q3)
        assertEquals(listOf(0, 1, 2, 3), WellbeingQuotes.getRecentHistory())

        // Call 5: select candidate 4. History capacity is 4, so 0 is evicted!
        val q4 = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates -> candidates.first() }
        assertEquals(WellbeingQuotes.ALL_MESSAGES[4], q4)
        assertEquals(listOf(1, 2, 3, 4), WellbeingQuotes.getRecentHistory())

        // Call 6: candidate 0 is now available again! Let's select it:
        val qReappear = WellbeingQuotes.getNextWarningQuoteDeterministic { candidates ->
            assertTrue("0 should be a candidate again", candidates.contains(0))
            0
        }
        assertEquals("Quote 0 must be allowed to reappear after 4 displays", WellbeingQuotes.ALL_MESSAGES[0], qReappear)
        assertEquals(listOf(2, 3, 4, 0), WellbeingQuotes.getRecentHistory())
    }

    @Test
    fun getRandomQuoteAndWelcomeQuote_returnValidContent() {
        val welcome = WellbeingQuotes.getWelcomeQuote()
        assertNotNull(welcome)
        assertTrue(welcome.contains("Healthy habits"))

        val randomQuote = WellbeingQuotes.getRandomQuote(Random(42))
        assertNotNull(randomQuote)
        assertTrue(WellbeingQuotes.ALL_MESSAGES.contains(randomQuote))
    }
}
