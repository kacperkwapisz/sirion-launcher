package pl.perier.sirion.logic

/**
 * `update.json`, published with every GitHub release. Only https URLs on
 * GitHub's release hosts are accepted, and the APK must match [sha256].
 * [notes] are this version's "What changed" lines; [earlier] carries the
 * releases before it, so a unit that skipped some still shows their notes.
 * Each APK is the whole app, so the newest one installs over any older one.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val notes: List<String> = emptyList(),
    val earlier: List<Release> = emptyList(),
) {
    data class Release(val versionCode: Int, val versionName: String, val notes: List<String>)

    fun isNewerThan(installed: Int) = versionCode > installed

    fun matches(hash: String) = sha256.equals(hash, ignoreCase = true)

    /** Every release newer than [installed], newest first. */
    fun changesSince(installed: Int): List<Release> =
        (listOf(Release(versionCode, versionName, notes)) + earlier)
            .filter { it.versionCode > installed }
            .distinctBy { it.versionCode }
            .sortedByDescending { it.versionCode }

    /** Kept on the unit with the downloaded APK until it's installed. */
    fun toJson() = Json.write(mapOf(
        "versionCode" to versionCode,
        "versionName" to versionName,
        "apk" to apk,
        "sha256" to sha256,
        "notes" to notes,
        "earlier" to earlier.map { mapOf("versionCode" to it.versionCode, "versionName" to it.versionName, "notes" to it.notes) },
    ))

    companion object {
        private val allowedHosts = listOf("https://github.com/", "https://objects.githubusercontent.com/")
        private val hex64 = Regex("^[0-9a-fA-F]{64}$")

        fun parse(json: String): UpdateManifest? {
            val root = Json.parse(json) as? Map<*, *> ?: return null
            val code = (root["versionCode"] as? Long)?.toInt() ?: return null
            val name = root["versionName"] as? String ?: return null
            val apk = (root["apk"] as? String)?.takeIf { url -> allowedHosts.any(url::startsWith) } ?: return null
            val sha = (root["sha256"] as? String)?.takeIf(hex64::matches) ?: return null
            val earlier = (root["earlier"] as? List<*>).orEmpty().mapNotNull { entry ->
                val release = entry as? Map<*, *> ?: return@mapNotNull null
                val releaseCode = (release["versionCode"] as? Long)?.toInt() ?: return@mapNotNull null
                Release(releaseCode, release["versionName"] as? String ?: return@mapNotNull null, strings(release["notes"]))
            }
            return UpdateManifest(code, name, apk, sha, strings(root["notes"]), earlier)
        }

        private fun strings(value: Any?) = (value as? List<*>).orEmpty().filterIsInstance<String>()
    }
}
