package pl.perier.sirion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import pl.perier.sirion.R
import kotlin.math.roundToInt

/**
 * Centred slider for values that go both ways (EQ): 8px track, a tick at
 * zero, accent fill from the centre. The knob is the shared slider_thumb,
 * so it looks exactly like every [Slider], ring while dragging included.
 */
@SuppressLint("ViewConstructor")
class CentredSlider(context: Context, private val range: Int, value: Int, private val onChange: (Int, Boolean) -> Unit) : View(context) {
    var value = value.coerceIn(-range, range)
        private set

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_track) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.text_muted) }
    private val knob = context.getDrawable(R.drawable.slider_thumb)!!.mutate()
    private val rect = RectF()
    private var dragging = false

    /** Half the shared knob's 44px box, so the knob stays inside the view at both ends. */
    private val knobRadius get() = knob.intrinsicWidth / 2f
    private val left get() = knobRadius
    private val right get() = width - knobRadius

    private fun xFor(v: Int) = left + (right - left) * (v + range) / (2f * range)

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val half = 4 * density
        rect.set(left, cy - half, right, cy + half)
        canvas.drawRoundRect(rect, half, half, track)
        val zero = xFor(0)
        val x = xFor(value)
        rect.set(minOf(zero, x), cy - half, maxOf(zero, x), cy + half)
        canvas.drawRect(rect, fill)
        rect.set(zero - density, cy - 8 * density, zero + density, cy + 8 * density)
        canvas.drawRoundRect(rect, density, density, tick)
        knob.state = if (dragging) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
        knob.setBounds((x - knobRadius).toInt(), (cy - knobRadius).toInt(), (x + knobRadius).toInt(), (cy + knobRadius).toInt())
        knob.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val raw = ((event.x - left) / (right - left) * 2 * range - range).roundToInt().coerceIn(-range, range)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dragging = true
            }
            // A cancel (the screen is being rebuilt) has no real position: keep the value.
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                set(raw)
                onChange(value, true)
                return true
            }
        }
        if (raw != value) { set(raw); onChange(value, false) }
        return true
    }

    private fun set(v: Int) {
        value = v
        invalidate()
    }
}
