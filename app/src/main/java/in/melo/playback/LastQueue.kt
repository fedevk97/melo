package `in`.melo.playback

import android.content.Context
import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * The queue and position Melo was left at, so the next launch can pick up the same track at the
 * same second. The queue is written only when it or the current track changes; the position goes
 * to a small file of its own every few seconds, so the frequent write never copies the queue.
 */
internal object LastQueue {
    private const val QUEUE_FILE = "last_queue"
    private const val PROGRESS_FILE = "last_queue_progress"
    /** Played tracks kept behind the current one, so Previous still works after a restart. */
    private const val KEEP_BEHIND = 25
    private const val KEEP_AHEAD = 100

    /** One saved track; [queued] marks the ones the user added with Add to queue or Play next. */
    data class Entry(val track: Music, val queued: Boolean)

    data class Snapshot(val entries: List<Entry>, val index: Int, val positionMs: Long)

    fun saveQueue(context: Context, entries: List<Entry>, current: Int) {
        if (current !in entries.indices) return
        val from = (current - KEEP_BEHIND).coerceAtLeast(0)
        val to = (current + KEEP_AHEAD + 1).coerceAtMost(entries.size)
        val json = JSONArray()
        entries.subList(from, to).forEach { json.put(it.toJson()) }
        context.getSharedPreferences(QUEUE_FILE, Context.MODE_PRIVATE).edit()
            .putString("tracks", json.toString())
            .putInt("index", current - from)
            .putString("current", entries[current].track.id)
            .apply()
    }

    fun saveProgress(context: Context, trackId: String, positionMs: Long) {
        context.getSharedPreferences(PROGRESS_FILE, Context.MODE_PRIVATE).edit()
            .putString("track", trackId)
            .putLong("position", positionMs.coerceAtLeast(0))
            .apply()
    }

    fun load(context: Context): Snapshot? = runCatching {
        val queue = context.getSharedPreferences(QUEUE_FILE, Context.MODE_PRIVATE)
        val raw = queue.getString("tracks", null) ?: return null
        val json = JSONArray(raw)
        val entries = (0 until json.length()).mapNotNull { json.optJSONObject(it)?.toEntry() }
        if (entries.isEmpty()) return null
        val index = queue.getInt("index", 0).coerceIn(entries.indices)
        val progress = context.getSharedPreferences(PROGRESS_FILE, Context.MODE_PRIVATE)
        // The position belongs to the track it was taken on; a newer track starts from the top.
        val position = if (progress.getString("track", null) == entries[index].track.id) {
            progress.getLong("position", 0L)
        } else 0L
        Snapshot(entries, index, position)
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(QUEUE_FILE, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(PROGRESS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun Entry.toJson() = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("subtitle", track.subtitle)
        track.imageUrl?.let { put("image", it) }
        put("duration", track.durationMs)
        put("artwork", track.artwork)
        put("album", track.album)
        track.mediaUri?.let { put("mediaUri", it) }
        put("quality", track.quality)
        if (queued) put("queued", true)
    }

    private fun JSONObject.toEntry(): Entry? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        return Entry(
            Music(
                id = id,
                title = optString("title"),
                subtitle = optString("subtitle"),
                kind = MusicKind.Track,
                imageUrl = optString("image").takeIf(String::isNotBlank),
                durationMs = optInt("duration"),
                artwork = optInt("artwork"),
                album = optString("album"),
                mediaUri = optString("mediaUri").takeIf(String::isNotBlank),
                quality = optString("quality"),
            ),
            queued = optBoolean("queued"),
        )
    }
}
