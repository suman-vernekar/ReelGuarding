package com.example.reelguard

import android.content.Context
import com.example.reelguard.limits.LimitEvaluationResult
import com.example.reelguard.limits.LimitStateMachine
import java.time.LocalDate

/**
 * Evaluates usage boundaries after each monitoring tick or reel-count increment.
 * Uses LimitStateMachine to ensure warnings and limit reminders trigger exactly once
 * per threshold crossing without annoying duplicates.
 */
object LimitEnforcer {

    private val stateMachine = LimitStateMachine()

    fun evaluate(
        context: Context,
        packageName: String,
        currentDate: LocalDate = LocalDate.now()
    ): LimitEvaluationResult {
        val config = UsageStore.getLimitConfig(context, packageName)
        val seconds = UsageStore.getSeconds(context, packageName)
        val reels = UsageStore.getReelCount(context, packageName)
        return stateMachine.evaluate(config, seconds, reels, currentDate)
    }

    fun warningIfLimitReached(context: Context, packageName: String): String? {
        val result = evaluate(context, packageName)
        return if (result.shouldTriggerLimitAlert || result.shouldTriggerWarningAlert) {
            result.alertMessage
        } else {
            null
        }
    }

    fun resetPackage(packageName: String) {
        stateMachine.resetPackage(packageName)
    }

    fun resetAll() {
        stateMachine.resetAll()
    }
}
