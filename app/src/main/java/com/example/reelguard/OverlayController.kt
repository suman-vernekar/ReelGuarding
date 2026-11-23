package com.example.reelguard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.reelguard.overlay.OverlayStateController
import com.example.reelguard.overlay.OverlayStatusLevel
import com.example.reelguard.util.PermissionHelper
import com.example.reelguard.util.WellbeingQuotes

/**
 * Manages the floating WindowManager overlay counter.
 * Displays real-time per-app remaining time, face emojis, and persistent limit-reached warnings.
 * Fully draggable to prevent blocking underlying app UI.
 */
object OverlayController {

    private val stateController = OverlayStateController()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: LinearLayout? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var quoteView: TextView? = null
    private var closeView: TextView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Drag tracking variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Warning quote rotation state (stable during timer ticks)
    private var activeWarningQuote: String? = null
    private var activeWarningPackage: String? = null
    private var warningDisplayTicks: Int = 0
    const val ROTATION_TICK_THRESHOLD = 30 // ~30 seconds before rotating message

    init {
        UsageStore.addReelCountListener { pkg, _ ->
            mainHandler.post {
                val currentPkg = stateController.currentPackage
                if (currentPkg == pkg && rootView != null) {
                    val ctx = rootView?.context ?: return@post
                    updateView(ctx, pkg, System.currentTimeMillis())
                }
            }
        }
    }

    fun show(context: Context, packageName: String) {
        mainHandler.post {
            showInternal(context, packageName)
        }
    }

    private fun showInternal(context: Context, packageName: String) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return

        val now = System.currentTimeMillis()
        stateController.onPackageForegrounded(packageName, now)

        val usage = UsageStore.getAppUsage(appContext, packageName)
        val isLimitReached = usage.isLimitReached ||
            (usage.isLimitEnabled && usage.dailyTimeLimitMinutes > 0 && usage.secondsToday >= usage.dailyTimeLimitMinutes * 60L)

        if (!stateController.shouldDisplayOverlay(isLimitReached)) {
            hideInternal()
            return
        }

        if (rootView != null && stateController.currentPackage == packageName) {
            updateView(appContext, packageName, now)
            return
        }

        hideInternal()

        try {
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createOverlayView(appContext, packageName, now)
        } catch (_: Exception) {
            cleanup()
        }
    }

    private fun createOverlayView(context: Context, packageName: String, now: Long) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            elevation = 12f
        }

        // Header Row: Title (Emoji + App Name) & Close Button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val close = TextView(context).apply {
            this.text = "✕"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(6), dp(0), dp(2), dp(0))
            setOnClickListener {
                stateController.dismissCurrentSession()
                hide()
            }
        }

        headerRow.addView(title)
        headerRow.addView(close)
        root.addView(headerRow)

        // Subtitle: Remaining Time or Warning Text
        val subtitle = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(3), 0, 0)
        }
        root.addView(subtitle)

        // Rotating Motivational Quote or Screen-Time Fact (shown when limit reached)
        val quote = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setTextColor(Color.parseColor("#FECACA"))
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }
        root.addView(quote)

        rootView = root
        titleView = title
        subtitleView = subtitle
        quoteView = quote
        closeView = close

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(72)
        }

        // Attach dragging listener so overlay does not obscure in-app controls
        root.setOnTouchListener { view, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            val wm = windowManager ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initialX + (event.rawX - initialTouchX).toInt()
                    lp.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        wm.updateViewLayout(view, lp)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        layoutParams = params
        try {
            windowManager?.addView(root, params)
            updateView(context, packageName, now)
        } catch (_: Exception) {
            cleanup()
        }
    }

    private fun updateView(context: Context, packageName: String, now: Long) {
        val root = rootView ?: return
        val title = titleView ?: return
        val subtitle = subtitleView ?: return
        val quote = quoteView
        val close = closeView

        val usage = UsageStore.getAppUsage(context, packageName)
        val hasPermission = PermissionHelper.hasUsageAccess(context)
        val uiModel = stateController.computeUiModel(usage, now, hasPermission)

        if (uiModel == null) {
            hideInternal()
            return
        }

        if (uiModel.statusLevel == OverlayStatusLevel.LIMIT_REACHED) {
            // Requirements 5 & 6: App Name, Emoji, Clear Warning Title, Short Explanatory Sentence
            val expectedTitle = "😔 ${uiModel.appDisplayName} • Daily Limit Reached"
            if (title.text != expectedTitle) {
                title.text = expectedTitle
            }

            val expectedSubtitle = "You've reached your daily time allowance. Consider taking a break."
            if (subtitle.text != expectedSubtitle) {
                subtitle.text = expectedSubtitle
            }
            subtitle.setTextColor(Color.parseColor("#FECACA"))

            // Requirements 5 & 6: Close button is completely hidden when limit is reached!
            close?.visibility = View.GONE

            // Requirement 4: Rotate quote on new session or after displayed several times (~30 ticks)
            val shouldRotate = activeWarningQuote == null ||
                activeWarningPackage != packageName ||
                warningDisplayTicks >= ROTATION_TICK_THRESHOLD

            if (shouldRotate) {
                activeWarningQuote = WellbeingQuotes.getNextWarningQuote()
                activeWarningPackage = packageName
                warningDisplayTicks = 0
            } else {
                warningDisplayTicks++
            }

            val targetQuote = activeWarningQuote ?: ""
            if (quote != null) {
                quote.visibility = View.VISIBLE
                if (quote.text != targetQuote) {
                    if (quote.text.isNullOrEmpty()) {
                        quote.text = targetQuote
                        quote.alpha = 1f
                    } else {
                        // Requirement 4: Lightweight fade transition of approximately 200 ms
                        quote.animate()
                            ?.alpha(0f)
                            ?.setDuration(100L)
                            ?.withEndAction {
                                quote.text = targetQuote
                                quote.animate()
                                    ?.alpha(1f)
                                    ?.setDuration(100L)
                                    ?.start()
                            }
                            ?.start()
                    }
                }
            }
        } else {
            // Normal or Warning state
            if (title.text != uiModel.titleText) {
                title.text = uiModel.titleText
            }
            if (subtitle.text != uiModel.subtitleText) {
                subtitle.text = uiModel.subtitleText
            }

            close?.visibility = View.VISIBLE
            quote?.visibility = View.GONE
            quote?.text = ""

            // Reset quote rotation state when not in limit-reached mode
            activeWarningQuote = null
            activeWarningPackage = null
            warningDisplayTicks = 0

            if (uiModel.statusLevel == OverlayStatusLevel.WARNING) {
                subtitle.setTextColor(Color.parseColor("#FDE68A"))
            } else {
                subtitle.setTextColor(Color.parseColor("#E2E8F0"))
            }
        }

        // Update styling based on warning/limit state
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val bg = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            when (uiModel.statusLevel) {
                OverlayStatusLevel.LIMIT_REACHED -> {
                    setColor(Color.parseColor("#EE7F1D1D")) // Deep calm red tonal background
                    setStroke(dp(2), Color.parseColor("#EF4444"))
                }
                OverlayStatusLevel.WARNING -> {
                    setColor(Color.parseColor("#EE78350F")) // Amber translucent
                    setStroke(dp(2), Color.parseColor("#F59E0B"))
                }
                OverlayStatusLevel.NORMAL -> {
                    setColor(Color.parseColor("#EE0F172A")) // Slate-900 translucent
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }
        root.background = bg
    }

    fun hide(context: Context? = null) {
        mainHandler.post {
            hideInternal()
        }
    }

    private fun hideInternal() {
        try {
            val v = rootView
            val wm = windowManager
            if (v != null && wm != null) {
                wm.removeView(v)
            }
        } catch (_: Exception) {
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        rootView = null
        titleView = null
        subtitleView = null
        quoteView = null
        closeView = null
        windowManager = null
        layoutParams = null
    }

    fun onPackageBackgrounded() {
        mainHandler.post {
            resetWarningQuoteState()
            stateController.onPackageBackgrounded()
            hideInternal()
        }
    }

    fun resetWarningQuoteState() {
        activeWarningQuote = null
        activeWarningPackage = null
        warningDisplayTicks = 0
    }

    fun getActiveWarningQuote(): String? = activeWarningQuote
}
