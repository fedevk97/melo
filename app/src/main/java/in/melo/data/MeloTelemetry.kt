package `in`.melo.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import `in`.melo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Tells Melo's API that someone is using Melo, for the public live count. An install counts while
 * the app is in the foreground or music is playing, so listening with the screen off or from
 * another app counts too. Only a random
 * install ID and the platform are sent, never anything about the listener or what they play, so
 * it runs whatever the analytics setting says.
 */
object MeloTelemetry {
    private const val PREFS = "melo_telemetry"
    private const val INSTALL_ID = "install_id"
    /** A quick trip to another app and back is not worth two pings. */
    private const val CLOSE_DEBOUNCE_MS = 2_000L
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foreground = MutableStateFlow(false)
    private val playing = MutableStateFlow(false)
    @Volatile private var installationId = ""
    @Volatile private var intervalSec = Presence.DEFAULT_INTERVAL_SEC
    /** Whether the server may be counting this install, so a close is owed. */
    @Volatile private var announced = false

    fun initialize(application: Application) {
        if (BuildConfig.MELO_STATS_ENDPOINT.isBlank() || installationId.isNotEmpty()) return
        val preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        installationId = preferences.getString(INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(INSTALL_ID, it).apply()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.value = true }
            override fun onStop(owner: LifecycleOwner) { foreground.value = false }
        })
        scope.launch {
            // collectLatest: leaving the app or stopping the music cancels the wait for the next
            // ping, and coming back cancels a close still waiting out its debounce.
            combine(foreground, playing) { shown, listening -> shown || listening }.distinctUntilChanged().collectLatest { open ->
                if (open) {
                    while (true) {
                        announced = true
                        send(true)?.let { intervalSec = Presence.clampInterval(it) }
                        delay(Presence.jittered(intervalSec, Random.nextDouble()))
                    }
                } else if (announced) {
                    delay(CLOSE_DEBOUNCE_MS)
                    if (send(false) != null) announced = false
                }
            }
        }
    }

    /** Whether music is playing, or about to once it has buffered. Called by the playback service. */
    fun setPlaying(value: Boolean) {
        playing.value = value
    }

    /** Posts one ping and returns the interval the server asks for, or null if it failed. */
    private suspend fun send(open: Boolean): Int? {
        val endpoint = BuildConfig.MELO_STATS_ENDPOINT
        if (endpoint.isBlank() || installationId.isEmpty()) return null
        val payload = JSONObject().put("id", installationId).put("platform", "android").put("open", open).toString()
        val call = client.newCall(Request.Builder().url(endpoint).post(payload.toRequestBody(jsonType)).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                // Best effort: a missed ping only means the server drops this install a little early.
                override fun onFailure(call: Call, e: IOException) = continuation.resume(null)
                override fun onResponse(call: Call, response: Response) = continuation.resume(response.use {
                    if (!it.isSuccessful) null
                    else runCatching { JSONObject(it.body.string()).optInt("intervalSec", 0) }.getOrNull() ?: 0
                })
            })
        }
    }
}

internal object Presence {
    const val DEFAULT_INTERVAL_SEC = 300
    private const val MIN_INTERVAL_SEC = 60
    private const val MAX_INTERVAL_SEC = 3600

    /** The server's interval, held to a sane range; a missing one keeps the default. */
    fun clampInterval(seconds: Int): Int =
        if (seconds <= 0) DEFAULT_INTERVAL_SEC else seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)

    /** The wait before the next ping, within 10% of the interval so installs do not ping in step. */
    fun jittered(intervalSec: Int, random: Double): Long =
        (intervalSec * 1000L * (0.9 + 0.2 * random.coerceIn(0.0, 1.0))).toLong()
}
