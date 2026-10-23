package pl.perier.sirion.data

import android.content.Context
import android.net.Uri

data class Contact(val name: String, val number: String)

data class RecentCall(val name: String, val number: String, val type: String, val time: String)

/**
 * Contacts and call history from the Bluetooth stack's own stores (filled
 * from the phone over PBAP). Favourites come from the phone when it shares
 * them (PhoneExtras saves them here); otherwise we keep our own list.
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

    /** The phone's favourites in the phone's order, or the ones kept in the car. */
    fun favourites(): List<Contact> = phoneFavourites().ifEmpty {
        favouritesStore.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.name.lowercase() }
    }

    /** True when the Favourites tab shows the phone's list (it can't be changed in the car). */
    val favouritesFromPhone get() = phoneFavourites().isNotEmpty()

    /** Null: the phone doesn't share favourites, so the car's own list is used. */
    fun savePhoneFavourites(contacts: List<Contact>?) {
        favouritesStore.edit().apply {
            if (contacts == null) remove(KEY_PHONE) else putString(KEY_PHONE, contacts.joinToString("\n", transform = ::encode))
        }.apply()
    }

    private fun phoneFavourites(): List<Contact> =
        favouritesStore.getString(KEY_PHONE, null).orEmpty().split("\n").mapNotNull(::decode)

    private fun decode(entry: String) = entry.split(SEPARATOR, limit = 2).takeIf { it.size == 2 }?.let { Contact(it[0], it[1]) }

    fun isFavourite(contact: Contact) = encode(contact) in favouritesStore.getStringSet(KEY, emptySet()).orEmpty()

    fun toggleFavourite(contact: Contact) {
        val set = favouritesStore.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!set.remove(encode(contact))) set += encode(contact)
        favouritesStore.edit().putStringSet(KEY, set).apply()
    }

    private fun encode(contact: Contact) = contact.name + SEPARATOR + contact.number

    companion object {
        private val CONTACTS: Uri = Uri.parse("content://com.autochips.bluetooth.contacts/bluetooth_contacts_table")
        private val DATA: Uri = Uri.parse("content://com.autochips.bluetooth.contactsData/bluetooth_contacts_data_table")
        val CALLS: Uri = Uri.parse("content://com.autochips.bluetooth.calllog/bluetooth_calllog_table")
        private const val KEY = "entries"
        private const val KEY_PHONE = "phone"
        private const val SEPARATOR = "\u001F"
    }
}
