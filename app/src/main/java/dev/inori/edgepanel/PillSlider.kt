package dev.inori.edgepanel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View

/** Loads a vector icon tinted white. Returns null instead of crashing if it can't be loaded. */
fun loadWhiteIcon(ctx: Context, res: Int): Drawable? = try {
    ctx.getDrawable(res)?.mutate()?.apply { setTint(Color.WHITE) }
} catch (e: Exception) {
    null
}

/** Rounded pill slider (ColorOS control-centre style): icon on the left, fill grows to the right. */
class PillSlider(
    context: Context,
    iconRes: Int,
    private val trackColor: Int,
    private val fillColor: Int
) : View(context) {

    var max = 15
    var value = 0
        set(v) {
            field = v.coerceIn(0, if (max > 0) max else 0)
            invalidate()
        }
    var onChange: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val icon: Drawable? = loadWhiteIcon(context, iconRes)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW)

        c.save()
        c.clipPath(clip)
        paint.color = trackColor
        c.drawRect(rect, paint)
        if (max > 0) {
            paint.color = fillColor
            c.drawRect(0f, 0f, w * value / max, h, paint)
        }
        c.restore()

        icon?.let {
            val s = (h * 0.5f).toInt()
            val left = (h * 0.28f).toInt()
            val top = ((h - s) / 2).toInt()
            it.setBounds(left, top, left + s, top + s)
            it.draw(c)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
        }
        return true
    }

    private fun update(x: Float) {
        if (width == 0 || max <= 0) return
        val v = Math.round((x / width).coerceIn(0f, 1f) * max)
        if (v != value) {
            value = v
            onChange?.invoke(v)
        }
    }
}
