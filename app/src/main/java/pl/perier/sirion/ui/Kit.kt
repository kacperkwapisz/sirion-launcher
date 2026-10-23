package pl.perier.sirion.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import pl.perier.sirion.R
import kotlin.math.roundToInt

/**
 * The library pieces the settings screens are made of (Paper: System page,
 * "Components" and "Screen pieces"). Every screen is put together from
 * these; none of them draws its own rows or buttons.
 */
class Kit(val context: Context) {
    enum class Dot { CONNECTED, PAIRED, CONNECTING, NONE }

    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    fun color(id: Int) = context.getColor(id)

    fun text(style: Int, value: CharSequence = "") = TextView(context, null, 0, style).apply { text = value }

    /** Row title 22 medium and its one state line 18 secondary. */
    fun titleBlock(title: CharSequence, line: CharSequence?, titleColor: Int = R.color.text_primary) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(R.style.Text_RowTitle, title).apply { setTextColor(color(titleColor)) })
            if (!line.isNullOrEmpty()) addView(text(R.style.Text_Context, line), wrap().apply { topMargin = dp(2) })
        }

    /** A card (#1A1A1A, radius 14) holding rows split by 1px #262626 lines. */
    fun card(vararg rows: View?) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        clipToOutline = true
        rows.filterNotNull().forEachIndexed { i, row ->
            if (i > 0) addView(View(context).apply { setBackgroundColor(color(R.color.surface_inner)) }, LinearLayout.LayoutParams(-1, dp(1)))
            addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Header card: says what the screen controls and its state; the main control sits right. */
    fun header(title: CharSequence, line: CharSequence, trailing: View? = null) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(dp(20), 0, dp(20), 0)
        minimumHeight = dp(84)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(R.style.Text_CardTitle, title))
            addView(text(R.style.Text_Context, line), wrap().apply { topMargin = dp(2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it, LinearLayout.LayoutParams(-2, it.layoutParams?.height ?: -2).apply { marginStart = dp(16) }) }
    }

    /** Back header: 64px back target, title and one line, at most one action. */
    fun backHeader(title: CharSequence, line: CharSequence, onBack: () -> Unit, action: View? = null) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(dp(10), 0, dp(10), 0)
        minimumHeight = dp(84)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.g_back)
            imageTintList = ColorStateList.valueOf(color(R.color.text_primary))
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(R.drawable.bg_bar_button)
            contentDescription = "Back"
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(R.style.Text_CardTitle, title))
            addView(text(R.style.Text_Context, line), wrap().apply { topMargin = dp(2) })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        action?.let { addView(it, wrap().apply { marginStart = dp(16) }) }
    }

    /** A row inside a card: optional leading slot, title + line, optional trailing control. */
    fun row(
        title: CharSequence,
        line: CharSequence? = null,
        leading: View? = null,
        leadingSize: Int = 18,
        trailing: View? = null,
        height: Int = 92,
        titleColor: Int = R.color.text_primary,
        onClick: (() -> Unit)? = null,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(height)
        setPadding(dp(20), dp(8), dp(20), dp(8))
        leading?.let { addView(it, LinearLayout.LayoutParams(dp(leadingSize), dp(leadingSize)).apply { marginEnd = dp(16) }) }
        addView(titleBlock(title, line, titleColor), LinearLayout.LayoutParams(0, -2, 1f))
        // A control that sets its own size (the segmented control's 64px) keeps it.
        trailing?.let { addView(it, ((it.layoutParams as? LinearLayout.LayoutParams) ?: wrap()).apply { marginStart = dp(16) }) }
        if (onClick != null) {
            setBackgroundResource(R.drawable.bg_row)
            setOnClickListener { onClick() }
        }
    }

    /** White pill: the one likely next step (Connect, Join, Pair). */
    fun pill(label: CharSequence, onClick: () -> Unit) = button(label, R.drawable.bg_pill, R.color.text_on_light, onClick)

    /** Secondary button sharing a row with a [pill]: the same pill shape, dark (#262626). */
    fun pillSecondary(label: CharSequence, onClick: () -> Unit) = button(label, R.drawable.bg_pill_dark, R.color.text_primary, onClick)

    /** Inner button (#262626, radius 10); with a chevron it opens a detail screen. */
    fun inner(label: CharSequence, chevron: Boolean = false, onClick: () -> Unit) =
        button(label, R.drawable.bg_inner, R.color.text_primary, onClick).apply {
            if (chevron) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.g_chevron, 0)
                compoundDrawableTintList = ColorStateList.valueOf(color(R.color.text_secondary))
                compoundDrawablePadding = dp(10)
                setPadding(dp(22), 0, dp(18), 0)
            }
        }

    private fun button(label: CharSequence, background: Int, textColor: Int, onClick: () -> Unit) =
        text(R.style.Text_ButtonSmall, label).apply {
            setBackgroundResource(background)
            setTextColor(color(textColor))
            gravity = Gravity.CENTER
            minHeight = dp(64)
            height = dp(64)
            setPadding(dp(22), 0, dp(22), 0)
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun toggle(on: Boolean, onChange: (Boolean) -> Unit) = Switch(context).apply {
        setThumbResource(R.drawable.switch_thumb)
        setTrackResource(R.drawable.switch_track)
        thumbTintList = null
        trackTintList = null
        showText = false
        switchMinWidth = dp(76)
        isChecked = on
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    /** 18px status slot: connected green dot, paired outline, spinner while connecting. */
    fun dot(state: Dot): View {
        if (state == Dot.CONNECTING) {
            return ProgressBar(context).apply { indeterminateTintList = ColorStateList.valueOf(color(R.color.text_secondary)) }
        }
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when (state) {
                Dot.CONNECTED -> setColor(color(R.color.connected))
                Dot.PAIRED -> setStroke(dp(2), color(R.color.text_muted))
                else -> setColor(0)
            }
        }
        return View(context).apply { background = InsetDrawable(circle, if (state == Dot.CONNECTED) dp(4) else dp(5)) }
    }

    fun glyph(id: Int, tint: Int = R.color.text_primary) = ImageView(context).apply {
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(color(tint))
    }

    /** Segmented control: track #262626, chosen segment accent with black text. */
    fun segmented(options: List<String>, chosen: Int, onPick: (Int) -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(R.drawable.bg_inner)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        options.forEachIndexed { i, label ->
            addView(text(R.style.Segment, label).apply {
                setPadding(dp(26), 0, dp(26), 0)
                if (i == chosen) {
                    setBackgroundResource(R.drawable.bg_segment_chosen)
                    setTextColor(color(R.color.text_on_light))
                }
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(-2, -1).apply { if (i > 0) marginStart = dp(4) })
        }
        layoutParams = LinearLayout.LayoutParams(-2, dp(64))
    }

    /** Slider row: label lane, slider, right-aligned tabular value. */
    fun sliderRow(label: String, line: String?, slider: View, value: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(80)
        setPadding(dp(20), dp(6), dp(20), dp(6))
        addView(titleBlock(label, line), LinearLayout.LayoutParams(dp(150), -2))
        addView(slider, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(20) })
        value.gravity = Gravity.END
        addView(value, LinearLayout.LayoutParams(dp(96), -2))
    }

    fun seekBar(max: Int, progress: Int, onChange: (Int, Boolean) -> Unit) = Slider(context).apply {
        this.max = max
        this.progress = progress
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) onChange(value, false) }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = onChange(bar.progress, true)
        })
    }

    fun valueText(value: String) = text(R.style.Text_RowTitle, value).apply { fontFeatureSettings = "tnum" }

    /** 28px source mark (radius 6) holding a 16px glyph; neutral #333 for things that aren't sources. */
    fun mark(glyph: Int, fill: Int = R.color.mark_neutral, glyphTint: Int = R.color.text_primary, glyphSize: Int = 16) =
        FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_mark)
            backgroundTintList = ColorStateList.valueOf(color(fill))
            addView(ImageView(context).apply {
                setImageResource(glyph)
                imageTintList = ColorStateList.valueOf(color(glyphTint))
            }, FrameLayout.LayoutParams(dp(glyphSize), dp(glyphSize), Gravity.CENTER))
        }

    /**
     * App tile: card anatomy (mark top-left, context top-right, title and line
     * at the bottom). Unavailable = dimmed mark, grey title, not tappable.
     */
    fun tile(
        mark: View,
        context: String,
        title: String,
        line: String,
        available: Boolean = true,
        live: Boolean = available,
        dot: Int? = null,
        onClick: (() -> Unit)?,
    ) = LinearLayout(this.context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        foreground = this.context.getDrawable(R.drawable.press_overlay)
        val top = LinearLayout(this.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            mark.alpha = if (live) 1f else DIMMED
            addView(mark, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(View(this.context), LinearLayout.LayoutParams(0, 1, 1f))
            dot?.let { c ->
                addView(View(this.context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color(c)) }
                }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
            }
            addView(text(R.style.Text_Context, context))
        }
        addView(top)
        addView(View(this.context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(text(R.style.Text_CardTitle, title).apply {
            if (!available) setTextColor(color(R.color.text_secondary))
        })
        addView(text(R.style.Text_Context, line), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        if (available && onClick != null) {
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    /** Stacks cards with the 6px gap. */
    fun column(vararg views: View?) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        views.filterNotNull().forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(6) })
        }
    }

    companion object {
        /** State rule: a source that isn't live shows its mark at 45%. */
        const val DIMMED = 0.45f
    }

    fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun frame(child: View, width: Int, height: Int) = FrameLayout(context).apply {
        addView(child, FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER))
    }
}

/**
 * Centres a start glyph together with the label inside a wide button, as
 * the design draws them (glyph, 10-12px gap, text, all centred).
 */
fun TextView.centreGlyph() {
    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        val t = v as TextView
        val glyph = t.compoundDrawablesRelative[0]?.intrinsicWidth ?: 0
        val content = t.paint.measureText(t.text.toString()) + glyph + (if (glyph > 0) t.compoundDrawablePadding else 0)
        val pad = ((t.width - content) / 2).toInt().coerceAtLeast(0)
        if (t.paddingStart != pad) t.post { t.setPaddingRelative(pad, 0, pad, 0) }
    }
}
