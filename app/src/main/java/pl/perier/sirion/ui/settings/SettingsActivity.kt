package pl.perier.sirion.ui.settings

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.ui.BottomBar
import pl.perier.sirion.ui.Kit
import pl.perier.sirion.ui.hideSystemBars

/**
 * Settings shell: category list on the left (active = accent text, card fill,
 * 4px accent stripe), the category's cards on the right. Detail screens
 * (Connect first, Pair a new phone, Join network, Balance and fader) open in
 * the right side with a back header.
 */
class SettingsActivity : Activity() {
    enum class Category(val label: String, val glyph: Int) {
        BLUETOOTH("Bluetooth", R.drawable.g_bluetooth),
        WIFI("Wi-Fi", R.drawable.g_wifi),
        SOUND("Sound", R.drawable.g_speaker),
        DISPLAY("Display", R.drawable.g_sun),
        SYSTEM("System", R.drawable.g_sliders),
    }

    val app get() = application as SirionApp
    lateinit var kit: Kit
        private set
    private lateinit var bar: BottomBar
    private val main = Handler(Looper.getMainLooper())
    private var category = Category.BLUETOOTH
    private val details = ArrayDeque<() -> View>()

    private lateinit var bluetooth: BluetoothSettings
    private lateinit var wifi: WifiSettings
    private lateinit var sound: SoundSettings
    private lateinit var display: DisplaySettings
    private lateinit var system: SystemSettings

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val relevant = when (category) {
                Category.BLUETOOTH -> intent.action?.startsWith("android.bluetooth") == true
                Category.WIFI -> intent.action?.startsWith("android.net.wifi") == true
                else -> false
            }
            if (relevant) refreshSoon()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        kit = Kit(this)
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }
        bluetooth = BluetoothSettings(this)
        wifi = WifiSettings(this)
        sound = SoundSettings(this)
        display = DisplaySettings(this)
        system = SystemSettings(this)
        category = intent.getStringExtra(EXTRA_CATEGORY)?.let { name -> Category.entries.firstOrNull { it.name == name } } ?: Category.BLUETOOTH
        buildCategories()
        render()
        if (intent.getBooleanExtra(EXTRA_PAIR, false)) bluetooth.openPairing()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CATEGORY)?.let { name -> Category.entries.firstOrNull { it.name == name } }?.let(::show)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        registerReceiver(events, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        })
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        unregisterReceiver(events)
        main.removeCallbacksAndMessages(null)
        bluetooth.stopPairing()
    }

    @Deprecated("Back from a detail screen goes to its category first.")
    override fun onBackPressed() {
        if (details.isNotEmpty()) back() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    fun show(next: Category) {
        if (next != category) bluetooth.stopPairing()
        category = next
        details.clear()
        buildCategories()
        render(fade = true)
        findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 0)
    }

    /** Opens a detail screen; [build] runs again on every refresh. */
    fun open(build: () -> View) {
        details.addLast(build)
        render(fade = true)
    }

    fun back() {
        details.removeLastOrNull()
        bluetooth.stopPairing()
        render(fade = true)
    }

    private var rendering = false

    /**
     * Rebuilds the visible screen from current state. Removing the old views
     * sends a cancel to whatever was being touched, and its handler may ask
     * for another rebuild; that one waits until this one is done.
     */
    fun render(fade: Boolean = false) {
        if (rendering) {
            main.post(renderTask)
            return
        }
        rendering = true
        try {
            build()
        } finally {
            rendering = false
        }
        // A new screen fades in; refreshes of the same screen don't, so nothing flickers.
        if (fade) findViewById<View>(R.id.settings_content).apply {
            alpha = 0f
            animate().alpha(1f).setDuration(FADE_MS).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun build() {
        val content = findViewById<FrameLayout>(R.id.settings_content)
        content.removeAllViews()
        val build = details.lastOrNull() ?: when (category) {
            Category.BLUETOOTH -> bluetooth::screen
            Category.WIFI -> wifi::screen
            Category.SOUND -> sound::screen
            Category.DISPLAY -> display::screen
            Category.SYSTEM -> system::screen
        }
        content.addView(build())
    }

    private val renderTask = Runnable { render() }

    fun refreshSoon() {
        main.removeCallbacks(renderTask)
        main.postDelayed(renderTask, 300)
    }

    private fun buildCategories() {
        val list = findViewById<LinearLayout>(R.id.settings_categories)
        list.removeAllViews()
        Category.entries.forEach { c ->
            val active = c == category
            val tint = getColor(if (active) R.color.accent else R.color.text_primary)
            val item = FrameLayout(this).apply {
                setBackgroundResource(R.drawable.bg_category)
                isSelected = active
                isClickable = true
                setOnClickListener { show(c) }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(kit.dp(16), 0, kit.dp(16), 0)
                addView(ImageView(context).apply {
                    setImageResource(c.glyph)
                    imageTintList = ColorStateList.valueOf(tint)
                }, LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)))
                addView(kit.text(R.style.Text_RowTitle, c.label).apply { setTextColor(tint) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(14) })
            }
            item.addView(inner, FrameLayout.LayoutParams(-1, -1))
            if (active) item.addView(View(this).apply { setBackgroundResource(R.drawable.bg_stripe) },
                FrameLayout.LayoutParams(kit.dp(4), kit.dp(32), Gravity.START or Gravity.CENTER_VERTICAL))
            list.addView(item, LinearLayout.LayoutParams(-1, kit.dp(64)).apply { if (list.childCount > 0) topMargin = kit.dp(4) })
        }
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        private const val FADE_MS = 120L
        /** Opens Bluetooth straight on "Pair a new phone". */
        const val EXTRA_PAIR = "pair"
    }
}
