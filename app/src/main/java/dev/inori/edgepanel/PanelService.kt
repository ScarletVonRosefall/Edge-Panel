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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * One service does everything: edge handle, side panel, and actions.
 * The panel view only exists while it is open, so idle RAM stays minimal.
 */
class PanelService : AccessibilityService() {

    // ---- tweak these ----
    private val onRight = true            // false = left edge
    private val handleWidthDp = 18        // touch strip width
    private val handleHeightDp = 260      // touch strip height (centered)
    private val swipeThresholdDp = 20     // how far to swipe to open
    private val handleColor = 0x01000000  // near-invisible; try 0x44FFFFFF to see it
    // ---------------------

    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private val ui = Handler(Looper.getMainLooper())
    private var density = 1f
    private var handle: View? = null
    private var panel: View? = null

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

    private fun addHandle() {
        val v = View(this)
        v.setBackgroundColor(handleColor)
        var startX = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX = e.rawX
                MotionEvent.ACTION_MOVE ->
                    if ((startX - e.rawX) * sign > dp(swipeThresholdDp) && panel == null) showPanel()
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            dp(handleWidthDp), dp(handleHeightDp),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = edge or Gravity.CENTER_VERTICAL }
        wm.addView(v, lp)
        handle = v
    }

    private fun showPanel() {
        if (panel != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2202124.toInt())
                cornerRadius = dp(24).toFloat()
            }
            // Tap outside the panel closes it.
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
                false
            }
        }

        val streams = listOf(
            "Media" to AudioManager.STREAM_MUSIC,
            "Ring" to AudioManager.STREAM_RING,
            "Alarm" to AudioManager.STREAM_ALARM,
            "Notifications" to AudioManager.STREAM_NOTIFICATION
        )
        for ((name, stream) in streams) {
            root.addView(TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 13f
            })
            root.addView(SeekBar(this).apply {
                max = audio.getStreamMaxVolume(stream)
                progress = audio.getStreamVolume(stream)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        try {
                            audio.setStreamVolume(stream, p, 0)
                        } catch (e: SecurityException) {
                            // Silent mode needs Do Not Disturb access.
                            Toast.makeText(
                                this@PanelService,
                                "Grant Do Not Disturb access in the Edge Panel app",
                                Toast.LENGTH_SHORT
                            ).show()
                            sb.progress = audio.getStreamVolume(stream)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            })
        }

        // Hide the panel first, wait for it to disappear, then act, so the
        // panel doesn't appear in the screenshot or sit on top of the power menu.
        root.addView(actionButton("Screenshot") {
            hidePanel()
            ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, 400)
        })
        root.addView(actionButton("Power menu (restart / power off)") {
            hidePanel()
            ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) }, 150)
        })
        root.addView(actionButton("Lock screen") {
            hidePanel()
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        })

        val lp = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = edge or Gravity.CENTER_VERTICAL
            x = dp(8)
        }
        wm.addView(root, lp)
        panel = root

        root.translationX = (dp(280) * sign).toFloat()
        root.animate().translationX(0f).setDuration(150).start()
    }

    private fun actionButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
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
}
