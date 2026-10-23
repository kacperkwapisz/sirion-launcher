package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.Contact
import pl.perier.sirion.data.PhoneBook
import pl.perier.sirion.logic.PhoneText
import pl.perier.sirion.ui.settings.SettingsActivity
import java.time.LocalDateTime
import kotlin.concurrent.thread

/** Phone · Favourites / Recents / Contacts, on Android's own Bluetooth stack. */
class PhoneActivity : Activity() {
    private lateinit var bar: BottomBar
    private lateinit var book: PhoneBook
    private val calls get() = (application as SirionApp).calls
    private val photos get() = (application as SirionApp).bluetooth.extras
    private var tab = TAB_FAVOURITES

    /** The unit fills its call list a few seconds after a call: show it as it lands. */
    private val callsChanged = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { if (tab == TAB_RECENTS) load() }
    }

    private data class Row(val contact: Contact, val detail: String, val missed: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)
        bar = BottomBar(this, BottomBar.Nav.HOME).also { it.bind() }
        book = PhoneBook(this)
        tab = intent.getStringExtra(EXTRA_TAB) ?: TAB_FAVOURITES

        mapOf(R.id.tab_favourites to TAB_FAVOURITES, R.id.tab_recents to TAB_RECENTS, R.id.tab_contacts to TAB_CONTACTS)
            .forEach { (id, name) -> findViewById<View>(id).setOnClickListener { tab = name; load() } }
        findViewById<TextView>(R.id.phone_keypad).apply {
            centreGlyph()
            setOnClickListener { startActivity(Intent(this@PhoneActivity, KeypadActivity::class.java)) }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        contentResolver.registerContentObserver(PhoneBook.CALLS, true, callsChanged)
        load()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        contentResolver.unregisterContentObserver(callsChanged)
    }

    private fun load() {
        showTabs()
        val phone = BluetoothPhones.connectedName()
        findViewById<TextView>(R.id.phone_context).text = phone ?: getString(R.string.not_connected)
        findViewById<View>(R.id.phone_mark).alpha = if (phone != null) 1f else 0.45f
        showConnected(phone != null)
        if (phone == null) return
        val current = tab
        thread {
            val now = LocalDateTime.now()
            val rows = when (current) {
                TAB_RECENTS -> book.recents().map {
                    Row(Contact(it.name, it.number), PhoneText.recentLine(it.type, PhoneText.parseCallTime(it.time), now), missed = it.type == "missed")
                }
                TAB_CONTACTS -> book.contacts().map { Row(it, it.number) }
                else -> book.favourites().map { Row(it, it.number) }
            }
            // Nothing came over yet (or sharing was allowed later): ask the phone again.
            if (rows.isEmpty() && current != TAB_FAVOURITES) (application as SirionApp).bluetooth.syncContactsSoon()
            runOnUiThread {
                if (tab != current) return@runOnUiThread
                when (current) {
                    TAB_RECENTS -> show(rows, R.string.no_recents, R.string.no_recents_line)
                    TAB_CONTACTS -> show(rows, R.string.no_contacts, R.string.no_contacts_line)
                    else -> show(rows, R.string.no_favourites, R.string.no_favourites_line)
                }
            }
        }
    }

    private fun showTabs() {
        mapOf(R.id.tab_favourites to TAB_FAVOURITES, R.id.tab_recents to TAB_RECENTS, R.id.tab_contacts to TAB_CONTACTS)
            .forEach { (id, name) ->
                val chosen = name == tab
                val view = findViewById<TextView>(id)
                view.setBackgroundResource(if (chosen) R.drawable.bg_segment_chosen else 0)
                view.setTextColor(getColor(if (chosen) R.color.text_on_light else R.color.text_primary))
            }
    }

    /** Not connected: the tabs give way to the two ways to get a phone on. */
    private fun showConnected(connected: Boolean) {
        val tabs = findViewById<View>(R.id.phone_tabs_row)
        val list = findViewById<View>(R.id.phone_list).parent as View
        val off = findViewById<View>(R.id.phone_disconnected)
        tabs.visibility = if (connected) View.VISIBLE else View.GONE
        list.visibility = if (connected) View.VISIBLE else View.GONE
        off.visibility = if (connected) View.GONE else View.VISIBLE
        if (connected) return
        val keeper = (application as SirionApp).bluetooth
        val first = keeper.phones().firstOrNull()
        findViewById<TextView>(R.id.phone_connect_first).apply {
            visibility = if (first != null) View.VISIBLE else View.GONE
            text = "Connect ${first?.name ?: ""}"
            setOnClickListener { first?.let(keeper::connect) }
        }
        findViewById<TextView>(R.id.phone_pair).apply {
            // With no phone paired yet, pairing is the one thing to do, so it becomes the pill.
            val only = first == null
            setBackgroundResource(if (only) R.drawable.bg_pill else R.drawable.bg_pill_dark)
            setTextColor(getColor(if (only) R.color.text_on_light else R.color.text_primary))
            setOnClickListener {
                startActivity(Intent(this@PhoneActivity, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.Category.BLUETOOTH.name)
                    .putExtra(SettingsActivity.EXTRA_PAIR, true))
            }
        }
    }

    private fun show(rows: List<Row>, emptyTitle: Int, emptyLine: Int) {
        val list = findViewById<ListView>(R.id.phone_list)
        val empty = findViewById<View>(R.id.phone_empty)
        list.adapter = RowAdapter(rows)
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.phone_empty_title).setText(emptyTitle)
        findViewById<TextView>(R.id.phone_empty_line).setText(emptyLine)
        findViewById<ImageView>(R.id.phone_empty_glyph).setImageResource(when (tab) {
            TAB_RECENTS -> R.drawable.g_clock
            TAB_CONTACTS -> R.drawable.g_person
            else -> R.drawable.g_star
        })
    }

    private fun call(contact: Contact) {
        if (!calls.dial(contact.number)) Toast.makeText(this, R.string.no_phone, Toast.LENGTH_SHORT).show()
    }

    private inner class RowAdapter(private val rows: List<Row>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_contact, parent, false)
            val row = rows[position]
            view.findViewById<TextView>(R.id.contact_initials).showAvatar(row.contact.name, photos.photo(row.contact.number))
            view.findViewById<TextView>(R.id.contact_name).apply {
                showName(row.contact.name)
                setTextColor(getColor(if (row.missed) R.color.danger else R.color.text_primary))
            }
            view.findViewById<TextView>(R.id.contact_detail).text = row.detail
            view.findViewById<View>(R.id.contact_call).setOnClickListener { call(row.contact) }
            view.setOnClickListener { call(row.contact) }
            view.setOnLongClickListener {
                if (book.favouritesFromPhone) {
                    Toast.makeText(this@PhoneActivity, R.string.favourites_on_phone, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                val adding = !book.isFavourite(row.contact)
                book.toggleFavourite(row.contact)
                Toast.makeText(this@PhoneActivity, if (adding) R.string.favourite_added else R.string.favourite_removed, Toast.LENGTH_SHORT).show()
                if (tab == TAB_FAVOURITES) load()
                true
            }
            return view
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_FAVOURITES = "favourites"
        const val TAB_RECENTS = "recents"
        const val TAB_CONTACTS = "contacts"
    }
}
