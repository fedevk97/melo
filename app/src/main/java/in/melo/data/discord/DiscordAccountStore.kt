package `in`.melo.data.discord

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A connected Discord account. [token] grants access to the whole account. */
data class DiscordAccount(
    val token: String,
    val username: String = "",
    val name: String = "",
    val avatar: String? = null,
)

/**
 * The Discord account, encrypted with a key that never leaves the Android keystore.
 * Shared by the settings page and the playback service, which run in one process.
 */
class DiscordAccountStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("discord_account", Context.MODE_PRIVATE)
    private val mutableAccount = MutableStateFlow(load())
    val account: StateFlow<DiscordAccount?> = mutableAccount.asStateFlow()

    fun save(account: DiscordAccount) {
        val clear = JSONObject().put("token", account.token).put("username", account.username)
            .put("name", account.name).put("avatar", account.avatar ?: "").toString().encodeToByteArray()
        val blob = try {
            Cipher.getInstance(CIPHER).run {
                init(Cipher.ENCRYPT_MODE, key())
                check(iv.size == IV_SIZE)
                iv + doFinal(clear)
            }
        } finally {
            clear.fill(0)
        }
        prefs.edit { putString(BLOB, Base64.encodeToString(blob, Base64.NO_WRAP)) }
        mutableAccount.value = account
    }

    /** Keeps the token and refreshes what the account row shows. */
    fun updateProfile(username: String, name: String, avatar: String?) {
        val current = mutableAccount.value ?: return
        val next = current.copy(username = username, name = name, avatar = avatar)
        if (next != current) save(next)
    }

    fun clear() {
        prefs.edit { remove(BLOB) }
        mutableAccount.value = null
    }

    private fun load(): DiscordAccount? = try {
        val blob = prefs.getString(BLOB, null)
        val key = keyStore().getKey(KEY, null) as? SecretKey
        if (blob == null || key == null) null else {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE)
            val clear = Cipher.getInstance(CIPHER).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
                doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            }
            try {
                val json = JSONObject(clear.decodeToString())
                DiscordAccount(
                    token = json.getString("token"),
                    username = json.optString("username"),
                    name = json.optString("name"),
                    avatar = json.optString("avatar").ifEmpty { null },
                ).takeIf { it.token.isNotBlank() }
            } finally {
                clear.fill(0)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun key(): SecretKey = keyStore().getKey(KEY, null) as? SecretKey ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
    ).apply {
        init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
    }.generateKey()

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY = "melo_discord_account"
        private const val BLOB = "account"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        @Volatile private var instance: DiscordAccountStore? = null
        fun get(context: Context): DiscordAccountStore = instance ?: synchronized(this) {
            instance ?: DiscordAccountStore(context.applicationContext).also { instance = it }
        }
    }
}
