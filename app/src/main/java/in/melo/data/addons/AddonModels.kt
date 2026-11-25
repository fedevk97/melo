package `in`.melo.data.addons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * The wire shapes of the add-on protocol. An add-on is an ordinary HTTP server:
 * it answers `/manifest.json`, `/search?q=` and `/stream/{id}` with JSON, and
 * nothing it sends is ever run on the phone.
 *
 * Only the fields Melo reads are declared. The parser ignores unknown keys, so
 * an add-on publishing more than this still parses.
 */

/** What an add-on says it is and what it can do. */
@Serializable
data class AddonManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("resources") val resources: List<String> = emptyList(),
    /** The settings schema. Its defaults travel with every request; `quality` gets a picker. */
    @SerialName("settings") val settings: List<AddonSetting> = emptyList(),
    /** `0` keeps the add-on to playback: downloads skip it. Absent means allowed. */
    @SerialName("allowDownloads") val allowDownloads: JsonElement? = null,
    /** The add-on is only to be used while a lossless-capable output is connected. */
    @SerialName("checkValidLossless") val checkValidLossless: JsonElement? = null,
    /** The add-on only serves one particular app, which Melo cannot vouch to be. */
    @SerialName("bitchordAuth") val appAttestation: JsonElement? = null,
) {
    val downloadsAllowed: Boolean get() = allowDownloads.asFlag() ?: true
    val requiresLosslessOutput: Boolean get() = checkValidLossless.asFlag() ?: false
    val requiresAttestation: Boolean get() = appAttestation.asFlag() ?: false

    /** The renditions offered by a declared `quality` select; empty when there is no real choice. */
    val qualityOptions: List<AddonQualityOption>
        get() = settings.firstOrNull { it.key == AddonClient.QUALITY_KEY }?.options.orEmpty()
            .mapNotNull { option ->
                val value = option.stringValue ?: return@mapNotNull null
                AddonQualityOption(value, option.labelText ?: value)
            }
            .distinctBy { it.value }
            .takeIf { it.size > 1 }
            .orEmpty()

    fun declares(resource: String): Boolean = resources.any { it.equals(resource, ignoreCase = true) }

    /**
     * Everything Melo asks starts with a search, so `search` is the one resource
     * required. Declaring nothing is silence rather than a claim, and is settled
     * by asking the endpoints.
     */
    val isPlayable: Boolean get() = resources.isEmpty() || declares("search")

    val displayName: String get() = name.ifBlank { id }
}

/** One declared setting: its default is sent, and a `quality` setting's options are offered. */
@Serializable
data class AddonSetting(
    @SerialName("key") val key: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<AddonSettingOption> = emptyList(),
) {
    val defaultValue: String? get() = default?.asQueryValue()
}

@Serializable
data class AddonSettingOption(
    @SerialName("value") val value: JsonElement? = null,
    @SerialName("label") val label: JsonElement? = null,
) {
    val stringValue: String? get() = value?.asQueryValue()
    val labelText: String? get() = label?.asQueryValue()
}

/** One entry of an add-on's quality picker, as stored with the add-on. */
@Serializable
data class AddonQualityOption(val value: String, val label: String)

/** A search answer. Every array is optional; only tracks are read. */
@Serializable
data class AddonSearchResponse(@SerialName("tracks") val tracks: List<AddonTrack> = emptyList())

@Serializable
data class AddonTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    /** Seconds; a double so `240.0` parses. */
    @SerialName("duration") val duration: Double? = null,
    @SerialName("artworkURL") val artworkURL: String? = null,
    @SerialName("albumArtworkURL") val albumArtworkURL: String? = null,
    @SerialName("format") val format: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    @SerialName("atmos") val atmos: Boolean? = null,
    /** A URL for the row itself, tried when `/stream` has nothing. */
    @SerialName("streamURL") val streamURL: String? = null,
) {
    val durationSec: Int? get() = duration?.takeIf { it > 0 }?.toInt()
    val artwork: String? get() = artworkURL?.ifBlank { null } ?: albumArtworkURL?.ifBlank { null }

    /** A catalogue may publish the immersive mix as a track of its own, told apart only here. */
    val isDolbyAtmos: Boolean
        get() = atmos == true ||
            ATMOS_HINT.containsMatchIn("$audioQuality ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} $format")

    val isHiRes: Boolean get() = HI_RES_HINT.containsMatchIn("$audioQuality $format")
    val isLossless: Boolean get() = isHiRes || LOSSLESS_HINT.containsMatchIn("$audioQuality $format")
}

/** What is on the end of a `/stream/{id}` call. Every routing field is optional. */
@Serializable
data class AddonStream(
    @SerialName("url") val url: String = "",
    @SerialName("format") val format: String = "",
    @SerialName("quality") val quality: String = "",
    @SerialName("streamQuality") val streamQuality: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("codec") val codec: String? = null,
    @SerialName("fileCodec") val fileCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("containerFormat") val containerFormat: String? = null,
    /** `none`, `hls` or `dash`: what the URL points at. */
    @SerialName("manifest") val manifest: String? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    /** `false`, or the name of a DRM scheme. Anything but false is refused. */
    @SerialName("encrypted") val encrypted: JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    @SerialName("bitrate") val bitrate: Double? = null,
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    /** Why there is no URL, when the add-on says (some answer 200 with an error). */
    @SerialName("error") val error: String? = null,
) {
    val statedCodec: String? get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase()
    val statedContainer: String? get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })?.lowercase()
    val qualityText: String get() = "$quality $streamQuality $audioQuality $format"

    /** Melo never asks for a protected rendition, so an encrypted answer is declined. */
    val isEncrypted: Boolean
        get() {
            val element = encrypted as? JsonPrimitive ?: return false
            if (element is JsonNull) return false
            element.booleanOrNull?.let { return it }
            val text = element.content.trim()
            return text.isNotEmpty() && !text.equals("false", true) && !text.equals("none", true) && text != "0"
        }

    /**
     * The declared transport, or null for a plain file. `format` is consulted last,
     * and only the words that can only mean a transport count: a live add-on sends a
     * Tidal DASH manifest as `"format": "dash"` with no other hint.
     */
    val transport: StreamTransport
        get() {
            val stated = manifest?.ifBlank { null } ?: mediaType?.ifBlank { null } ?: format.ifBlank { null }
            return when (stated?.lowercase()) {
                "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> StreamTransport.Hls
                "dash", "mpd", "application/dash+xml" -> StreamTransport.Dash
                else -> when {
                    url.substringBefore('?').endsWith(".mpd", true) -> StreamTransport.Dash
                    url.substringBefore('?').endsWith(".m3u8", true) -> StreamTransport.Hls
                    else -> StreamTransport.File
                }
            }
        }

    /** From the field, else from the label (`96 kHz`); some publishers give kHz in the field. */
    val sampleRateHz: Int?
        get() = sampleRate?.takeIf { it > 0 }?.let { if (it < 1000) (it * 1000).toInt() else it.toInt() }
            ?: KHZ_LABEL.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toInt() }

    val bits: Int?
        get() = bitDepth?.takeIf { it > 0 }?.toInt()
            ?: BIT_DEPTH_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 8..32 }

    /** Kbit/s whichever unit was used: over 3000 can only be bit/s. */
    val kbps: Int?
        get() = bitrate?.takeIf { it > 0 }?.let { if (it > 3_000) (it / 1000).toInt() else it.toInt() }
            ?: KBPS_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()

    val isDolbyAtmos: Boolean
        get() = ATMOS_HINT.containsMatchIn("$qualityText ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} ${statedCodec.orEmpty()}")

    internal companion object {
        val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
        val KHZ_LABEL = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
        val BIT_DEPTH_LABEL = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
    }
}

enum class StreamTransport { File, Dash, Hls }

private val HI_RES_HINT = Regex("""hi[-_ ]?res|24[-_ ]?bit""", RegexOption.IGNORE_CASE)
private val LOSSLESS_HINT = Regex("""lossless|flac|alac|wav|aiff|16[-_ ]?bit""", RegexOption.IGNORE_CASE)
private val ATMOS_HINT = Regex("""atmos|dolby|eac3[_-]?joc|ec-?3""", RegexOption.IGNORE_CASE)

/** A manifest switch however it was written (`1`, `true`, `"on"`); null for absent or unreadable. */
internal fun JsonElement?.asFlag(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    primitive.booleanOrNull?.let { return it }
    return when (primitive.content.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> primitive.content.toDoubleOrNull()?.let { it != 0.0 }
    }
}

/** A JSON scalar as a query value: `320` stays `320`, and null or composite values are dropped. */
private fun JsonElement.asQueryValue(): String? {
    if (this is JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.ifBlank { null }
}
