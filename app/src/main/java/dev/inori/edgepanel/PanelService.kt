package dev.inori.edgepanel

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs

/**
 * One service does everything: a small edge handle, a compact panel, and the actions.
 * The panel only exists while it is open, so idle RAM stays minimal.
 */
class PanelService : AccessibilityService() {

    // ---- tweak these ----
    private val onRight = true            // false = left edge
    private val edgeInsetDp = 0           // gap between screen edge and handle (0 = flush with the edge)
    private val handleWidthDp = 36        // touch area width
    private val handleHeightDp = 200      // touch area height
    private val openOnSwipe = false       // inward swipe also opens, but on the very edge it collides with the back gesture
    private val swipeThresholdDp = 14     // inward swipe distance (only used if openOnSwipe = true)
    private val panelEdgePadDp = 24       // extra padding on the edge side of the panel, keeps sliders clear of the back-gesture zone
    private val panelWidthDp = 196
    private val colorPanel = 0xF2262628.toInt()
    private val colorTrack = 0xFF444446.toInt()
    private val colorButton = 0xFF3A3A3C.toInt()
    private val colorAccent = 0xFF2BC670.toInt()
    private val colorHandle = 0xCCFFFFFF.toInt()
    // ---------------------

    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private val ui = Handler(Looper.getMainLooper())
    private var density = 1f
    private var handle: View? = null
    private var handleLp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var warned = false

    private val edge get() = if (onRight) Gravity.END else Gravity.START
    private val sign get() = if (onRight) 1 else -1
    private fun dp(v: Int) = (v * density).toInt()

    override fun onServiceConnected() {
        if (handle != null) return
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        density = resources.displayMetrics.density
        addHandle()
    }

    // ---------------------------------------------------------------- handle

    private fun addHandle() {
        val prefs = getSharedPreferences("panel", Context.MODE_PRIVATE)

        val box = FrameLayout(this)
        val pill = View(this)
        pill.background = GradientDrawable().apply {
            setColor(colorHandle)
            cornerRadius = dp(2).toFloat()
        }
        box.addView(pill, FrameLayout.LayoutParams(dp(5), dp(80), edge or Gravity.CENTER_VERTICAL))
        box.alpha = IDLE_ALPHA

        val lp = WindowManager.LayoutParams(
            dp(handleWidthDp), dp(handleHeightDp),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = edge or Gravity.CENTER_VERTICAL
            x = dp(edgeInsetDp)
            y = prefs.getInt("handleY", 0)
        }

        attachHandleTouch(box, lp)
        wm.addView(box, lp)
        handle = box
        handleLp = lp
    }

    /** Tap or swipe inward = open. Long-press then drag = move the handle up/down. */
    private fun attachHandleTouch(v: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val maxY = resources.displayMetrics.heightPixels / 2 - dp(handleHeightDp) / 2
        var downX = 0f
        var downY = 0f
        var startOffset = 0
        var moving = false
        var moved = false

        val longPress = Runnable {
            moving = true
            v.alpha = 1f
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startOffset = lp.y
                    moving = false
                    moved = false
                    v.alpha = 1f
                    ui.postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (downX - e.rawX) * sign
                    val dy = e.rawY - downY
                    if (moving) {
                        lp.y = (startOffset + dy.toInt()).coerceIn(-maxY, maxY)
                        wm.updateViewLayout(v, lp)
                    } else {
                        if (abs(dx) > slop || abs(dy) > slop) {
                            moved = true
                            ui.removeCallbacks(longPress)
                        }
                        if (openOnSwipe && dx > dp(swipeThresholdDp)) {
                            ui.removeCallbacks(longPress)
                            showPanel()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(longPress)
                    if (moving) {
                        getSharedPreferences("panel", Context.MODE_PRIVATE)
                            .edit().putInt("handleY", lp.y).apply()
                    } else if (!moved) {
                        showPanel() // plain tap
                    }
                    v.alpha = IDLE_ALPHA
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longPress)
                    v.alpha = IDLE_ALPHA
                }
            }
            true
        }
    }

    // ----------------------------------------------------------------- panel

    private fun showPanel() {
        if (panel != null) return
        warned = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                if (onRight) dp(12) else dp(panelEdgePadDp), dp(12),
                if (onRight) dp(panelEdgePadDp) else dp(12), dp(12)
            )
            background = GradientDrawable().apply {
                setColor(colorPanel)
                val r = dp(26).toFloat()
                // flush with the edge: only the inner corners are rounded
                cornerRadii = if (onRight) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                              else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
            // Tap outside the panel closes it.
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
                false
            }
        }

        val streams = listOf(
            R.drawable.ic_media to AudioManager.STREAM_MUSIC,
            R.drawable.ic_ring to AudioManager.STREAM_RING,
            R.drawable.ic_alarm to AudioManager.STREAM_ALARM
        )
        for ((iconRes, stream) in streams) {
            val s = PillSlider(this, iconRes, colorTrack, colorAccent)
            s.max = audio.getStreamMaxVolume(stream)
            s.value = audio.getStreamVolume(stream)
            s.onChange = { target -> s.value = applyVolume(stream, target) }
            root.addView(s, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)
            ).apply { bottomMargin = dp(8) })
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = listOf(
            Triple(R.drawable.ic_screenshot, "Screenshot") {
                hidePanel()
                ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, 400)
            },
            Triple(R.drawable.ic_power, "Power menu") {
                hidePanel()
                ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) }, 150)
            },
            Triple(R.drawable.ic_lock, "Lock screen") {
                hidePanel()
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        )
        for ((i, b) in buttons.withIndex()) {
            row.addView(actionButton(b.first, b.second, b.third),
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (i < buttons.size - 1) marginEnd = dp(6)
                })
        }
        root.addView(row)

        val lp = WindowManager.LayoutParams(
            dp(panelWidthDp), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = edge or Gravity.CENTER_VERTICAL
            x = 0
        }
        wm.addView(root, lp)
        panel = root

        root.alpha = 0f
        root.translationX = (dp(60) * sign).toFloat()
        root.animate().alpha(1f).translationX(0f).setDuration(140).start()
    }

    private fun actionButton(iconRes: Int, label: String, onClick: () -> Any?) =
        ImageView(this).apply {
            background = GradientDrawable().apply {
                setColor(colorButton)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = label
            loadWhiteIcon(this@PanelService, iconRes)?.let { setImageDrawable(it) }
            setOnClickListener { onClick() }
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) v.alpha = 0.6f
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) v.alpha = 1f
                false
            }
        }

    /**
     * Sets a volume and returns what the system actually ended up with.
     * Tries setStreamVolume first, then steps like the hardware keys would.
     */
    private fun applyVolume(stream: Int, target: Int): Int {
        try {
            audio.setStreamVolume(stream, target, 0)
        } catch (e: SecurityException) {
            toastOnce("Grant Do Not Disturb access in the Edge Panel app")
            return audio.getStreamVolume(stream)
        }
        var now = audio.getStreamVolume(stream)
        var guard = 30
        while (now != target && guard-- > 0) {
            try {
                audio.adjustStreamVolume(
                    stream,
                    if (target > now) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    0
                )
            } catch (e: SecurityException) {
                break
            }
            val n = audio.getStreamVolume(stream)
            if (n == now) break
            now = n
        }
        if (now != target) toastOnce("System kept volume at $now of ${audio.getStreamMaxVolume(stream)}")
        return now
    }

    private fun toastOnce(msg: String) {
        if (warned) return
        warned = true
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hidePanel() {
        val p = panel ?: return
        panel = null
        try { wm.removeView(p) } catch (_: Exception) {}
    }

    private fun removeAll() {
        hidePanel()
        handle?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        handle = null
        handleLp = null
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeAll()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private companion object {
        const val IDLE_ALPHA = 0.55f
    }
}
