package com.example.reelguard.breakremind

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.reelguard.MainActivity
import com.example.reelguard.model.AppUsageData

class BreakReminderActivity : Activity() {

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(BreakExperienceManager.EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(BreakExperienceManager.EXTRA_APP_NAME) ?: "Social App"
        val videoType = intent.getStringExtra(BreakExperienceManager.EXTRA_VIDEO_TYPE) ?: "Reels"
        val timeUsedSeconds = intent.getLongExtra(BreakExperienceManager.EXTRA_TIME_USED_SECONDS, 0L)
        val reelsCount = intent.getIntExtra(BreakExperienceManager.EXTRA_REELS_COUNT, 0)
        val timeLimitMinutes = intent.getIntExtra(BreakExperienceManager.EXTRA_TIME_LIMIT_MINUTES, 30)
        val reelLimit = intent.getIntExtra(BreakExperienceManager.EXTRA_REEL_LIMIT, 20)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(40))
        }

        root.addView(TextView(this).apply {
            text = "😔"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(TextView(this).apply {
            text = "Time for a Mindful Break"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Daily usage allowance reached on $appName"
            textSize = 15f
            setTextColor(Color.parseColor("#9AA0A6"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(24))
        })

        // Stats Card
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#333333"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
        }

        val timeUsedFormatted = AppUsageData.formatDuration(timeUsedSeconds)
        val timeLimitFormatted = AppUsageData.formatDuration(timeLimitMinutes * 60L)

        statsCard.addView(TextView(this).apply {
            text = "Today's Consumption"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8AB4F8"))
            setPadding(0, 0, 0, dp(8))
        })

        statsCard.addView(TextView(this).apply {
            text = "• Foreground Time: $timeUsedFormatted"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(4))
        })

        statsCard.addView(TextView(this).apply {
            text = "• Daily Allowance: $timeLimitFormatted"
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        root.addView(statsCard)

        // Mindful Message Card
        val mindfulCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#132A1C"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#1E4620"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
        }

        val mindfulQuote = com.example.reelguard.util.WellbeingQuotes.getRandomQuote()

        mindfulCard.addView(TextView(this).apply {
            text = "🌿 Mindful Pause"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#81C995"))
            setPadding(0, 0, 0, dp(4))
        })

        mindfulCard.addView(TextView(this).apply {
            text = mindfulQuote
            textSize = 13.5f
            setTextColor(Color.parseColor("#E8EAED"))
        })
        root.addView(mindfulCard)

        // Countdown prompt
        val countdownText = TextView(this).apply {
            text = "Take a deep breath (5s pause)..."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(countdownText)

        // Primary Button: Go to Home (Take a Break)
        val homeButton = Button(this).apply {
            text = "Take a 5-Minute Break (Exit to Home)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.parseColor("#1A73E8"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        }
        root.addView(homeButton)

        // Secondary Button: Open ReelGuard Dashboard
        val dashboardButton = Button(this).apply {
            text = "Open ReelGuard Dashboard"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#2D2E30"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener {
                val dashIntent = Intent(this@BreakReminderActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(dashIntent)
                finish()
            }
        }
        root.addView(dashboardButton)

        // Override Button: Continue for Today
        val overrideButton = Button(this).apply {
            text = "Continue for Today (Override)"
            textSize = 12f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#80868B"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            setOnClickListener {
                finish()
            }
        }
        root.addView(overrideButton)

        scrollView.addView(root)
        setContentView(scrollView)

        countDownTimer = object : CountDownTimer(5000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000L) + 1
                countdownText.text = "Take a deep breath (${sec}s pause)..."
            }

            override fun onFinish() {
                countdownText.text = "You are in control of your screen time."
            }
        }.start()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        super.onDestroy()
    }
}
