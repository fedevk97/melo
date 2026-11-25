package `in`.melo.data.addons

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/**
 * One add-on the listener has added. [url] can carry a personal token, so it is
 * kept on the device only and never logged in full.
 */
@Serializable
data class AddonConfig(
    val id: String,
    val url: String,
    val name: String,
    val version: String = "",
    val enabled: Boolean = true,
    /** The `quality` value picked for this add-on, or null for its first option. */
    val pickedQuality: String? = null,
    val qualityOptions: List<AddonQualityOption> = emptyList(),
    val allowDownloads: Boolean = true,
    /** Only to be used while a lossless-capable output is connected. */
    val needsLosslessOutput: Boolean = false,
)

/** The listener's add-ons, in their order of preference, and a client for each. */
class AddonStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("melo_addons", Context.MODE_PRIVATE)
    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<AddonConfig>> = _addons.asStateFlow()

    private val clients = HashMap<String, AddonClient>()

    /** The enabled add-ons, best first. */
    fun enabled(): List<AddonConfig> = addons.value.filter { it.enabled }

    fun find(id: String): AddonConfig? = addons.value.firstOrNull { it.id == id }

    /** A client for [config], rebuilt when its address or quality pick changes. */
    fun client(config: AddonConfig): AddonClient = synchronized(clients) {
        val existing = clients[config.id]
        if (existing != null && existing.baseUrl == AddonClient.normalizeBase(config.url) && existing.picked == config.pickedQuality) {
            existing
        } else {
            AddonClient(config.url, config.pickedQuality).also { clients[config.id] = it }
        }
    }

    /** Whether an add-on with this address is already in the list. */
    fun hasUrl(url: String): Boolean {
        val wanted = AddonClient.normalizeBase(url)
        return addons.value.any { AddonClient.normalizeBase(it.url).equals(wanted, ignoreCase = true) }
    }

    /**
     * What the add-on at [url] says it is. One that never published a manifest is
     * still taken when its search answers, named after its host.
     */
    suspend fun identify(url: String): Result<AddonManifest> {
        val base = AddonClient.normalizeBase(url)
        val host = base.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host
            ?: return Result.failure(AddonException(AddonProblem.BadAddress))
        val client = AddonClient(base)
        return client.manifest().recoverCatching { failure ->
            if ((failure as? AddonException)?.problem != AddonProblem.NoManifest) throw failure
            client.probeSearch().getOrElse { throw failure }
            AddonManifest(id = host, name = host, resources = listOf("search", "stream"))
        }.mapCatching { manifest ->
            if (manifest.requiresAttestation) throw AddonException(AddonProblem.NeedsAttestation)
            manifest
        }
    }

    fun add(url: String, manifest: AddonManifest): AddonConfig {
        val config = AddonConfig(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            url = AddonClient.normalizeBase(url),
            name = manifest.displayName,
            version = manifest.version,
            qualityOptions = manifest.qualityOptions,
            allowDownloads = manifest.downloadsAllowed,
            needsLosslessOutput = manifest.requiresLosslessOutput,
        )
        save { it + config }
        return config
    }

    /** Takes in what a fresh manifest says, keeping the listener's own choices. */
    fun refresh(id: String, manifest: AddonManifest) = change(id) { config ->
        val options = manifest.qualityOptions
        config.copy(
            name = manifest.displayName,
            version = manifest.version,
            qualityOptions = options,
            pickedQuality = config.pickedQuality?.takeIf { picked -> options.any { it.value == picked } },
            allowDownloads = manifest.downloadsAllowed,
            needsLosslessOutput = manifest.requiresLosslessOutput,
        )
    }

    fun setEnabled(id: String, enabled: Boolean) = change(id) { it.copy(enabled = enabled) }

    fun setQuality(id: String, value: String?) = change(id) { it.copy(pickedQuality = value) }

    fun remove(id: String) {
        save { list -> list.filterNot { it.id == id } }
        synchronized(clients) { clients.remove(id) }
    }

    /** Moves an add-on one place up ([by] -1) or down (+1) in the order of preference. */
    fun move(id: String, by: Int) = save { list ->
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun change(id: String, transform: (AddonConfig) -> AddonConfig) =
        save { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun save(transform: (List<AddonConfig>) -> List<AddonConfig>) {
        _addons.update(transform)
        preferences.edit { putString(KEY, AddonClient.JSON.encodeToString(SERIALIZER, _addons.value)) }
    }

    private fun load(): List<AddonConfig> =
        preferences.getString(KEY, null)
            ?.let { runCatching { AddonClient.JSON.decodeFromString(SERIALIZER, it) }.getOrNull() }
            .orEmpty()

    companion object {
        private const val KEY = "addons"
        private val SERIALIZER = ListSerializer(AddonConfig.serializer())

        @Volatile private var instance: AddonStore? = null

        fun get(context: Context): AddonStore =
            instance ?: synchronized(this) { instance ?: AddonStore(context).also { instance = it } }
    }
}
