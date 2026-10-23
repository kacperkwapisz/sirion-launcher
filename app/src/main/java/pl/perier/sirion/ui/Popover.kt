package pl.perier.sirion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import pl.perier.sirion.R

/**
 * Finer control of one item. Grows from its trigger with a caret pointing at
 * it; the trigger shows its filled active state and the rest of the screen
 * dims to 62% black. Tapping the dim (or the trigger again) closes it.
 *
 * [Placement.ABOVE_BAR]: from a bottom bar item. The bar isn't dimmed, but a
 * tap anywhere outside the card, the bar included, closes the popover.
 * [Placement.BELOW_TRIGGER]: from a control on the screen (the music source
 * button). The trigger stays bright through a cut-out in the dim.
 */
class Popover(
    private val activity: Activity,
    private val trigger: View,
    private val content: View,
    private val placement: Placement = Placement.ABOVE_BAR,
    private val width: Int = CARD_WIDTH,
) {
    enum class Placement { ABOVE_BAR, BELOW_TRIGGER }

    private val root = activity.findViewById<ViewGroup>(android.R.id.content)
    private var overlay: FrameLayout? = null
    private var triggerBackground: android.graphics.drawable.Drawable? = null

    val isShowing get() = overlay != null

    /** Called however the popover closes, so its owner can forget it. */
    var onDismiss: (() -> Unit)? = null

    fun show() {
        if (isShowing) return
        val screen = root.width
        val box = IntArray(2).also(trigger::getLocationInWindow)
        val origin = IntArray(2).also(root::getLocationInWindow)
        val triggerLeft = box[0] - origin[0]
        val triggerTop = box[1] - origin[1]
        val triggerCentre = triggerLeft + trigger.width / 2

        val dim = DimLayer(activity)
        dim.setOnClickListener { dismiss() }
        content.isClickable = true // taps inside the card don't close it
        when (placement) {
            Placement.ABOVE_BAR -> {
                val left = (triggerCentre - width / 2).coerceIn(EDGE, screen - width - EDGE)
                dim.addView(content, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = left
                    bottomMargin = BAR_HEIGHT + CARET_HEIGHT
                })
                dim.addView(caret(up = false), FrameLayout.LayoutParams(CARET_WIDTH, CARET_HEIGHT + 1, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = triggerCentre - CARET_WIDTH / 2
                    bottomMargin = BAR_HEIGHT
                })
                // Catches taps over the whole screen, but dims only above the bar.
                dim.undimmedBottom = BAR_HEIGHT
                root.addView(dim, FrameLayout.LayoutParams(-1, -1))
                triggerBackground = trigger.background
                trigger.setBackgroundResource(R.drawable.bg_bar_button_active)
            }
            Placement.BELOW_TRIGGER -> {
                // Right edges line up; the card hangs below the trigger.
                val left = (triggerLeft + trigger.width - width).coerceIn(EDGE, screen - width - EDGE)
                val top = triggerTop + trigger.height + GAP + CARET_HEIGHT
                dim.addView(content, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = left
                    topMargin = top
                })
                dim.addView(caret(up = true), FrameLayout.LayoutParams(CARET_WIDTH, CARET_HEIGHT + 1).apply {
                    leftMargin = triggerCentre - CARET_WIDTH / 2
                    topMargin = top - CARET_HEIGHT
                })
                dim.cutout = RectF(
                    triggerLeft.toFloat(), triggerTop.toFloat(),
                    (triggerLeft + trigger.width).toFloat(), (triggerTop + trigger.height).toFloat(),
                )
                root.addView(dim, FrameLayout.LayoutParams(-1, -1))
                trigger.isSelected = true
            }
        }
        overlay = dim
        // Grow in: fade and an 8px rise towards the trigger (or drop, when it hangs below).
        val rise = activity.resources.displayMetrics.density * RISE_DP * if (placement == Placement.BELOW_TRIGGER) -1 else 1
        dim.alpha = 0f
        content.translationY = rise
        dim.animate().alpha(1f).setDuration(SHOW_MS).setInterpolator(DecelerateInterpolator()).start()
        content.animate().translationY(0f).setDuration(SHOW_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    fun dismiss() {
        overlay?.let { dim ->
            dim.isClickable = false
            dim.animate().cancel()
            dim.animate().alpha(0f).setDuration(HIDE_MS).withEndAction {
                (content.parent as? ViewGroup)?.removeView(content)
                root.removeView(dim)
            }.start()
        }
        overlay = null
        when (placement) {
            Placement.ABOVE_BAR -> trigger.background = triggerBackground
            Placement.BELOW_TRIGGER -> trigger.isSelected = false
        }
        onDismiss?.invoke()
        onDismiss = null
    }

    private fun caret(up: Boolean) = ImageView(activity).apply {
        setImageResource(R.drawable.popover_caret)
        if (up) rotation = 180f
    }

    /** 62% black everywhere except [cutout], which stays bright. */
    private class DimLayer(activity: Activity) : FrameLayout(activity) {
        var cutout: RectF? = null
        /** Height at the bottom left bright (the bar). */
        var undimmedBottom = 0
        private val path = Path()

        init {
            setWillNotDraw(false)
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            cutout?.let {
                path.reset()
                path.addRoundRect(it, RADIUS, RADIUS, Path.Direction.CW)
                canvas.clipOutPath(path)
            }
            canvas.clipRect(0, 0, width, height - undimmedBottom)
            canvas.drawColor(DIM)
            canvas.restore()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // A tap on the bright trigger closes the popover too.
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }
    }

    private companion object {
        const val CARD_WIDTH = 500
        const val SHOW_MS = 140L
        const val HIDE_MS = 90L
        const val RISE_DP = 8
        const val EDGE = 8
        const val GAP = 2
        const val BAR_HEIGHT = 84
        const val CARET_WIDTH = 24
        const val CARET_HEIGHT = 12
        const val RADIUS = 10f
        const val DIM = 0x9E000000.toInt()
    }
}
