package com.example.reelguard

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.Screen
import com.example.reelguard.model.SupportedApp
import com.example.reelguard.repository.SqliteUsageRepository
import com.example.reelguard.ui.ChartBarEntry
import com.example.reelguard.ui.DashboardUiState
import com.example.reelguard.ui.UsageAnalyticsChartView
import com.example.reelguard.util.PermissionHelper
import com.example.reelguard.util.WellbeingEmojiHelper
import com.example.reelguard.util.WellbeingQuotes
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_CREATE_CSV = 2001
        var hasShownOpeningAnimation = false
    }

    private var currentScreen: Screen = Screen.DASHBOARD
    private var isMonitorRunning = false
    private var pendingGlobalLimitMinutes = 30
    private var selectedHorizonDays = 7
    private var selectedAnalyticsPackage: String? = null

    private val pendingAppLimits = mutableMapOf<String, Int>()
    private val pendingAppEnabled = mutableMapOf<String, Boolean>()

    private val usageRepository by lazy { SqliteUsageRepository(this) }

    private lateinit var headerStatusBadge: TextView
    private lateinit var contentContainer: LinearLayout
    private val bottomNavViews = mutableMapOf<Screen, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadAppLimits()

        val windowContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC")) // Slate-50 background
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Header
        root.addView(createHeaderView())

        // Scrollable Main Content
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView.addView(contentContainer)
        root.addView(scrollView)

        // Modern 4-Tab Bottom Navigation Bar
        root.addView(createBottomNavBarView())

        windowContainer.addView(root)

        if (!hasShownOpeningAnimation) {
            hasShownOpeningAnimation = true
            setupOpeningAnimation(windowContainer)
        } else {
            setContentView(windowContainer)
        }
    }

    private fun loadAppLimits() {
        pendingGlobalLimitMinutes = UsageStore.limitMinutes(this)
        for (app in SupportedApp.entries) {
            val pkg = app.packageName
            pendingAppLimits[pkg] = UsageStore.getAppLimitMinutes(this, pkg)
            pendingAppEnabled[pkg] = UsageStore.isAppLimitEnabled(this, pkg)
        }
    }

    private fun setupOpeningAnimation(windowContainer: FrameLayout) {
        val animScale = try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        } catch (_: Exception) { 1.0f }

        val isReducedMotion = animScale == 0f || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled())

        if (isReducedMotion) {
            setContentView(windowContainer)
            return
        }

        val splashOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1E1B4B")) // Calm dark indigo/violet background
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
        }

        val splashCenter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Stylized ReelGuard Logo Container
        val logoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(2), Color.parseColor("#6366F1"))
            }
            background = bg
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val logoIcon = TextView(this).apply {
            text = "🛡️"
            textSize = 42f
            gravity = Gravity.CENTER
        }
        val logoFace = TextView(this).apply {
            text = "😊"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        logoContainer.addView(logoIcon)
        logoContainer.addView(logoFace)
        splashCenter.addView(logoContainer)

        val appNameView = TextView(this).apply {
            text = "ReelGuard"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        }
        splashCenter.addView(appNameView)

        val taglineView = TextView(this).apply {
            text = "Mindful screen time • Healthier habits"
            textSize = 13.5f
            setTextColor(Color.parseColor("#A5B4FC"))
            gravity = Gravity.CENTER
        }
        splashCenter.addView(taglineView)

        val welcomeQuoteView = TextView(this).apply {
            text = WellbeingQuotes.getWelcomeQuote()
            textSize = 12f
            setTextColor(Color.parseColor("#C7D2FE"))
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(20), dp(32), 0)
        }
        splashCenter.addView(welcomeQuoteView)

        splashOverlay.addView(splashCenter)
        windowContainer.addView(splashOverlay)
        setContentView(windowContainer)

        // Initial animation properties
        logoContainer.scaleX = 0.7f
        logoContainer.scaleY = 0.7f
        logoContainer.alpha = 0f
        appNameView.alpha = 0f
        appNameView.translationY = dp(14).toFloat()
        taglineView.alpha = 0f
        welcomeQuoteView.alpha = 0f

        // Smooth choreographed entrance animation (total duration ~850ms)
        logoContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(450L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        appNameView.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(120L)
            .setDuration(350L)
            .start()

        taglineView.animate()
            .alpha(1f)
            .setStartDelay(220L)
            .setDuration(300L)
            .start()

        welcomeQuoteView.animate()
            .alpha(1f)
            .setStartDelay(320L)
            .setDuration(300L)
            .start()

        // Automatic smooth fade-out to dashboard (no button needed)
        splashOverlay.animate()
            .alpha(0f)
            .setStartDelay(650L)
            .setDuration(250L)
            .withEndAction {
                windowContainer.removeView(splashOverlay)
            }
            .start()
    }

    private val usageChangeListener = UsageStore.OnUsageChangeListener { _ ->
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                refreshUi()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        UsageStore.addUsageListener(usageChangeListener)
    }

    override fun onStop() {
        UsageStore.removeUsageListener(usageChangeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        loadAppLimits()
        if (PermissionHelper.hasUsageAccess(this) && !isMonitorRunning) {
            startMonitorService()
        }
        refreshUi()
    }

    private fun getUiState(): DashboardUiState {
        val hasUsage = PermissionHelper.hasUsageAccess(this)
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        val hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(this)
        val hasNotification = PermissionHelper.hasNotificationPermission(this)
        val hasBattery = PermissionHelper.isBatteryOptimizationIgnored(this)
        val appsUsage = UsageStore.getAllAppsUsage(this)

        return DashboardUiState(
            currentScreen = currentScreen,
            isLoading = false,
            errorMessage = null,
            appsUsage = appsUsage,
            isMonitorRunning = isMonitorRunning,
            hasUsagePermission = hasUsage,
            hasOverlayPermission = hasOverlay,
            hasAccessibilityPermission = hasAccessibility,
            hasNotificationPermission = hasNotification,
            hasBatteryOptimizationIgnored = hasBattery
        )
    }

    private fun refreshUi() {
        val state = getUiState()
        updateHeaderStatus(state)
        updateBottomNavSelection()
        renderScreen(state)
    }

    // --- TOP HEADER ---
    private fun createHeaderView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()

            // App Icon Pill Badge
            val logoPill = TextView(this@MainActivity).apply {
                text = "🛡️"
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = createPillDrawable(Color.parseColor("#EEF2FF"))
            }
            addView(logoPill)

            val titleBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val title = TextView(this@MainActivity).apply {
                text = "ReelGuard"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            }

            val subtitle = TextView(this@MainActivity).apply {
                text = "Your digital balance"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            }

            titleBox.addView(title)
            titleBox.addView(subtitle)
            addView(titleBox)

            // Header Wellbeing Face Emoji Badge
            headerStatusBadge = TextView(this@MainActivity).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = createPillDrawable(Color.parseColor("#EEF2FF"))
                setTextColor(Color.parseColor("#4F46E5"))
                text = "😊 Ready"
                setOnClickListener {
                    currentScreen = Screen.DASHBOARD
                    refreshUi()
                }
            }
            addView(headerStatusBadge)

            // Settings Action Button
            val settingsBtn = TextView(this@MainActivity).apply {
                text = "⚙️"
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val density = resources.displayMetrics.density
                background = createRippleCardDrawable(
                    bgColor = Color.parseColor("#F1F5F9"),
                    cornerRadiusPx = 14f * density
                )
                setOnClickListener {
                    currentScreen = Screen.SETTINGS
                    refreshUi()
                }
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginStart = dp(8)
                }
            }
            addView(settingsBtn)
        }
    }

    private fun updateHeaderStatus(state: DashboardUiState) {
        val wellbeing = WellbeingEmojiHelper.getTimeWellbeingStatus(
            totalSecondsUsed = state.totalSecondsToday,
            dailyLimitMinutes = pendingGlobalLimitMinutes,
            hasPermission = state.hasUsagePermission
        )
        headerStatusBadge.text = "${wellbeing.emoji} ${wellbeing.title}"
        headerStatusBadge.setTextColor(Color.parseColor(wellbeing.colorHex))
        headerStatusBadge.background = createPillDrawable(Color.parseColor(wellbeing.badgeBgHex))
    }

    // --- BOTTOM NAVIGATION BAR ---
    private fun createBottomNavBarView(): View {
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        }

        val destinations = listOf(
            Triple(Screen.DASHBOARD, "Home", "🏠"),
            Triple(Screen.USAGE, "Usage", "🎯"),
            Triple(Screen.ANALYTICS, "Analytics", "📊"),
            Triple(Screen.SETTINGS, "Settings", "⚙️")
        )

        for ((screen, title, icon) in destinations) {
            val itemContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                val density = resources.displayMetrics.density
                background = createRippleCardDrawable(Color.TRANSPARENT, 16f * density)

                val iconView = TextView(this@MainActivity).apply {
                    text = icon
                    textSize = 17f
                    gravity = Gravity.CENTER
                }

                val labelView = TextView(this@MainActivity).apply {
                    text = title
                    textSize = 11f
                    typeface = Typeface.DEFAULT
                    setTextColor(Color.parseColor("#64748B"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, 0)
                }

                addView(iconView)
                addView(labelView)

                setOnClickListener {
                    if (currentScreen != screen) {
                        currentScreen = screen
                        refreshUi()
                    }
                }
            }
            bottomNavViews[screen] = itemContainer
            navBar.addView(itemContainer)
        }

        return navBar
    }

    private fun updateBottomNavSelection() {
        val activeScreen = when (currentScreen) {
            Screen.DASHBOARD -> Screen.DASHBOARD
            Screen.USAGE, Screen.LIMITS -> Screen.USAGE
            Screen.ANALYTICS, Screen.STATISTICS -> Screen.ANALYTICS
            Screen.SETTINGS, Screen.PERMISSIONS, Screen.SUPPORTED_APPS -> Screen.SETTINGS
        }

        val density = resources.displayMetrics.density
        for ((screen, container) in bottomNavViews) {
            val isSelected = screen == activeScreen
            val labelView = container.getChildAt(1) as? TextView

            if (isSelected) {
                container.background = createPillDrawable(Color.parseColor("#EEF2FF"))
                labelView?.setTextColor(Color.parseColor("#4F46E5"))
                labelView?.typeface = Typeface.DEFAULT_BOLD
            } else {
                container.background = createRippleCardDrawable(Color.TRANSPARENT, 16f * density)
                labelView?.setTextColor(Color.parseColor("#64748B"))
                labelView?.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun renderScreen(state: DashboardUiState) {
        contentContainer.removeAllViews()
        contentContainer.alpha = 0f
        contentContainer.animate().alpha(1f).setDuration(160L).start()

        when (state.currentScreen) {
            Screen.DASHBOARD -> renderDashboard(state)
            Screen.USAGE, Screen.LIMITS -> renderUsage(state)
            Screen.ANALYTICS, Screen.STATISTICS -> renderAnalytics(state)
            Screen.SETTINGS -> renderSettings(state)
            Screen.SUPPORTED_APPS -> renderSupportedApps(state)
            Screen.PERMISSIONS -> renderPermissions(state)
        }
    }

    // =========================================================================
    // SCREEN 1: DASHBOARD (HOME)
    // =========================================================================
    private fun renderDashboard(state: DashboardUiState) {
        // 1. Missing Permissions Warning Banner
        if (!state.allCriticalPermissionsGranted) {
            val banner = createModernCardView(
                bgColor = Color.parseColor("#FFFBEB"),
                strokeColor = Color.parseColor("#FDE68A")
            ).apply {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val headerRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = "😐"
                    textSize = 24f
                    setPadding(0, 0, dp(8), 0)
                })
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = "Usage data unavailable"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#B45309"))
                })
                box.addView(headerRow)

                box.addView(TextView(this@MainActivity).apply {
                    text = "ReelGuard needs Usage Access, Overlay, and Accessibility to track your screen time and enforce boundaries."
                    textSize = 13f
                    setTextColor(Color.parseColor("#78350F"))
                    setPadding(0, dp(4), 0, dp(10))
                })

                box.addView(Button(this@MainActivity).apply {
                    text = "Complete Permission Setup (1-tap)"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    val density = resources.displayMetrics.density
                    background = createRippleButtonDrawable(Color.parseColor("#D97706"), 12f * density)
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        currentScreen = Screen.PERMISSIONS
                        refreshUi()
                    }
                })
                addView(box)
            }
            contentContainer.addView(banner)
        }

        // 2. Today's Combined Screen Time Hero Card (Face Emoji Feedback)
        val wellbeing = WellbeingEmojiHelper.getTimeWellbeingStatus(
            totalSecondsUsed = state.totalSecondsToday,
            dailyLimitMinutes = pendingGlobalLimitMinutes,
            hasPermission = state.hasUsagePermission
        )

        val heroCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val emojiView = TextView(this@MainActivity).apply {
                text = wellbeing.emoji
                textSize = 34f
                setPadding(0, 0, dp(10), 0)
            }
            topRow.addView(emojiView)

            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = wellbeing.title
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = wellbeing.message
                textSize = 12.5f
                setTextColor(Color.parseColor("#475569"))
            })
            topRow.addView(titleCol)

            val pctBadge = TextView(this@MainActivity).apply {
                text = if (pendingGlobalLimitMinutes > 0 && state.hasUsagePermission) {
                    val p = (state.totalSecondsToday.toFloat() / (pendingGlobalLimitMinutes * 60f)) * 100f
                    "%.1f%% used".format(p)
                } else "Goal"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(wellbeing.colorHex))
                background = createPillDrawable(Color.parseColor(wellbeing.badgeBgHex))
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            topRow.addView(pctBadge)
            box.addView(topRow)

            // Primary Stat Numbers
            val statRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(6))
            }

            val timeCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            timeCol.addView(TextView(this@MainActivity).apply {
                text = "Today's Consumption"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
            timeCol.addView(TextView(this@MainActivity).apply {
                val used = AppUsageData.formatDuration(state.totalSecondsToday)
                val limit = AppUsageData.formatDuration(pendingGlobalLimitMinutes * 60L)
                text = "$used / $limit"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            statRow.addView(timeCol)

            val remainingSeconds = ((pendingGlobalLimitMinutes * 60L) - state.totalSecondsToday).coerceAtLeast(0L)
            val remCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            remCol.addView(TextView(this@MainActivity).apply {
                text = "Remaining"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.END
            })
            remCol.addView(TextView(this@MainActivity).apply {
                text = "${AppUsageData.formatDuration(remainingSeconds)} left"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4F46E5"))
                gravity = Gravity.END
            })
            statRow.addView(remCol)
            box.addView(statRow)

            // Animated Smooth Progress Bar
            val pct = if (pendingGlobalLimitMinutes > 0) {
                (state.totalSecondsToday.toFloat() / (pendingGlobalLimitMinutes * 60f)) * 100f
            } else 0f
            box.addView(createAnimatedProgressBar(pct, wellbeing.colorHex, heightDp = 10))

            addView(box)
        }
        contentContainer.addView(heroCard)

        // 2b. Daily Mindful Thought Card
        val mindfulQuote = WellbeingQuotes.getRandomQuote()
        val quoteCard = createModernCardView(
            bgColor = Color.parseColor("#F5F3FF"), // Calm indigo/violet-50
            strokeColor = Color.parseColor("#DDD6FE") // Violet-200
        ).apply {
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(4))
            }
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "🌱"
                textSize = 16f
                setPadding(0, 0, dp(6), 0)
            })
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "Daily Mindful Thought"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#5B21B6")) // Violet-800
            })
            box.addView(titleRow)

            box.addView(TextView(this@MainActivity).apply {
                text = mindfulQuote
                textSize = 13.5f
                setTextColor(Color.parseColor("#3730A3")) // Indigo-800
            })
            addView(box)
        }
        contentContainer.addView(quoteCard)

        // 3. Quick Actions Grid
        contentContainer.addView(createSectionHeader("Quick Actions", "Instant access to controls and tools"))

        val quickRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        quickRow1.addView(createQuickActionCard("🎯 App Limits", "Configure per-app limits") {
            currentScreen = Screen.USAGE
            refreshUi()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginEnd = dp(6)
            }
        })
        quickRow1.addView(createQuickActionCard("📊 6-Month Analytics", "View trends & graphs") {
            currentScreen = Screen.ANALYTICS
            refreshUi()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = dp(6)
            }
        })
        contentContainer.addView(quickRow1)

        val quickRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }
        quickRow2.addView(createQuickActionCard("📱 Monitored Apps", "Instagram, YouTube, Facebook") {
            currentScreen = Screen.SUPPORTED_APPS
            refreshUi()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginEnd = dp(6)
            }
        })
        quickRow2.addView(createQuickActionCard("⚙️ Settings & Backup", "CSV backup and options") {
            currentScreen = Screen.SETTINGS
            refreshUi()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = dp(6)
            }
        })
        contentContainer.addView(quickRow2)

        // 4. Individual App Cards (Separate Daily Limits for Instagram, YouTube, and Facebook)
        contentContainer.addView(createSectionHeader("Platform Activity", "Individual daily limits and screen time consumption"))

        for (supported in SupportedApp.entries) {
            val pkg = supported.packageName
            val appUsage = state.appsUsage.firstOrNull { it.packageName == pkg }
            val secsUsed = appUsage?.secondsToday ?: 0L
            val limitMin = pendingAppLimits[pkg] ?: UsageStore.getAppLimitMinutes(this, pkg)
            val isLimitEnabled = pendingAppEnabled[pkg] ?: UsageStore.isAppLimitEnabled(this, pkg)
            val remSecs = if (isLimitEnabled) ((limitMin * 60L) - secsUsed).coerceAtLeast(0L) else 0L

            val iconStr = when (supported) {
                SupportedApp.INSTAGRAM -> "📷"
                SupportedApp.YOUTUBE -> "🎬"
                SupportedApp.FACEBOOK -> "👥"
            }

            val appWellbeing = if (isLimitEnabled) {
                WellbeingEmojiHelper.getTimeWellbeingStatus(secsUsed, limitMin, true)
            } else {
                null
            }

            val card = createModernCardView(bgColor = Color.WHITE).apply {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

                // Top row: App Name & Status Pill
                val topRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(this@MainActivity).apply {
                    text = iconStr
                    textSize = 22f
                    setPadding(0, 0, dp(10), 0)
                })

                val titleCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = supported.displayName
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                })
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = if (isLimitEnabled) "Limit: ${limitMin}m daily" else "Daily limit disabled"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                })
                topRow.addView(titleCol)

                val badge = TextView(this@MainActivity).apply {
                    if (isLimitEnabled && appWellbeing != null) {
                        text = "${appWellbeing.emoji} ${appWellbeing.title}"
                        setTextColor(Color.parseColor(appWellbeing.colorHex))
                        background = createPillDrawable(Color.parseColor(appWellbeing.badgeBgHex))
                    } else {
                        text = "⚪ Off"
                        setTextColor(Color.parseColor("#64748B"))
                        background = createPillDrawable(Color.parseColor("#F1F5F9"))
                    }
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                topRow.addView(badge)
                box.addView(topRow)

                // Time Spent and Remaining Stats
                val timeRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(4))
                }
                timeRow.addView(TextView(this@MainActivity).apply {
                    text = AppUsageData.formatDuration(secsUsed)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                })
                timeRow.addView(TextView(this@MainActivity).apply {
                    text = if (isLimitEnabled) {
                        "  •  ${AppUsageData.formatDuration(remSecs)} remaining"
                    } else {
                        "  •  Unlimited today"
                    }
                    textSize = 13f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(dp(4), dp(4), 0, 0)
                })
                box.addView(timeRow)

                // Progress Bar
                if (isLimitEnabled) {
                    val appPct = if (limitMin > 0) (secsUsed.toFloat() / (limitMin * 60f)) * 100f else 0f
                    val barColor = appWellbeing?.colorHex ?: "#4F46E5"
                    box.addView(createAnimatedProgressBar(appPct, barColor, heightDp = 8))
                }

                // Edit Limit Button
                val editBtn = Button(this@MainActivity).apply {
                    text = "Configure Limit"
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    val density = resources.displayMetrics.density
                    background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                    setTextColor(Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                        topMargin = dp(8)
                    }
                    setOnClickListener {
                        currentScreen = Screen.USAGE
                        refreshUi()
                    }
                }
                box.addView(editBtn)

                addView(box)
            }
            contentContainer.addView(card)
        }

        // 5. Foreground Protection Service Status Card
        val protectionCard = createModernCardView(
            bgColor = if (state.isMonitorRunning) Color.WHITE else Color.parseColor("#F1F5F9")
        ).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(this@MainActivity).apply {
                text = if (state.isMonitorRunning) "🟢" else "⚪"
                textSize = 16f
                setPadding(0, 0, dp(8), 0)
            })

            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = if (state.isMonitorRunning) "Foreground Protection Active" else "Protection Paused"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = if (state.isMonitorRunning)
                    "Tracking active screen time and floating usage overlays."
                else
                    "Start to begin foreground app tracking and floating counter."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
            topRow.addView(titleCol)
            box.addView(topRow)

            val actionBtn = Button(this@MainActivity).apply {
                text = if (state.isMonitorRunning) "Pause Protection" else "Start Protection"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                if (state.isMonitorRunning) {
                    background = createRippleButtonDrawable(Color.parseColor("#E2E8F0"), 12f * density)
                    setTextColor(Color.parseColor("#334155"))
                } else {
                    background = createRippleButtonDrawable(Color.parseColor("#4F46E5"), 12f * density)
                    setTextColor(Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(12)
                }
                setOnClickListener {
                    if (state.isMonitorRunning) stopMonitorService() else startMonitorService()
                }
            }
            box.addView(actionBtn)
            addView(box)
        }
        contentContainer.addView(protectionCard)
    }

    // =========================================================================
    // SCREEN 2: INDEPENDENT APP LIMITS & USAGE
    // =========================================================================
    private fun renderUsage(state: DashboardUiState) {
        contentContainer.addView(createHeaderSection(
            title = "App Limits & Goals",
            subtitle = "Set independent daily time limits for each supported platform."
        ))

        for (supported in SupportedApp.entries) {
            val pkg = supported.packageName
            val currentLimit = pendingAppLimits[pkg] ?: UsageStore.getAppLimitMinutes(this, pkg)
            val isEnabled = pendingAppEnabled[pkg] ?: UsageStore.isAppLimitEnabled(this, pkg)
            val appUsage = state.appsUsage.firstOrNull { it.packageName == pkg }
            val secsUsed = appUsage?.secondsToday ?: 0L
            val remSecs = if (isEnabled) ((currentLimit * 60L) - secsUsed).coerceAtLeast(0L) else 0L

            val iconStr = when (supported) {
                SupportedApp.INSTAGRAM -> "📷"
                SupportedApp.YOUTUBE -> "🎬"
                SupportedApp.FACEBOOK -> "👥"
            }

            val card = createModernCardView(bgColor = Color.WHITE).apply {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

                // Top Header Row: Icon, App Name & Enable/Disable Toggle Pill
                val headerRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = iconStr
                    textSize = 22f
                    setPadding(0, 0, dp(10), 0)
                })

                val titleCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = "${supported.displayName} Daily Limit"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                })
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = "Consumed: ${AppUsageData.formatDuration(secsUsed)}  •  ${if (isEnabled) "${AppUsageData.formatDuration(remSecs)} left" else "Disabled"}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                })
                headerRow.addView(titleCol)

                // Toggle Button
                val toggleBtn = TextView(this@MainActivity).apply {
                    text = if (isEnabled) "🟢 Active" else "⚪ Disabled"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isEnabled) Color.parseColor("#065F46") else Color.parseColor("#64748B"))
                    background = createPillDrawable(if (isEnabled) Color.parseColor("#ECFDF5") else Color.parseColor("#F1F5F9"))
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener {
                        val newEnabled = !isEnabled
                        pendingAppEnabled[pkg] = newEnabled
                        UsageStore.setAppLimit(this@MainActivity, pkg, currentLimit, newEnabled)
                        Toast.makeText(this@MainActivity, "${supported.displayName} limit ${if (newEnabled) "enabled" else "disabled"}.", Toast.LENGTH_SHORT).show()
                        refreshUi()
                    }
                }
                headerRow.addView(toggleBtn)
                box.addView(headerRow)

                // Limit Display
                val valueLabel = TextView(this@MainActivity).apply {
                    text = "$currentLimit minutes / day"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isEnabled) Color.parseColor("#4F46E5") else Color.parseColor("#94A3B8"))
                    setPadding(0, dp(12), 0, dp(8))
                }
                box.addView(valueLabel)

                // Presets Row
                val presetsRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(8))
                }
                val presets = listOf(15, 30, 45, 60, 90, 120)
                for (p in presets) {
                    presetsRow.addView(Button(this@MainActivity).apply {
                        text = "${p}m"
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        val density = resources.displayMetrics.density
                        val isSelected = currentLimit == p
                        background = if (isSelected)
                            createRippleButtonDrawable(Color.parseColor("#4F46E5"), 10f * density)
                        else
                            createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                        setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#334155"))
                        layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.0f).apply { marginEnd = dp(4) }
                        setOnClickListener {
                            pendingAppLimits[pkg] = p
                            valueLabel.text = "$p minutes / day"
                            UsageStore.setAppLimit(this@MainActivity, pkg, p, isEnabled)
                            refreshUi()
                        }
                    })
                }
                box.addView(presetsRow)

                // Steppers Row
                val stepperRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(8))
                }
                stepperRow.addView(Button(this@MainActivity).apply {
                    text = "- 5 minutes"
                    textSize = 12f
                    val density = resources.displayMetrics.density
                    background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                    setTextColor(Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1.0f).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        val newLimit = (currentLimit - 5).coerceAtLeast(5)
                        pendingAppLimits[pkg] = newLimit
                        valueLabel.text = "$newLimit minutes / day"
                        UsageStore.setAppLimit(this@MainActivity, pkg, newLimit, isEnabled)
                    }
                })
                stepperRow.addView(Button(this@MainActivity).apply {
                    text = "+ 5 minutes"
                    textSize = 12f
                    val density = resources.displayMetrics.density
                    background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                    setTextColor(Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1.0f).apply { marginStart = dp(4) }
                    setOnClickListener {
                        val newLimit = (currentLimit + 5).coerceAtMost(360)
                        pendingAppLimits[pkg] = newLimit
                        valueLabel.text = "$newLimit minutes / day"
                        UsageStore.setAppLimit(this@MainActivity, pkg, newLimit, isEnabled)
                    }
                })
                box.addView(stepperRow)

                // Save Button
                box.addView(Button(this@MainActivity).apply {
                    text = "Save ${supported.displayName} Limit"
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    val density = resources.displayMetrics.density
                    background = createRippleButtonDrawable(Color.parseColor("#4F46E5"), 10f * density)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                        topMargin = dp(4)
                    }
                    setOnClickListener {
                        val savedMin = pendingAppLimits[pkg] ?: currentLimit
                        UsageStore.setAppLimit(this@MainActivity, pkg, savedMin, isEnabled)
                        Toast.makeText(this@MainActivity, "Saved: ${supported.displayName} set to ${savedMin}m daily", Toast.LENGTH_SHORT).show()
                        refreshUi()
                    }
                })

                addView(box)
            }
            contentContainer.addView(card)
        }

        // Global Combined Screen Time Goal Card
        val globalCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "🌐 Overall Daily Screen Time Goal"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "Overall threshold across all monitored platforms combined."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, dp(8))
            })

            val globalLabel = TextView(this@MainActivity).apply {
                text = "$pendingGlobalLimitMinutes minutes / day"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4F46E5"))
                setPadding(0, 0, 0, dp(8))
            }
            box.addView(globalLabel)

            val globalPresets = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (p in listOf(30, 60, 90, 120, 180)) {
                globalPresets.addView(Button(this@MainActivity).apply {
                    text = "${p}m"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    val density = resources.displayMetrics.density
                    val isSelected = pendingGlobalLimitMinutes == p
                    background = if (isSelected)
                        createRippleButtonDrawable(Color.parseColor("#4F46E5"), 8f * density)
                    else
                        createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 8f * density)
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1.0f).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        pendingGlobalLimitMinutes = p
                        UsageStore.setLimits(this@MainActivity, p, UsageStore.limitReels(this@MainActivity))
                        refreshUi()
                    }
                })
            }
            box.addView(globalPresets)
            addView(box)
        }
        contentContainer.addView(globalCard)
    }

    // =========================================================================
    // SCREEN 3: SIX-MONTH USAGE ANALYTICS & TRENDS
    // =========================================================================
    private fun renderAnalytics(state: DashboardUiState) {
        contentContainer.addView(createHeaderSection(
            title = "Usage Analytics & Trends",
            subtitle = "Explore up to 6 months of persistent local screen time history."
        ))

        // Time Horizon Filter Selector (7D, 30D, 3M, 6M)
        val horizonCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "Time Horizon"
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(0, 0, 0, dp(8))
            })

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val options = listOf(
                Pair(7, "7 Days"),
                Pair(30, "30 Days"),
                Pair(90, "3 Months"),
                Pair(180, "6 Months")
            )
            val density = resources.displayMetrics.density
            for ((days, label) in options) {
                val isSelected = selectedHorizonDays == days
                row.addView(Button(this@MainActivity).apply {
                    text = label
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    background = if (isSelected)
                        createRippleButtonDrawable(Color.parseColor("#4F46E5"), 10f * density)
                    else
                        createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.0f).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        selectedHorizonDays = days
                        refreshUi()
                    }
                })
            }
            box.addView(row)
            addView(box)
        }
        contentContainer.addView(horizonCard)

        // App Filter Chips (All Apps, Instagram, YouTube, Facebook)
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(10))
        }
        val density = resources.displayMetrics.density

        val appFilters = listOf(
            Pair(null, "All Monitored Apps"),
            Pair("com.instagram.android", "Instagram"),
            Pair("com.google.android.youtube", "YouTube"),
            Pair("com.facebook.katana", "Facebook")
        )
        for ((pkg, label) in appFilters) {
            val isSelected = selectedAnalyticsPackage == pkg
            filterRow.addView(TextView(this).apply {
                text = label
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#475569"))
                background = if (isSelected)
                    createPillDrawable(Color.parseColor("#4F46E5"))
                else
                    createPillDrawable(Color.parseColor("#E2E8F0"))
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    selectedAnalyticsPackage = pkg
                    refreshUi()
                }
            })
        }
        contentContainer.addView(filterRow)

        // Interactive Canvas Chart Card
        val today = LocalDate.now()
        val startDate = today.minusDays((selectedHorizonDays - 1).toLong())
        val allRecords = usageRepository.getDateRangeHistory(startDate, today)
        val records = if (selectedAnalyticsPackage != null) {
            allRecords.filter { it.packageName == selectedAnalyticsPackage }
        } else {
            allRecords
        }

        // Group records into chart bar entries
        val recordsByDate: Map<LocalDate, List<com.example.reelguard.data.DailyUsageRecord>> = records.groupBy { it.date }
        val chartEntries = mutableListOf<ChartBarEntry>()
        var d = startDate
        val dateFormatter = DateTimeFormatter.ofPattern(if (selectedHorizonDays <= 30) "M/d" else "MM/yy")
        while (!d.isAfter(today)) {
            val dayRecords = recordsByDate[d] ?: emptyList()
            val appSecs = mutableMapOf<String, Long>()
            var dayTotalSec = 0L
            for (r in dayRecords) {
                appSecs[r.packageName] = r.secondsUsed
                dayTotalSec += r.secondsUsed
            }
            chartEntries.add(
                ChartBarEntry(
                    date = d,
                    label = d.format(dateFormatter),
                    appSeconds = appSecs,
                    totalSeconds = dayTotalSec,
                    hasData = dayTotalSec > 0L
                )
            )
            d = d.plusDays(1)
        }

        val chartCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

            val chartHeader = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val chartTitle = TextView(this@MainActivity).apply {
                text = "📊 Daily Screen Time (${selectedHorizonDays} Days)"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            chartHeader.addView(chartTitle)
            box.addView(chartHeader)

            val tooltipInfo = TextView(this@MainActivity).apply {
                text = "💡 Touch any bar to inspect daily duration"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, dp(8))
            }
            box.addView(tooltipInfo)

            val chartView = UsageAnalyticsChartView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(230)
                )
                this.entries = chartEntries
                this.selectedPackageFilter = selectedAnalyticsPackage
                this.onEntrySelectedListener = { selected ->
                    if (selected != null) {
                        val duration = AppUsageData.formatDuration(selected.totalSeconds)
                        tooltipInfo.text = "📅 ${selected.date}: $duration total usage"
                        tooltipInfo.setTextColor(Color.parseColor("#4F46E5"))
                    } else {
                        tooltipInfo.text = "💡 Touch any bar to inspect daily duration"
                        tooltipInfo.setTextColor(Color.parseColor("#64748B"))
                    }
                }
            }
            box.addView(chartView)

            addView(box)
        }
        contentContainer.addView(chartCard)

        // Key Metrics Summary Grid (Total Time, Daily Avg, Most-Used App, Trend)
        val analyticsSummary = usageRepository.getAnalyticsSummary(today)
        val totalSecsPeriod = chartEntries.sumOf { it.totalSeconds }
        val avgSecsPeriod = if (selectedHorizonDays > 0) totalSecsPeriod / selectedHorizonDays.toLong() else 0L

        val metricsCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "Key Performance Indicators"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(0, 0, 0, dp(10))
            })

            val row1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            row1.addView(createMetricBox("Total Screen Time", AppUsageData.formatDuration(totalSecsPeriod)).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply { marginEnd = dp(6) }
            })
            row1.addView(createMetricBox("Daily Average", "${AppUsageData.formatDuration(avgSecsPeriod)} / day").apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply { marginStart = dp(6) }
            })
            box.addView(row1)

            val row2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val mostUsedName = when (analyticsSummary.mostUsedPackage) {
                "com.instagram.android" -> "Instagram"
                "com.google.android.youtube" -> "YouTube"
                "com.facebook.katana" -> "Facebook"
                else -> "Balanced"
            }
            val trendText = when {
                analyticsSummary.changePercentageVsPreviousWeek == null -> "Stable"
                analyticsSummary.changePercentageVsPreviousWeek > 0f -> "📈 +%.0f%% vs last wk".format(analyticsSummary.changePercentageVsPreviousWeek)
                else -> "📉 %.0f%% vs last wk".format(analyticsSummary.changePercentageVsPreviousWeek)
            }
            row2.addView(createMetricBox("Most Active Platform", mostUsedName).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply { marginEnd = dp(6) }
            })
            row2.addView(createMetricBox("Weekly Trend", trendText).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply { marginStart = dp(6) }
            })
            box.addView(row2)

            addView(box)
        }
        contentContainer.addView(metricsCard)

        // 7-Day Weekly Summary Card
        val weekly = usageRepository.getWeeklySummary()
        val weeklyCard = createModernCardView(
            bgColor = Color.parseColor("#EEF2FF"),
            strokeColor = Color.parseColor("#C7D2FE")
        ).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "📅 7-Day Rolling Summary"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4338CA"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "Total: ${weekly.formattedTotalTime}  •  Average: ${weekly.formattedDailyAverageTime}/day"
                textSize = 13f
                setTextColor(Color.parseColor("#3730A3"))
                setPadding(0, dp(4), 0, 0)
            })
            addView(box)
        }
        contentContainer.addView(weeklyCard)

        // 30-Day Monthly Summary Card
        val monthly = usageRepository.getMonthlySummary(today.year, today.monthValue)
        val monthlyCard = createModernCardView(
            bgColor = Color.parseColor("#F0FDF4"),
            strokeColor = Color.parseColor("#BBF7D0")
        ).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "🗓️ 30-Day Monthly Summary"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#166534"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "Total: ${monthly.formattedTotalTime}  •  Average: ${monthly.formattedDailyAverageTime}/day  •  Top App: ${monthly.mostUsedAppName}"
                textSize = 13f
                setTextColor(Color.parseColor("#15803D"))
                setPadding(0, dp(4), 0, 0)
            })
            addView(box)
        }
        contentContainer.addView(monthlyCard)

        // Data Backup & Export
        contentContainer.addView(createSectionHeader("Data Backup & Export", "Export up to six months of persistent usage records to a CSV file"))

        // Export Usage Data Action
        contentContainer.addView(Button(this).apply {
            text = "📥 Export Usage Data (CSV)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            val density = resources.displayMetrics.density
            background = createRippleButtonDrawable(Color.parseColor("#4F46E5"), 12f * density)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            setOnClickListener {
                launchExportCsv()
            }
        })
    }

    private fun createMetricBox(label: String, value: String): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRippleCardDrawable(
                bgColor = Color.parseColor("#F8FAFC"),
                cornerRadiusPx = 12f * density,
                strokeColor = Color.parseColor("#E2E8F0"),
                strokeWidthPx = (1f * density).toInt()
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11.5f
                setTextColor(Color.parseColor("#64748B"))
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    // =========================================================================
    // SCREEN 4: SETTINGS & PREFERENCES
    // =========================================================================
    private fun renderSettings(state: DashboardUiState) {
        contentContainer.addView(createHeaderSection(
            title = "Settings & Preferences",
            subtitle = "Customize floating monitor, permissions, backups, and privacy."
        ))

        // Floating Screen Time Counter Toggle Card
        val overlayCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this@MainActivity).apply {
                text = "🪟"
                textSize = 20f
                setPadding(0, 0, dp(8), 0)
            })
            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Floating Usage Counter"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Show session duration and daily progress above apps while scrolling."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
            row.addView(titleCol)
            box.addView(row)

            val actionRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            actionRow.addView(Button(this@MainActivity).apply {
                text = if (state.hasOverlayPermission) "Overlay Enabled" else "Grant Overlay Permission"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                background = createRippleButtonDrawable(
                    if (state.hasOverlayPermission) Color.parseColor("#ECFDF5") else Color.parseColor("#4F46E5"),
                    10f * density
                )
                setTextColor(if (state.hasOverlayPermission) Color.parseColor("#065F46") else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
                setOnClickListener {
                    if (!state.hasOverlayPermission) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else {
                        Toast.makeText(this@MainActivity, "Floating counter permission is active.", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            box.addView(actionRow)
            addView(box)
        }
        contentContainer.addView(overlayCard)

        // Supported Apps Quick Access Card
        val platformsCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this@MainActivity).apply {
                text = "📱"
                textSize = 20f
                setPadding(0, 0, dp(8), 0)
            })
            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Supported Platforms"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "View Instagram, YouTube, and Facebook screen time tracking details."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
            row.addView(titleCol)
            box.addView(row)

            box.addView(Button(this@MainActivity).apply {
                text = "Manage Supported Platforms"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                setTextColor(Color.parseColor("#334155"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    topMargin = dp(10)
                }
                setOnClickListener {
                    currentScreen = Screen.SUPPORTED_APPS
                    refreshUi()
                }
            })
            addView(box)
        }
        contentContainer.addView(platformsCard)

        // System Permissions Access Card
        val permCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this@MainActivity).apply {
                text = "🔒"
                textSize = 20f
                setPadding(0, 0, dp(8), 0)
            })
            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "System Permissions"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Usage access, overlay, and accessibility status."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
            row.addView(titleCol)
            box.addView(row)

            box.addView(Button(this@MainActivity).apply {
                text = "Manage Permissions"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                setTextColor(Color.parseColor("#334155"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    topMargin = dp(10)
                }
                setOnClickListener {
                    currentScreen = Screen.PERMISSIONS
                    refreshUi()
                }
            })
            addView(box)
        }
        contentContainer.addView(permCard)

        // CSV Export Card
        val backupCard = createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "📦 Data Backup & Export"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "Export your 6-month historical usage records to CSV format for backup and personal review."
                textSize = 12.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(4), 0, dp(10))
            })
            box.addView(Button(this@MainActivity).apply {
                text = "Export Usage Data (CSV)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                background = createRippleButtonDrawable(Color.parseColor("#4F46E5"), 10f * density)
                setTextColor(Color.WHITE)
                setOnClickListener {
                    launchExportCsv()
                }
            })
            addView(box)
        }
        contentContainer.addView(backupCard)

        // Privacy Transparency Card
        val privacyCard = createModernCardView(
            bgColor = Color.parseColor("#F0FDF4"),
            strokeColor = Color.parseColor("#BBF7D0")
        ).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "🌿 Privacy Guarantee"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#166534"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "• ReelGuard is 100% offline and requires NO internet permission.\n" +
                        "• Screen content is never recorded or transmitted anywhere.\n" +
                        "• History is durably stored in local SQLite database for 6+ months under your control.\n" +
                        "• Accessibility is strictly used on-device to track foreground active states."
                textSize = 12.5f
                setTextColor(Color.parseColor("#15803D"))
                setPadding(0, dp(6), 0, 0)
            })
            addView(box)
        }
        contentContainer.addView(privacyCard)

        // Protected History & System Data Management Information Card
        val storageInfoCard = createModernCardView(
            bgColor = Color.parseColor("#F8FAFC"),
            strokeColor = Color.parseColor("#E2E8F0")
        ).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(this@MainActivity).apply {
                text = "🛡️ Protected History & Data"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            box.addView(TextView(this@MainActivity).apply {
                text = "• ReelGuard permanently preserves your six-month usage history across restarts and reboots.\n" +
                        "• In-app history deletion is disabled to protect your digital wellbeing journey.\n" +
                        "• System notice: Full app data reset can only be performed manually through Android System Settings → Apps → ReelGuard → Storage."
                textSize = 12.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(4), 0, 0)
            })
            addView(box)
        }
        contentContainer.addView(storageInfoCard)
    }

    // =========================================================================
    // SUB-SCREEN 5: PERMISSIONS MANAGEMENT
    // =========================================================================
    private fun renderPermissions(state: DashboardUiState) {
        val header = createHeaderWithBack("System Permissions", "Allow ReelGuard to track usage and show overlays.") {
            currentScreen = Screen.SETTINGS
            refreshUi()
        }
        contentContainer.addView(header)

        // 1. Usage Access
        contentContainer.addView(createPermissionItemCard(
            title = "Usage Access",
            isGranted = state.hasUsagePermission,
            description = "Allow ReelGuard to measure how long supported apps stay in the foreground.",
            actionText = "Open Usage Access Settings"
        ) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })

        // 2. Display Over Other Apps
        contentContainer.addView(createPermissionItemCard(
            title = "Floating Usage Counter (Overlay)",
            isGranted = state.hasOverlayPermission,
            description = "Show your session timer and daily progress above apps while scrolling.",
            actionText = "Open Overlay Permission Settings"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        // 3. Accessibility Service
        contentContainer.addView(createPermissionItemCard(
            title = "Accessibility Service (Activity Detection)",
            isGranted = state.hasAccessibilityPermission,
            description = "Allow ReelGuard to detect supported app transitions locally on-device. Does not read messages or personal content.",
            actionText = "Open Accessibility Settings"
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // 4. Notifications
        contentContainer.addView(createPermissionItemCard(
            title = "Post Notifications",
            isGranted = state.hasNotificationPermission,
            description = "Required on Android 13+ to display ongoing service status and daily limit warnings.",
            actionText = "Request Notification Permission"
        ) {
            requestNotificationPermission()
        })

        // 5. Battery Optimization
        contentContainer.addView(createPermissionItemCard(
            title = "Unrestricted Battery (Keep App Alive)",
            isGranted = state.hasBatteryOptimizationIgnored,
            description = "Prevent Android from pausing the usage monitor while in the background.",
            actionText = if (state.hasBatteryOptimizationIgnored) "Battery Unrestricted" else "Set Battery Unrestricted"
        ) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {}
            }
        })
    }

    // =========================================================================
    // SUB-SCREEN 6: SUPPORTED PLATFORMS
    // =========================================================================
    private fun renderSupportedApps(state: DashboardUiState) {
        val header = createHeaderWithBack("Supported Platforms", "ReelGuard actively monitors usage across these platforms.") {
            currentScreen = Screen.DASHBOARD
            refreshUi()
        }
        contentContainer.addView(header)

        for (supported in SupportedApp.entries) {
            val pkg = supported.packageName
            val appUsage = state.appsUsage.firstOrNull { it.packageName == pkg }
            val timeSpent = appUsage?.formattedUsageTime ?: "0s"
            val limitMin = pendingAppLimits[pkg] ?: UsageStore.getAppLimitMinutes(this, pkg)
            val isEnabled = pendingAppEnabled[pkg] ?: UsageStore.isAppLimitEnabled(this, pkg)
            val remSecs = if (isEnabled) ((limitMin * 60L) - (appUsage?.secondsToday ?: 0L)).coerceAtLeast(0L) else 0L

            val iconStr = when (supported) {
                SupportedApp.INSTAGRAM -> "📷"
                SupportedApp.YOUTUBE -> "🎬"
                SupportedApp.FACEBOOK -> "👥"
            }

            val card = createModernCardView(bgColor = Color.WHITE).apply {
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                row.addView(TextView(this@MainActivity).apply {
                    text = iconStr
                    textSize = 22f
                    setPadding(0, 0, dp(10), 0)
                })

                val titleCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = supported.displayName
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                })
                titleCol.addView(TextView(this@MainActivity).apply {
                    text = "Package: ${supported.packageName}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, dp(2), 0, dp(6))
                })
                row.addView(titleCol)

                val badge = TextView(this@MainActivity).apply {
                    text = if (isEnabled) "🟢 Active" else "⚪ Disabled"
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isEnabled) Color.parseColor("#065F46") else Color.parseColor("#64748B"))
                    background = createPillDrawable(if (isEnabled) Color.parseColor("#ECFDF5") else Color.parseColor("#F1F5F9"))
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                row.addView(badge)
                box.addView(row)

                box.addView(TextView(this@MainActivity).apply {
                    val remText = if (isEnabled) "${AppUsageData.formatDuration(remSecs)} left" else "unlimited"
                    text = "Today's Consumption: $timeSpent foreground time  •  Limit: ${limitMin}m ($remText)"
                    textSize = 13f
                    setTextColor(Color.parseColor("#334155"))
                })
                addView(box)
            }
            contentContainer.addView(card)
        }
    }

    private fun launchExportCsv() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "ReelGuard_Usage_History.csv")
            }
            startActivityForResult(intent, REQUEST_CODE_CREATE_CSV)
        } catch (_: Exception) {
            fallbackExportCsv()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_CSV) {
            if (resultCode == RESULT_OK && data?.data != null) {
                writeCsvToUri(data.data!!)
            } else {
                Toast.makeText(this, "Export cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeCsvToUri(uri: Uri) {
        try {
            val csv = usageRepository.exportToCsv()
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(csv.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Toast.makeText(this, "Usage history exported successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.localizedMessage ?: "Storage error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fallbackExportCsv() {
        try {
            val csv = usageRepository.exportToCsv()
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "ReelGuard_Usage_History.csv")
            file.writeText(csv, Charsets.UTF_8)
            Toast.makeText(this, "Saved to: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // SERVICE CONTROLS
    // =========================================================================
    private fun startMonitorService() {
        if (!PermissionHelper.hasUsageAccess(this)) {
            Toast.makeText(this, "Usage Access required first.", Toast.LENGTH_SHORT).show()
            currentScreen = Screen.PERMISSIONS
            refreshUi()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)) {
            requestNotificationPermission()
        }

        val intent = Intent(this, UsageMonitorService::class.java)
        startForegroundService(intent)
        isMonitorRunning = true
        Toast.makeText(this, "ReelGuard Protection Started!", Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    private fun stopMonitorService() {
        val intent = Intent(this, UsageMonitorService::class.java)
        stopService(intent)
        isMonitorRunning = false
        OverlayController.hide()
        Toast.makeText(this, "ReelGuard Protection Paused.", Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    // =========================================================================
    // VIEW BUILDER HELPERS
    // =========================================================================
    private fun createHeaderSection(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun createHeaderWithBack(title: String, subtitle: String, onBack: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(6))
            }

            val backBtn = TextView(this@MainActivity).apply {
                text = "← Back"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4F46E5"))
                setPadding(0, dp(4), dp(12), dp(4))
                setOnClickListener { onBack() }
            }
            topRow.addView(backBtn)
            addView(topRow)

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun createSectionHeader(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun createQuickActionCard(title: String, subtitle: String, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRippleCardDrawable(
                bgColor = Color.WHITE,
                cornerRadiusPx = 14f * density,
                strokeColor = Color.parseColor("#E2E8F0"),
                strokeWidthPx = (1f * density).toInt()
            )
            elevation = 1.5f * density
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { onClick() }

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun createPermissionItemCard(
        title: String,
        isGranted: Boolean,
        description: String,
        actionText: String,
        onAction: () -> Unit
    ): View {
        return createModernCardView(bgColor = Color.WHITE).apply {
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(this@MainActivity).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            topRow.addView(titleView)

            val badge = TextView(this@MainActivity).apply {
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(4), dp(8), dp(4))
                if (isGranted) {
                    text = "Granted"
                    setTextColor(Color.parseColor("#065F46"))
                    background = createPillDrawable(Color.parseColor("#ECFDF5"))
                } else {
                    text = "Required"
                    setTextColor(Color.parseColor("#991B1B"))
                    background = createPillDrawable(Color.parseColor("#FEF2F2"))
                }
            }
            topRow.addView(badge)
            box.addView(topRow)

            val desc = TextView(this@MainActivity).apply {
                text = description
                textSize = 12.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(6), 0, dp(10))
            }
            box.addView(desc)

            val btn = Button(this@MainActivity).apply {
                text = actionText
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                val density = resources.displayMetrics.density
                if (isGranted) {
                    background = createRippleButtonDrawable(Color.parseColor("#F1F5F9"), 10f * density)
                    setTextColor(Color.parseColor("#475569"))
                } else {
                    background = createRippleButtonDrawable(Color.parseColor("#4F46E5"), 10f * density)
                    setTextColor(Color.WHITE)
                }
                setOnClickListener { onAction() }
            }
            box.addView(btn)

            addView(box)
        }
    }

    private fun createModernCardView(
        bgColor: Int = Color.WHITE,
        cornerRadiusDp: Int = 18,
        strokeColor: Int = Color.parseColor("#E2E8F0"),
        strokeWidthDp: Int = 1
    ): LinearLayout {
        val density = resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val strokePx = (strokeWidthDp * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRippleCardDrawable(
                bgColor = bgColor,
                cornerRadiusPx = radiusPx,
                strokeColor = strokeColor,
                strokeWidthPx = strokePx
            )
            elevation = 2f * density
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            layoutParams = params
        }
    }

    private fun createAnimatedProgressBar(
        percentage: Float,
        colorHex: String,
        heightDp: Int = 10
    ): View {
        val density = resources.displayMetrics.density
        val heightPx = (heightDp * density).toInt()
        val radiusPx = heightPx / 2f

        val track = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E2E8F0"))
                cornerRadius = radiusPx
            }
        }

        val progressFill = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = radiusPx
            }
        }

        val targetFraction = (percentage.coerceIn(0f, 100f) / 100f).coerceAtLeast(0.015f)

        track.post {
            val totalWidth = track.width
            if (totalWidth > 0) {
                val animator = ValueAnimator.ofFloat(0f, targetFraction)
                animator.duration = 300L
                animator.interpolator = AccelerateDecelerateInterpolator()
                animator.addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    val w = (totalWidth * fraction).toInt()
                    progressFill.layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                animator.start()
            }
        }

        track.addView(progressFill)
        return track
    }

    private fun createRippleCardDrawable(
        bgColor: Int,
        cornerRadiusPx: Float,
        strokeColor: Int = 0,
        strokeWidthPx: Int = 0
    ): Drawable {
        val content = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = cornerRadiusPx
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#14000000")),
            content,
            mask
        )
    }

    private fun createRippleButtonDrawable(
        bgColor: Int,
        cornerRadiusPx: Float
    ): Drawable {
        val content = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = cornerRadiusPx
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#26FFFFFF")),
            content,
            mask
        )
    }

    private fun createPillDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
