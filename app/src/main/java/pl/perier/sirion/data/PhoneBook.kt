package pl.perier.sirion.data

import android.content.Context
import android.net.Uri

data class Contact(val name: String, val number: String)

data class RecentCall(val name: String, val number: String, val type: String, val time: String)

/**
 * Contacts and call history from the Bluetooth stack's own stores (filled
 * from the phone over PBAP). Favourites don't exist on that side, so we
 * keep our own list.
 */
class PhoneBook(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val favouritesStore get() = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    fun contacts(): List<Contact> {
        if (Demo.on) return Demo.contacts
        val numbers = mutableMapOf<Long, String>()
        resolver.query(DATA, arrayOf("contacts_id", "data1"), null, null, null)?.use { c ->
            while (c.moveToNext()) numbers.putIfAbsent(c.getLong(0), c.getString(1).orEmpty())
        }
        val contacts = mutableListOf<Contact>()
        resolver.query(CONTACTS, arrayOf("_id", "display_name"), null, null, "display_name COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) {
                val number = numbers[c.getLong(0)] ?: continue
                contacts += Contact(c.getString(1).orEmpty(), number)
            }
        }
        return contacts
    }

    fun recents(): List<RecentCall> {
        if (Demo.on) return Demo.recents()
        val calls = mutableListOf<RecentCall>()
        resolver.query(CALLS, arrayOf("display_name", "phone_number", "call_type", "call_time"), null, null, "call_time DESC")?.use { c ->
            while (c.moveToNext() && calls.size < 50) {
                val number = c.getString(1).orEmpty()
                calls += RecentCall(c.getString(0).orEmpty().ifBlank { number }, number, c.getString(2).orEmpty(), c.getString(3).orEmpty())
            }
        }
        return calls
    }

    fun favourites(): List<Contact> =
        favouritesStore.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { entry -> entry.split(SEPARATOR, limit = 2).takeIf { it.size == 2 }?.let { Contact(it[0], it[1]) } }
            .sortedBy { it.name.lowercase() }

    fun isFavourite(contact: Contact) = encode(contact) in favouritesStore.getStringSet(KEY, emptySet()).orEmpty()

    fun toggleFavourite(contact: Contact) {
        val set = favouritesStore.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!set.remove(encode(contact))) set += encode(contact)
        favouritesStore.edit().putStringSet(KEY, set).apply()
    }

    private fun encode(contact: Contact) = contact.name + SEPARATOR + contact.number

    private companion object {
        val CONTACTS: Uri = Uri.parse("content://com.autochips.bluetooth.contacts/bluetooth_contacts_table")
        val DATA: Uri = Uri.parse("content://com.autochips.bluetooth.contactsData/bluetooth_contacts_data_table")
        val CALLS: Uri = Uri.parse("content://com.autochips.bluetooth.calllog/bluetooth_calllog_table")
        const val KEY = "entries"
        const val SEPARATOR = "\u001F"
    }
}
