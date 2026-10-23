package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.CarPlayEngine

/**
 * Every launchable app on the unit. Reached from Unit apps; with the stock
 * launcher off it's the only way to open things like Chrome or YouTube.
 */
class AllAppsActivity : Activity() {
    private lateinit var bar: BottomBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }
        findViewById<GridView>(R.id.apps_grid).adapter = AppsAdapter(launchableApps())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
    }

    private fun launchableApps(): List<ResolveInfo> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

    private inner class AppsAdapter(private val apps: List<ResolveInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.app_tile, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.loadIcon(packageManager))
            view.findViewById<TextView>(R.id.app_label).text = app.loadLabel(packageManager)
            view.setOnClickListener {
                val info = app.activityInfo
                // Opening the CarPlay engine here is the driver asking for it too.
                if (info.packageName in CarPlayEngine.engines) (application as SirionApp).carPlayFocus.driverOpened()
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(info.packageName, info.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            return view
        }
    }
}
