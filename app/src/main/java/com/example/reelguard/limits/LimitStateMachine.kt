package com.example.reelguard.limits

import com.example.reelguard.model.AppLimitConfig
import java.time.LocalDate

enum class LimitStatus {
    BELOW_WARNING,
    WARNING,
    LIMIT_REACHED
}

data class LimitEvaluationResult(
    val status: LimitStatus,
    val shouldTriggerWarningAlert: Boolean,
    val shouldTriggerLimitAlert: Boolean,
    val alertMessage: String? = null
)

class LimitStateMachine {

    private data class AppAlertHistory(
        var warningFired: Boolean = false,
        var limitFired: Boolean = false,
        var observedDate: LocalDate = LocalDate.now()
    )

    private val alertHistories = mutableMapOf<String, AppAlertHistory>()

    fun evaluate(
        config: AppLimitConfig,
        secondsToday: Long,
        reelsToday: Int,
        currentDate: LocalDate = LocalDate.now()
    ): LimitEvaluationResult {
        val history = alertHistories.getOrPut(config.packageName) {
            AppAlertHistory(observedDate = currentDate)
        }

        // Daily reset check
        if (history.observedDate != currentDate) {
            history.warningFired = false
            history.limitFired = false
            history.observedDate = currentDate
        }

        val isLimit = config.isLimitReached(secondsToday, reelsToday)
        val isWarning = config.isWarningReached(secondsToday, reelsToday)

        return when {
            isLimit -> {
                val isNewCrossing = !history.limitFired
                history.limitFired = true
                val msg = "Daily limit reached for ${config.packageName}. Time to take a healthy break!"
                LimitEvaluationResult(
                    status = LimitStatus.LIMIT_REACHED,
                    shouldTriggerWarningAlert = false,
                    shouldTriggerLimitAlert = isNewCrossing && config.isBreakReminderEnabled,
                    alertMessage = msg
                )
            }
            isWarning -> {
                // If limit was previously marked fired but settings were raised, unmark it
                if (history.limitFired) {
                    history.limitFired = false
                }
                val isNewCrossing = !history.warningFired
                history.warningFired = true
                val msg = "You are approaching your daily limit for ${config.packageName}."
                LimitEvaluationResult(
                    status = LimitStatus.WARNING,
                    shouldTriggerWarningAlert = isNewCrossing && config.isBreakReminderEnabled,
                    shouldTriggerLimitAlert = false,
                    alertMessage = msg
                )
            }
            else -> {
                // Below warning: clear alert triggers so future crossings trigger cleanly
                history.warningFired = false
                history.limitFired = false
                LimitEvaluationResult(
                    status = LimitStatus.BELOW_WARNING,
                    shouldTriggerWarningAlert = false,
                    shouldTriggerLimitAlert = false,
                    alertMessage = null
                )
            }
        }
    }

    fun resetPackage(packageName: String) {
        alertHistories.remove(packageName)
    }

    fun resetAll() {
        alertHistories.clear()
    }
}
