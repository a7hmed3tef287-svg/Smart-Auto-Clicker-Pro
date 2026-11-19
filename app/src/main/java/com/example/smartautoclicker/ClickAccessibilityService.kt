package com.example.smartautoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

class ClickAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var rules: List<ClickRule> = emptyList()
    private var repeatCycles = 0
    private var currentIndex = 0
    private var completedCycles = 0
    private var failedGestures = 0
    private var isRunning = false
    private var stopOverlay: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = stopAutomation()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    fun startAutomation(points: List<ClickRule>, cycles: Int) {
        stopAutomation()
        if (points.isEmpty()) return
        rules = points.toList()
        repeatCycles = cycles.coerceAtLeast(0)
        currentIndex = 0
        completedCycles = 0
        failedGestures = 0
        isRunning = true
        if (!showStopOverlay()) {
            stopAutomation()
            return
        }
        handler.postDelayed({ performNextTap() }, START_DELAY_MS)
    }

    fun stopAutomation() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        removeStopOverlay()
        rules = emptyList()
        currentIndex = 0
        completedCycles = 0
    }

    private fun performNextTap() {
        if (!isRunning || rules.isEmpty()) return
        val rule = rules[currentIndex]
        val metrics = resources.displayMetrics
        val x = rule.clickX.coerceIn(0, metrics.widthPixels - 1).toFloat()
        val y = rule.clickY.coerceIn(0, metrics.heightPixels - 1).toFloat()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                failedGestures = 0
                scheduleFollowingTap(rule.delayMs)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                failedGestures += 1
                if (failedGestures >= MAX_FAILED_GESTURES) {
                    stopAutomation()
                } else {
                    handler.postDelayed({ performNextTap() }, RETRY_DELAY_MS)
                }
            }
        }, handler)

        if (!accepted) {
            failedGestures += 1
            if (failedGestures >= MAX_FAILED_GESTURES) stopAutomation()
            else handler.postDelayed({ performNextTap() }, RETRY_DELAY_MS)
        }
    }

    private fun scheduleFollowingTap(delayMs: Long) {
        if (!isRunning) return
        currentIndex += 1
        if (currentIndex >= rules.size) {
            currentIndex = 0
            completedCycles += 1
            if (repeatCycles > 0 && completedCycles >= repeatCycles) {
                stopAutomation()
                return
            }
        }
        handler.postDelayed({ performNextTap() }, delayMs.coerceAtLeast(MIN_DELAY_MS))
    }

    private fun showStopOverlay(): Boolean {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = TextView(this).apply {
                text = "إيقاف النقر  ×"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(0xFFB3261E.toInt())
                    cornerRadius = dp(24).toFloat()
                }
                elevation = dp(8).toFloat()
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = dp(12)
                y = dp(120)
            }

            attachDragAndStop(view, params, wm)
            wm.addView(view, params)
            windowManager = wm
            overlayParams = params
            stopOverlay = view
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun attachDragAndStop(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        try { wm.updateViewLayout(view, params) } catch (_: Exception) { stopAutomation() }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) stopAutomation()
                    true
                }
                else -> true
            }
        }
    }

    private fun removeStopOverlay() {
        val view = stopOverlay ?: return
        try { windowManager?.removeView(view) } catch (_: Exception) { }
        stopOverlay = null
        overlayParams = null
        windowManager = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopAutomation()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val START_DELAY_MS = 3_000L
        private const val TAP_DURATION_MS = 60L
        private const val MIN_DELAY_MS = 50L
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_FAILED_GESTURES = 3

        @Volatile private var instance: ClickAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, ClickAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun startAutomation(context: Context, points: List<ClickRule>, cycles: Int): Boolean {
            val service = instance ?: return false
            service.startAutomation(points, cycles)
            return service.isRunning
        }

        fun stopAutomation() {
            instance?.stopAutomation()
        }
    }
}
