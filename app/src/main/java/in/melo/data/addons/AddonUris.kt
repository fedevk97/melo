package `in`.melo.data.addons

import android.net.Uri

/**
 * How a row picked from an add-on is known: `addon:<addonId>:<trackId>` in the
 * queue and the library, `addon://<addonId>/<trackId>` as what the player opens.
 * The add-on's own id is kept whole, `:` and `/` included.
 */
object AddonUris {
    const val SCHEME = "addon"
    const val ID_PREFIX = "addon:"

    fun trackId(addonId: String, trackId: String): String = "$ID_PREFIX$addonId:$trackId"

    fun isAddonTrack(id: String): Boolean = id.startsWith(ID_PREFIX)

    /** The add-on and the add-on's own track id behind a queue id. */
    fun split(id: String): Pair<String, String>? {
        if (!isAddonTrack(id)) return null
        val rest = id.removePrefix(ID_PREFIX)
        val addonId = rest.substringBefore(':', "").ifEmpty { return null }
        val track = rest.substringAfter(':', "").ifEmpty { return null }
        return addonId to track
    }

    fun uriOf(id: String): Uri? {
        val (addonId, track) = split(id) ?: return null
        return Uri.Builder().scheme(SCHEME).authority(addonId).appendPath(track).build()
    }

    fun parse(uri: Uri?): Pair<String, String>? {
        if (uri?.scheme != SCHEME) return null
        val addonId = uri.authority?.ifEmpty { null } ?: return null
        val track = uri.lastPathSegment?.ifEmpty { null } ?: return null
        return addonId to track
    }
}
