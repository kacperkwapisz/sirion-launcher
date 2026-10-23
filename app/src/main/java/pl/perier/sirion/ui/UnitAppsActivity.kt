package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.perier.sirion.R

/**
 * Unit apps: the vendor's own apps, kept for rare jobs. Only the ones still
 * enabled show, since we turn most of them off.
 */
class UnitAppsActivity : Activity() {
    private data class UnitApp(val pkg: String?, val title: String, val line: String)

    private lateinit var kit: Kit
    private lateinit var bar: BottomBar

    private val apps = listOf(
        UnitApp("com.xyauto.Settings", "Factory settings", "Installer menus. Change only what you know"),
        UnitApp("com.autochips.bluetooth", "Original Bluetooth", "The unit's phone app, if ours can't connect"),
        UnitApp("com.xy.tda7729", "Equaliser", "Built-in sound tuning"),
        UnitApp("com.mediatek.filemanager", "File manager", "USB stick and storage"),
        UnitApp("com.android.settings", "Android settings", "Everything else"),
        UnitApp(null, "All apps", "Everything installed on the unit"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tiles)
        kit = Kit(this)
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
    }

    private fun render() {
        val rows = apps.mapNotNull { unit ->
            val open = if (unit.pkg == null) Intent(this, AllAppsActivity::class.java)
            else packageManager.getLaunchIntentForPackage(unit.pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return@mapNotNull null
            kit.row(unit.title, unit.line, height = 76, trailing = kit.glyph(R.drawable.g_chevron, R.color.text_secondary)) {
                startActivity(open)
            }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(kit.dp(20), kit.dp(20), kit.dp(20), kit.dp(4))
            addView(kit.mark(R.drawable.g_apps), LinearLayout.LayoutParams(kit.dp(28), kit.dp(28)))
            addView(android.view.View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(kit.text(R.style.Text_Context, "The unit's own apps, kept for rare jobs"))
        }
        val card = kit.card(*rows.toTypedArray()).apply { addView(top, 0) }
        findViewById<FrameLayout>(R.id.tiles_content).apply {
            removeAllViews()
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(card)
            })
        }
    }
}
