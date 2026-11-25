package `in`.melo.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * The sleep timer. It holds a deadline, or a number of songs still to finish, and the playback
 * service watches both: the timer keeps running when the app's screens are gone, and nothing has
 * to tick for it to stay accurate. elapsedRealtime, so changing the clock can't cut it short.
 */
object SleepTimer {
    /** When to pause, on [SystemClock.elapsedRealtime], or null when no timed pause is set. */
    val deadline = MutableStateFlow<Long?>(null)

    /** The length that was chosen, so the picker can tick it. Null when off. */
    val minutes = MutableStateFlow<Int?>(null)

    /** Songs still to finish before pausing, the playing one included; 0 when off. */
    val songsLeft = MutableStateFlow(0)

    /** Lengths offered as rows; anything else is set on the custom page. */
    val PRESETS = listOf(15, 30, 45, 60)

    const val STEP_MINUTES = 5
    const val MAX_MINUTES = 720
    const val MAX_SONGS = 10

    /** The custom length a slider at [fraction] of its travel stands for. */
    fun minutesAt(fraction: Float): Int {
        val raw = STEP_MINUTES + fraction.coerceIn(0f, 1f) * (MAX_MINUTES - STEP_MINUTES)
        return (raw / STEP_MINUTES).roundToInt() * STEP_MINUTES
    }

    /** Where [minutes] sits along the custom slider, the inverse of [minutesAt]. */
    fun fractionOf(minutes: Int): Float =
        ((minutes - STEP_MINUTES).toFloat() / (MAX_MINUTES - STEP_MINUTES)).coerceIn(0f, 1f)

    fun start(minutes: Int) {
        songsLeft.value = 0
        this.minutes.value = minutes
        deadline.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    /** Pause once [count] songs have finished, the one playing now being the first. */
    fun startAfterSongs(count: Int) {
        minutes.value = null
        deadline.value = null
        songsLeft.value = count.coerceIn(1, MAX_SONGS)
    }

    fun cancel() {
        minutes.value = null
        deadline.value = null
        songsLeft.value = 0
    }

    /** A song played through to its end: one fewer to go. */
    fun songFinished() {
        if (songsLeft.value > 1) songsLeft.value -= 1
    }

    /** How long is left, or null when no timed pause is set. */
    fun remainingMs(): Long? =
        deadline.value?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }
}
