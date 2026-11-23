package com.example.reelguard.util

import kotlin.random.Random

/**
 * Curated collection of digital wellbeing quotes and screen-time facts.
 * Provides in-memory rotated messages for floating warning overlays, welcome screens, and dashboard.
 *
 * Implements a FIFO history queue (size 4) to ensure no repetition within 3–4 displays,
 * while allowing quotes to reappear after 3–4 different displays.
 */
object WellbeingQuotes {

    val MOTIVATIONAL_QUOTES: List<String> = listOf(
        "😊 Small breaks can make room for big things.",
        "🙂 Your time is valuable. Spend it intentionally.",
        "🌱 Healthy habits grow one small choice at a time.",
        "💙 You are in control of how you spend your time.",
        "🌿 Pause, breathe, and enjoy the moment.",
        "☀️ A little balance can make a big difference.",
        "😊 Make time for the things that matter to you.",
        "🌼 Progress matters more than perfection.",
        "💪 You can choose what deserves your attention.",
        "🌈 Every moment is an opportunity to reset."
    )

    val WELLBEING_FACTS: List<String> = listOf(
        "👁️ Taking regular breaks can help reduce eye strain.",
        "👀 Looking away from your screen regularly gives your eyes a chance to rest.",
        "🚶 A short walk or stretch can break up long periods of sitting.",
        "🔕 Turning off unnecessary notifications can reduce distractions.",
        "🌙 A consistent bedtime routine can support better sleep."
    )

    val ALL_MESSAGES: List<String> = MOTIVATIONAL_QUOTES + WELLBEING_FACTS

    const val DEFAULT_HISTORY_CAPACITY = 4

    // In-memory FIFO queue of recently shown message indices
    private val recentIndices = ArrayDeque<Int>()

    @Volatile
    var historyCapacity: Int = DEFAULT_HISTORY_CAPACITY

    /**
     * Selects the next warning message, avoiding recently shown messages.
     * Prevents repetition within [historyCapacity] displays (default 4).
     */
    @Synchronized
    fun getNextWarningQuote(random: Random = Random): String {
        val candidates = ALL_MESSAGES.indices.filter { it !in recentIndices }
        val chosenIndex = if (candidates.isNotEmpty()) {
            candidates[random.nextInt(candidates.size)]
        } else {
            // Evict oldest if all are exhausted
            if (recentIndices.isNotEmpty()) recentIndices.removeFirst() else 0
        }

        recentIndices.addLast(chosenIndex)
        while (recentIndices.size > historyCapacity) {
            recentIndices.removeFirst()
        }

        return ALL_MESSAGES[chosenIndex]
    }

    /**
     * Deterministic selection for testing rotation without random jitter.
     */
    @Synchronized
    fun getNextWarningQuoteDeterministic(indexSelector: (List<Int>) -> Int): String {
        val candidates = ALL_MESSAGES.indices.filter { it !in recentIndices }
        val chosenIndex = if (candidates.isNotEmpty()) {
            indexSelector(candidates)
        } else {
            if (recentIndices.isNotEmpty()) recentIndices.removeFirst() else 0
        }

        recentIndices.addLast(chosenIndex)
        while (recentIndices.size > historyCapacity) {
            recentIndices.removeFirst()
        }

        return ALL_MESSAGES[chosenIndex]
    }

    @Synchronized
    fun getRecentHistory(): List<Int> = recentIndices.toList()

    @Synchronized
    fun resetHistory() {
        recentIndices.clear()
        historyCapacity = DEFAULT_HISTORY_CAPACITY
    }

    fun getRandomQuote(random: Random = Random): String {
        return ALL_MESSAGES[random.nextInt(ALL_MESSAGES.size)]
    }

    fun getWelcomeQuote(): String {
        return "🌱 Healthy habits grow one small choice at a time."
    }
}
