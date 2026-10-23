package pl.perier.sirion.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.logic.PhoneText

/**
 * Shows a contact name without characters no font on the unit can draw
 * (they'd show as boxes or "?"). Emoji the unit can draw stay.
 */
fun TextView.showName(name: String) {
    val sb = StringBuilder()
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        val type = Character.getType(cp)
        // Joiners and variation selectors are invisible parts of emoji; keep them with their emoji.
        val joiner = type == Character.FORMAT.toInt() || type == Character.NON_SPACING_MARK.toInt() ||
            cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF
        val text = String(Character.toChars(cp))
        if (joiner || Character.isWhitespace(cp) || paint.hasGlyph(text)) sb.append(text)
        i += Character.charCount(cp)
    }
    text = sb.toString().trim().replace(Regex("\\s{2,}"), " ")
}

/** Initials in the avatar circle, or a person glyph when the name has no letters (say, only an emoji). */
fun TextView.showInitials(name: String) {
    val initials = PhoneText.initials(name)
    text = initials
    if (initials.isEmpty()) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.g_person, 0, 0, 0)
        compoundDrawableTintList = ColorStateList.valueOf(context.getColor(R.color.text_secondary))
    } else {
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
    }
}

/** The contact's photo when the phone has one, otherwise their initials. */
fun TextView.showAvatar(name: String, photo: Bitmap?) {
    if (photo != null) {
        text = ""
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        background = BitmapDrawable(resources, photo)
        backgroundTintList = null
    } else {
        setBackgroundResource(R.drawable.bg_dot)
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.surface_track))
        showInitials(name)
    }
}
