package com.example.reelguard.detector

/**
 * Immutable snapshot of accessibility node data.
 * Enables platform-specific reel detection heuristics to run against pure,
 * testable data structures without coupling to Android framework classes.
 */
data class NodeSnapshot(
    val packageName: String = "",
    val className: String = "",
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val isScrollable: Boolean = false,
    val isSelected: Boolean = false,
    val isPassword: Boolean = false,
    val childCount: Int = 0,
    val children: List<NodeSnapshot> = emptyList()
) {
    fun isTabSelected(tabName: String): Boolean {
        return findFirst { n ->
            val matchText = n.text?.equals(tabName, ignoreCase = true) == true
            val matchDesc = n.contentDescription?.contains(tabName, ignoreCase = true) == true
            val selected = n.isSelected ||
                n.contentDescription?.contains("$tabName, selected", ignoreCase = true) == true ||
                n.contentDescription?.contains("$tabName tab, selected", ignoreCase = true) == true
            (matchText || matchDesc) && selected
        } != null
    }

    fun containsAny(vararg terms: String): Boolean {
        for (term in terms) {
            if (className.contains(term, ignoreCase = true)) return true
            if (contentDescription?.contains(term, ignoreCase = true) == true) return true
            if (viewIdResourceName?.contains(term, ignoreCase = true) == true) return true
            if (text?.contains(term, ignoreCase = true) == true) return true
        }
        for (child in children) {
            if (child.containsAny(*terms)) return true
        }
        return false
    }

    fun findFirst(predicate: (NodeSnapshot) -> Boolean): NodeSnapshot? {
        if (predicate(this)) return this
        for (child in children) {
            val found = child.findFirst(predicate)
            if (found != null) return found
        }
        return null
    }

    fun collectAllMatching(predicate: (NodeSnapshot) -> Boolean, outList: MutableList<NodeSnapshot> = mutableListOf()): List<NodeSnapshot> {
        if (predicate(this)) {
            outList.add(this)
        }
        for (child in children) {
            child.collectAllMatching(predicate, outList)
        }
        return outList
    }

    /**
     * Collects non-blank texts and contentDescriptions up to [maxItems].
     */
    fun collectAllTexts(maxItems: Int = 50, outList: MutableList<String> = mutableListOf()): List<String> {
        if (outList.size >= maxItems) return outList
        val t = text?.trim()
        if (!t.isNullOrBlank() && t.length > 1 && !outList.contains(t)) {
            outList.add(t)
        }
        val cd = contentDescription?.trim()
        if (!cd.isNullOrBlank() && cd.length > 1 && !outList.contains(cd)) {
            outList.add(cd)
        }
        for (child in children) {
            child.collectAllTexts(maxItems, outList)
            if (outList.size >= maxItems) break
        }
        return outList
    }

    /**
     * Finds text immediately adjacent (preceding or succeeding) to the specified keyword.
     */
    fun findTextAdjacentTo(keyword: String): String? {
        val all = collectAllTexts(50)
        val idx = all.indexOfFirst { it.equals(keyword, ignoreCase = true) }
        return if (idx > 0) {
            all[idx - 1]
        } else if (idx >= 0 && idx + 1 < all.size) {
            all[idx + 1]
        } else {
            null
        }
    }

    /**
     * Finds if any node in hierarchy has text or description containing any of the keywords.
     */
    fun matchesAnyText(vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text?.contains(kw, ignoreCase = true) == true) return true
            if (contentDescription?.contains(kw, ignoreCase = true) == true) return true
        }
        for (child in children) {
            if (child.matchesAnyText(*keywords)) return true
        }
        return false
    }
}

data class DetectionResult(
    val isTransition: Boolean,
    val confidence: Float,
    val diagnosticReason: String,
    val signature: String? = null
) {
    companion object {
        val NO_TRANSITION = DetectionResult(
            isTransition = false,
            confidence = 0f,
            diagnosticReason = "No short-video transition detected"
        )

        fun ignored(reason: String) = DetectionResult(
            isTransition = false,
            confidence = 0f,
            diagnosticReason = reason
        )

        fun transition(confidence: Float, reason: String, signature: String) = DetectionResult(
            isTransition = true,
            confidence = confidence.coerceIn(0f, 1f),
            diagnosticReason = reason,
            signature = signature
        )
    }
}
