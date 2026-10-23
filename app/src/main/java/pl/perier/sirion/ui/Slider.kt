package pl.perier.sirion.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import pl.perier.sirion.R

/**
 * The one slider (Paper library: Slider): 8px #333 track, accent fill, 32px
 * white knob with a ring while dragging. Every slider in the launcher is
 * this view, so changing @style/Slider, slider_track or slider_thumb changes
 * them all. [CentredSlider] draws the same knob for values that go both ways.
 */
class Slider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SeekBar(context, attrs, 0, R.style.Slider)
