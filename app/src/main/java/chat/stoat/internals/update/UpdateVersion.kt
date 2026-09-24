package chat.stoat.internals.update

/**
 * Version of a fork release, parsed from tags like `v1.7.2-selfhosted.2` or version names
 * like `1.7.2-selfhosted.2`. A plain upstream version (`1.7.2`) counts as fork revision 0.
 */
data class UpdateVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val revision: Int,
) : Comparable<UpdateVersion> {
    override fun compareTo(other: UpdateVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.revision })

    companion object {
        private val VersionRegex =
            Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-selfhosted\\.(\\d+))?(?:\\+.*)?$")

        fun parse(value: String): UpdateVersion? {
            val match = VersionRegex.matchEntire(value.trim()) ?: return null
            val (major, minor, patch, revision) = match.destructured
            return UpdateVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                revision = revision.toIntOrNull() ?: 0,
            )
        }

        /** True when [remote] parses and is strictly newer than [current]. */
        fun isNewer(remote: String, current: String): Boolean {
            val remoteVersion = parse(remote) ?: return false
            val currentVersion = parse(current) ?: return false
            return remoteVersion > currentVersion
        }
    }
}
