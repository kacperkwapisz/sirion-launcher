package pl.perier.sirion.logic

/**
 * `update.json`, published with every GitHub release. Only https URLs on
 * GitHub's release hosts are accepted, and the APK must match [sha256].
 */
data class UpdateManifest(val versionCode: Int, val versionName: String, val apk: String, val sha256: String) {
    fun isNewerThan(installed: Int) = versionCode > installed

    fun matches(hash: String) = sha256.equals(hash, ignoreCase = true)

    companion object {
        private val allowedHosts = listOf("https://github.com/", "https://objects.githubusercontent.com/")
        private val hex64 = Regex("^[0-9a-fA-F]{64}$")

        fun parse(json: String): UpdateManifest? {
            val code = number(json, "versionCode") ?: return null
            val name = text(json, "versionName") ?: return null
            val apk = text(json, "apk")?.takeIf { url -> allowedHosts.any(url::startsWith) } ?: return null
            val sha = text(json, "sha256")?.takeIf(hex64::matches) ?: return null
            return UpdateManifest(code, name, apk, sha)
        }

        private fun text(json: String, key: String) =
            Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

        private fun number(json: String, key: String) =
            Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}
