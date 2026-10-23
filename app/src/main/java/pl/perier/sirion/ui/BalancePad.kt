package pl.perier.sirion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import pl.perier.sirion.R
import kotlin.math.roundToInt

/**
 * Balance and fader: a top view of the car with a dot you drag to where the
 * sound should be centred. Balance runs left (negative) to right, fader rear
 * (negative) to front (top).
 */
@SuppressLint("ViewConstructor")
class BalancePad(
    context: Context,
    private val steps: Int,
    balance: Int,
    fader: Int,
    private val onChange: (balance: Int, fader: Int, done: Boolean) -> Unit,
) : View(context) {
    var balance = balance
        private set
    var fader = fader
        private set

    private val d = resources.displayMetrics.density
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_inner) }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * d; color = context.getColor(R.color.surface_track)
    }
    private val seat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface_track) }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = d; color = context.getColor(R.color.surface_track)
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary); textSize = 18 * d; textAlign = Paint.Align.CENTER
        typeface = resources.getFont(R.font.inter_tight_regular)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * d; color = context.getColor(R.color.accent)
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.text_primary) }
    private val r = RectF()

    /** The cabin the dot moves inside. */
    private val cabin get() = RectF(width / 2f - 80 * d, 28 * d + 20 * d, width / 2f + 80 * d, height - 28 * d - 20 * d)

    private fun dotX() = cabin.centerX() + cabin.width() / 2 * balance / steps
    private fun dotY() = cabin.centerY() - cabin.height() / 2 * fader / steps

    override fun onDraw(canvas: Canvas) {
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(r, 10 * d, 10 * d, ground)
        val c = cabin
        r.set(c.left, c.top - 20 * d, c.right, c.bottom + 20 * d)
        canvas.drawRoundRect(r, 60 * d, 60 * d, body)
        // Seats: driver front left, passenger front right, rear bench (left-hand drive).
        r.set(c.centerX() - 60 * d, c.centerY() - 70 * d, c.centerX() - 6 * d, c.centerY() - 10 * d)
        canvas.drawRoundRect(r, 10 * d, 10 * d, seat)
        r.set(c.centerX() + 6 * d, c.centerY() - 70 * d, c.centerX() + 60 * d, c.centerY() - 10 * d)
        canvas.drawRoundRect(r, 10 * d, 10 * d, seat)
        r.set(c.centerX() - 60 * d, c.centerY() + 36 * d, c.centerX() + 60 * d, c.centerY() + 92 * d)
        canvas.drawRoundRect(r, 10 * d, 10 * d, seat)
        canvas.drawLine(20 * d, dotY(), width - 20 * d, dotY(), guide)
        canvas.drawLine(dotX(), 36 * d, dotX(), height - 36 * d, guide)
        canvas.drawText("Front", width / 2f, 22 * d, label)
        canvas.drawText("Rear", width / 2f, height - 10 * d, label)
        canvas.drawCircle(dotX(), dotY(), 30 * d, ring)
        canvas.drawCircle(dotX(), dotY(), 16 * d, knob)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A cancel (the screen is being rebuilt) has no real position: keep the dot where it is.
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true
        val c = cabin
        val b = ((event.x - c.centerX()) / (c.width() / 2) * steps).roundToInt().coerceIn(-steps, steps)
        val f = (-(event.y - c.centerY()) / (c.height() / 2) * steps).roundToInt().coerceIn(-steps, steps)
        val done = event.actionMasked == MotionEvent.ACTION_UP
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
        if (b != balance || f != fader || done) {
            balance = b
            fader = f
            invalidate()
            onChange(b, f, done)
        }
        return true
    }

    fun set(balance: Int, fader: Int) {
        this.balance = balance
        this.fader = fader
        invalidate()
    }
}
