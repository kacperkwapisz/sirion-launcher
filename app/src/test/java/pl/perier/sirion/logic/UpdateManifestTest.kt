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
}
