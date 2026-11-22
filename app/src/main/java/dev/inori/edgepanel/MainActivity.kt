package dev.inori.edgepanel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny setup screen. The real work happens in PanelService. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            textSize = 16f
            text = "1. Enable \"Edge Panel\" in Accessibility settings.\n\n" +
                "2. (Optional) Grant Do Not Disturb access so the Ring and " +
                "Notification sliders can go all the way to silent.\n\n" +
                "Then swipe in from the right edge of the screen."
        })
        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Grant Do Not Disturb access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
