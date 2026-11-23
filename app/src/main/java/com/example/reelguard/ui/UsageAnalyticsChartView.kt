package com.example.reelguard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.SupportedApp
import java.time.LocalDate

data class ChartBarEntry(
    val date: LocalDate,
    val label: String,
    val appSeconds: Map<String, Long>,
    val totalSeconds: Long,
    val hasData: Boolean
)

/**
 * High-performance, Canvas-drawn interactive analytics chart for 6-month historical usage.
 * Supports daily, weekly, and monthly aggregations, independent app filtering,
 * tooltips, and smooth animations with zero external library overhead.
 */
class UsageAnalyticsChartView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var entries: List<ChartBarEntry> = emptyList()
        set(value) {
            field = value
            selectedEntryIndex = -1
            startEntryAnimation()
        }

    var selectedPackageFilter: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var onEntrySelectedListener: ((ChartBarEntry?) -> Unit)? = null

    private var selectedEntryIndex = -1
    private var animationProgress = 1.0f

    // Colors
    private val colorInstagram = Color.parseColor("#E1306C")
    private val colorYouTube = Color.parseColor("#EF4444")
    private val colorFacebook = Color.parseColor("#1877F2")
    private val colorPrimary = Color.parseColor("#4F46E5")
    private val colorGrid = Color.parseColor("#E2E8F0")
    private val colorText = Color.parseColor("#64748B")
    private val colorTooltipBg = Color.parseColor("#0F172A")

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }

    private val gridDashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = dp(10.5f)
        typeface = Typeface.DEFAULT
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorTooltipBg
        style = Paint.Style.FILL
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
        isFocusable = true
    }

    private fun startEntryAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || entries.isEmpty()) {
            drawEmptyState(canvas, w, h)
            return
        }

        val paddingLeft = dp(38f)
        val paddingRight = dp(14f)
        val paddingTop = dp(28f)
        val paddingBottom = dp(24f)

        val chartWidth = w - paddingLeft - paddingRight
        val chartHeight = h - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Calculate maximum seconds for Y-axis scaling
        val maxSeconds = entries.maxOfOrNull { entry ->
            if (selectedPackageFilter != null) {
                entry.appSeconds[selectedPackageFilter] ?: 0L
            } else {
                entry.totalSeconds
            }
        } ?: 0L

        // Round max seconds to pleasant interval (at least 30 mins)
        val yMax = ((maxSeconds / 1800L) + 1) * 1800L
        val yMaxSafe = yMax.coerceAtLeast(3600L) // Minimum scale: 1 hour

        // 2. Draw Horizontal Grid Lines & Y-Axis Labels
        val steps = 3
        for (i in 0..steps) {
            val fraction = i.toFloat() / steps.toFloat()
            val y = (paddingTop + chartHeight) - (fraction * chartHeight)
            val secLabel = (fraction * yMaxSafe).toLong()

            canvas.drawLine(paddingLeft, y, w - paddingRight, y, if (i == 0) gridPaint else gridDashedPaint)

            val labelStr = formatShortDuration(secLabel)
            val textWidth = textPaint.measureText(labelStr)
            canvas.drawText(labelStr, paddingLeft - textWidth - dp(6f), y + dp(3.5f), textPaint)
        }

        // 3. Draw Bars for Each Date Entry
        val count = entries.size
        val slotWidth = chartWidth / count.toFloat()
        val barGroupWidth = (slotWidth * 0.72f).coerceAtMost(dp(36f))

        for (i in entries.indices) {
            val entry = entries[i]
            val centerX = paddingLeft + (i * slotWidth) + (slotWidth / 2f)

            // Draw X-axis date label (skip alternate labels if crowded)
            val shouldDrawLabel = when {
                count <= 10 -> true
                count <= 20 -> i % 2 == 0
                count <= 31 -> i % 4 == 0
                else -> i % (count / 6).coerceAtLeast(1) == 0
            }
            if (shouldDrawLabel) {
                val labelWidth = textPaint.measureText(entry.label)
                canvas.drawText(
                    entry.label,
                    centerX - (labelWidth / 2f),
                    h - dp(6f),
                    textPaint
                )
            }

            val baseY = paddingTop + chartHeight

            if (!entry.hasData || entry.totalSeconds == 0L) {
                // Draw clean "no data / zero" indicator dot at baseline
                canvas.drawCircle(centerX, baseY - dp(3f), dp(2.5f), noDataPaint)
                continue
            }

            if (selectedPackageFilter != null) {
                // Single App View
                val sec = entry.appSeconds[selectedPackageFilter] ?: 0L
                val barH = ((sec.toFloat() / yMaxSafe.toFloat()) * chartHeight * animationProgress).coerceAtLeast(dp(2f))
                val left = centerX - (barGroupWidth / 2f)
                val right = left + barGroupWidth
                val top = baseY - barH

                val color = getAppColor(selectedPackageFilter!!)
                barPaint.color = color
                val rect = RectF(left, top, right, baseY)
                canvas.drawRoundRect(rect, dp(4f), dp(4f), barPaint)
            } else {
                // Multi-App Grouped Bars side by side
                val apps = listOf(
                    SupportedApp.INSTAGRAM.packageName,
                    SupportedApp.YOUTUBE.packageName,
                    SupportedApp.FACEBOOK.packageName
                )
                val subBarWidth = barGroupWidth / apps.size
                var subLeft = centerX - (barGroupWidth / 2f)

                for (pkg in apps) {
                    val sec = entry.appSeconds[pkg] ?: 0L
                    if (sec > 0) {
                        val barH = ((sec.toFloat() / yMaxSafe.toFloat()) * chartHeight * animationProgress).coerceAtLeast(dp(2f))
                        val top = baseY - barH
                        barPaint.color = getAppColor(pkg)
                        val rect = RectF(subLeft, top, subLeft + subBarWidth - dp(1f), baseY)
                        canvas.drawRoundRect(rect, dp(3f), dp(3f), barPaint)
                    }
                    subLeft += subBarWidth
                }
            }
        }

        // 4. Draw Floating Tooltip on Selected Touch
        if (selectedEntryIndex in entries.indices) {
            drawTooltip(canvas, entries[selectedEntryIndex], slotWidth, paddingLeft, paddingTop, w)
        }
    }

    private fun drawTooltip(
        canvas: Canvas,
        entry: ChartBarEntry,
        slotWidth: Float,
        paddingLeft: Float,
        paddingTop: Float,
        totalWidth: Float
    ) {
        val centerX = paddingLeft + (selectedEntryIndex * slotWidth) + (slotWidth / 2f)

        val line1 = "${entry.date}: ${AppUsageData.formatDuration(entry.totalSeconds)}"
        val line2 = if (selectedPackageFilter != null) {
            val appName = SupportedApp.fromPackageName(selectedPackageFilter!!)?.displayName ?: "App"
            val sec = entry.appSeconds[selectedPackageFilter] ?: 0L
            "$appName: ${AppUsageData.formatDuration(sec)}"
        } else {
            val ig = AppUsageData.formatDuration(entry.appSeconds[SupportedApp.INSTAGRAM.packageName] ?: 0L)
            val yt = AppUsageData.formatDuration(entry.appSeconds[SupportedApp.YOUTUBE.packageName] ?: 0L)
            val fb = AppUsageData.formatDuration(entry.appSeconds[SupportedApp.FACEBOOK.packageName] ?: 0L)
            "IG: $ig • YT: $yt • FB: $fb"
        }

        val textW = maxOf(tooltipTextPaint.measureText(line1), tooltipTextPaint.measureText(line2))
        val bubbleW = textW + dp(16f)
        val bubbleH = dp(34f)

        var bubbleLeft = centerX - (bubbleW / 2f)
        if (bubbleLeft < dp(8f)) bubbleLeft = dp(8f)
        if (bubbleLeft + bubbleW > totalWidth - dp(8f)) bubbleLeft = totalWidth - bubbleW - dp(8f)
        val bubbleTop = paddingTop - dp(18f)

        val rect = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), tooltipBgPaint)

        canvas.drawText(line1, bubbleLeft + dp(8f), bubbleTop + dp(13f), tooltipTextPaint)
        tooltipTextPaint.textSize = dp(9.5f)
        canvas.drawText(line2, bubbleLeft + dp(8f), bubbleTop + dp(27f), tooltipTextPaint)
        tooltipTextPaint.textSize = dp(11f)
    }

    private fun drawEmptyState(canvas: Canvas, w: Float, h: Float) {
        val text = "No usage records available for this date range"
        val textW = textPaint.measureText(text)
        canvas.drawText(text, (w - textW) / 2f, h / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (entries.isEmpty()) return super.onTouchEvent(event)

        val paddingLeft = dp(38f)
        val paddingRight = dp(14f)
        val chartWidth = width - paddingLeft - paddingRight
        if (chartWidth <= 0) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val relX = event.x - paddingLeft
                val slotWidth = chartWidth / entries.size.toFloat()
                val idx = (relX / slotWidth).toInt().coerceIn(0, entries.size - 1)
                if (idx != selectedEntryIndex) {
                    selectedEntryIndex = idx
                    onEntrySelectedListener?.invoke(entries[idx])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Keep selected point for review
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getAppColor(packageName: String): Int {
        return when (packageName) {
            SupportedApp.INSTAGRAM.packageName -> colorInstagram
            SupportedApp.YOUTUBE.packageName -> colorYouTube
            SupportedApp.FACEBOOK.packageName -> colorFacebook
            else -> colorPrimary
        }
    }

    private fun formatShortDuration(seconds: Long): String {
        val m = seconds / 60
        val h = m / 60
        return if (h > 0) "${h}h" else "${m}m"
    }
}
