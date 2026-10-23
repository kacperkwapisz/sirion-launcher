package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val json = """
        {
          "versionCode": 7,
          "versionName": "0.3",
          "apk": "https://github.com/kacper/sirion-launcher/releases/download/v0.3/sirion-launcher-7.apk",
          "sha256": "916c0708beb1022d4ea9678ff6a46dfb6d234f707837a2cbd80d615e8e2bb2e3"
        }
    """.trimIndent()

    @Test
    fun readsTheReleaseManifest() {
        val manifest = UpdateManifest.parse(json)!!
        assertEquals(7, manifest.versionCode)
        assertEquals("0.3", manifest.versionName)
        assertEquals("https://github.com/kacper/sirion-launcher/releases/download/v0.3/sirion-launcher-7.apk", manifest.apk)
        assertEquals("916c0708beb1022d4ea9678ff6a46dfb6d234f707837a2cbd80d615e8e2bb2e3", manifest.sha256)
    }

    @Test
    fun onlyNewerVersionsCount() {
        val manifest = UpdateManifest.parse(json)!!
        assertTrue(manifest.isNewerThan(6))
        assertFalse(manifest.isNewerThan(7))
        assertFalse(manifest.isNewerThan(8))
    }

    @Test
    fun rejectsManifestsThatCouldInstallSomethingElse() {
        // Plain http, other hosts and missing hashes are all refused.
        assertNull(UpdateManifest.parse(json.replace("https://github.com", "http://github.com")))
        assertNull(UpdateManifest.parse(json.replace("https://github.com", "https://evil.example")))
        assertNull(UpdateManifest.parse(json.replace("916c0708", "")))
        assertNull(UpdateManifest.parse("""{"versionCode": 7}"""))
        assertNull(UpdateManifest.parse("not json"))
    }

    @Test
    fun hashCheckIgnoresCase() {
        val manifest = UpdateManifest.parse(json)!!
        assertTrue(manifest.matches("916C0708BEB1022D4EA9678FF6A46DFB6D234F707837A2CBD80D615E8E2BB2E3"))
        assertFalse(manifest.matches("0000"))
    }

    @Test
    fun readsWhatChangedInOrder() {
        val withNotes = json.replace("\n}", ",\n  \"notes\": [\"Contact photos from your iPhone\", \"Says \\\"hi\\\" back\"]\n}")
        assertEquals(listOf("Contact photos from your iPhone", "Says \"hi\" back"), UpdateManifest.parse(withNotes)!!.notes)
    }

    @Test
    fun aManifestWithoutNotesStillWorks() {
        assertEquals(emptyList<String>(), UpdateManifest.parse(json)!!.notes)
    }

    @Test
    fun theManifestSurvivesBeingSavedAndReadBack() {
        val manifest = UpdateManifest.parse(json)!!.copy(notes = listOf("One", "Two \"quoted\""))
        assertEquals(manifest, UpdateManifest.parse(manifest.toJson()))
    }
}

class UpdateManifestReleaseFormatTest {
    private val json = """
        {
          "versionCode": 4,
          "versionName": "0.4",
          "apk": "https://github.com/kacperkwapisz/sirion-launcher/releases/download/v0.4/sirion-launcher-4.apk",
          "sha256": "916c0708beb1022d4ea9678ff6a46dfb6d234f707837a2cbd80d615e8e2bb2e3",
          "notes": ["Back\\slash and \"quotes\" ż"],
          "earlier": [
            {"versionCode": 3, "versionName": "0.3", "notes": ["Contact photos from your iPhone"]},
            {"versionCode": 2, "versionName": "0.2", "notes": ["Over-the-air updates"]}
          ]
        }
    """.trimIndent()

    @Test
    fun readsWhatTheReleaseScriptWrites() {
        assertEquals(listOf("Back\\slash and \"quotes\" ż"), UpdateManifest.parse(json)?.notes)
    }

    @Test
    fun skippedVersionsShowTheirNotesToo() {
        // Installed 0.2, the phone missed 0.3: both 0.4 and 0.3 are new, newest first.
        val changes = UpdateManifest.parse(json)!!.changesSince(installed = 2)
        assertEquals(listOf("0.4", "0.3"), changes.map { it.versionName })
        assertEquals(listOf("Contact photos from your iPhone"), changes[1].notes)
    }

    @Test
    fun onlyTheNewestWhenNothingWasSkipped() {
        assertEquals(listOf("0.4"), UpdateManifest.parse(json)!!.changesSince(installed = 3).map { it.versionName })
    }

    @Test
    fun earlierReleasesSurviveBeingSaved() {
        val manifest = UpdateManifest.parse(json)!!
        assertEquals(manifest, UpdateManifest.parse(manifest.toJson()))
    }
}
