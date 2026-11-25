package `in`.melo.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Playlist writes over Playlist4 HTTP: `ListChanges` deltas posted to `playlist/v2/playlist/{id}/changes`
 * for a playlist's items and attributes, and to `playlist/v2/user/{username}/rootlist/changes` for the
 * account's list of playlists (create, publish, unfollow). See protocol/06-playlist-writes.md.
 */
internal class SpotifyPlaylists(private val context: Context) : AutoCloseable {
    override fun close() = Unit

    private suspend fun session(): SpotifySession {
        if (!NetworkMonitor.online.value) throw SpotifyFailure("Sign in to edit a Spotify playlist.")
        return SpotifyAccount.require(context)
    }

    private fun playlistPath(uri: String) = "playlist/v2/playlist/${playlistUri(uri).substringAfterLast(':')}"
    private suspend fun rootlistPath(session: SpotifySession) = "playlist/v2/user/${session.username()}/rootlist"

    /** The playlist's head: revision, length and owner, without items. */
    private suspend fun head(session: SpotifySession, uri: String, operation: String): Proto {
        val url = spotifyUrl(playlistPath(uri)).newBuilder()
            .addQueryParameter("from", "0").addQueryParameter("length", "0")
            .addQueryParameter("decorate", "revision,length,owner").build()
        return Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, url, operation))
    }

    private suspend fun postChanges(session: SpotifySession, path: String, changes: ByteArray, operation: String): Proto =
        Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, spotifyUrl("$path/changes"), operation, changes))

    /** A write based on a revision another client has since moved past (409) is read and sent once more. */
    private suspend fun <T> retryOnConflict(block: suspend () -> T): T = try { block() }
    catch (failure: SpotifyFailure) { if (failure.status != 409) throw failure; block() }

    /** Applies [ops] to a playlist at its current revision. */
    private suspend fun changePlaylist(uri: String, operation: String, ops: List<ByteArray>) {
        val session = session()
        retryOnConflict {
            val revision = head(session, uri, operation).bytes(1)
            postChanges(session, playlistPath(uri), listChanges(revision, ops, session.username()), operation)
        }
    }

    suspend fun create(name: String, tracks: List<Music> = emptyList(), published: Boolean = false): Music {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 100) { "The name must be between 1 and 100 characters." }
        val uris = spotifyTracks(tracks)
        val session = session()
        val reply = Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, spotifyUrl("playlist/v2/playlist"),
            "New playlist", createPlaylistRequest(clean)))
        val uri = reply.text(1)?.let(::canonicalMusicUri)?.takeIf { it.startsWith("spotify:playlist:") }
            ?: throw SpotifyFailure("Spotify did not return the new playlist.")
        // A new list is in nobody's library until the rootlist holds it: put it first, as the apps do.
        retryOnConflict {
            postChanges(session, rootlistPath(session), listChanges(rootRevision(session),
                listOf(addOp(listOf(uri), first = true, public = published)), session.username()), "New playlist")
        }
        uris.chunked(ADD_CHUNK).forEach { chunk -> changePlaylist(uri, "New playlist", listOf(addOp(chunk))) }
        return Music(uri, clean, `in`.melo.utils.formatSongCount(uris.size), MusicKind.Playlist, ownerUsername = session.username())
    }

    suspend fun rename(uri: String, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 100)
        changePlaylist(uri, "Rename playlist", listOf(updateListAttributesOp(proto { writeString(1, clean) })))
    }

    /** The playlist's row in the rootlist: its index, the revision it was read at, its URI as stored, and its `public` flag. */
    private class RootEntry(val index: Int, val revision: ByteArray?, val uri: String, val public: Boolean)

    private suspend fun rootEntry(session: SpotifySession, uri: String): RootEntry? {
        val target = playlistUri(uri)
        var offset = 0
        while (true) {
            val url = spotifyUrl(rootlistPath(session)).newBuilder()
                .addQueryParameter("decorate", "revision,length")
                .addQueryParameter("from", offset.toString()).addQueryParameter("length", "300").build()
            val response = Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, url, "Spotify library"))
            val contents = response.message(5) ?: return null
            val items = contents.messages(3)
            val start = contents.number(1) ?: offset
            items.withIndex().firstOrNull { (_, entry) -> entry.text(1)?.let(::canonicalMusicUri) == target }?.let { (index, row) ->
                return RootEntry(start + index, response.bytes(1), checkNotNull(row.text(1)), row.message(2)?.number(10) == 1)
            }
            val total = response.number(2) ?: 0
            if (items.isEmpty() || (contents.number(2) != 1 && start + items.size >= total)) return null
            offset = start + items.size
        }
    }

    private suspend fun rootRevision(session: SpotifySession): ByteArray? {
        val url = spotifyUrl(rootlistPath(session)).newBuilder()
            .addQueryParameter("decorate", "revision").addQueryParameter("from", "0").addQueryParameter("length", "0").build()
        return Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, url, "Spotify library")).bytes(1)
    }

    /** Publishing is the `public` attribute of the playlist's rootlist row, unrelated to collaborative access. */
    suspend fun setPublished(uri: String, published: Boolean) {
        val session = session()
        retryOnConflict {
            val entry = rootEntry(session, uri) ?: throw SpotifyFailure("This playlist is not in your library.")
            if (entry.public != published) postChanges(session, rootlistPath(session),
                listChanges(entry.revision, listOf(updateItemAttributesOp(entry.index, proto { writeBool(10, published) })), session.username()),
                "Publish playlist")
        }
    }

    suspend fun add(uri: String, tracks: List<Music>, onAccepted: suspend () -> Unit = {}) {
        val uris = spotifyTracks(tracks)
        if (uris.isEmpty()) return
        // Order and duplicates are kept, as Spotify does; one bounded delta per chunk.
        uris.chunked(ADD_CHUNK).forEach { chunk -> changePlaylist(uri, "Add to playlist", listOf(addOp(chunk))) }
        onAccepted()
    }

    /**
     * Removes one appearance of a track: the [occurrence]-th (from 0) row holding [trackUri].
     * Rows are removed by item ID, so a duplicated track loses only the chosen copy.
     */
    suspend fun remove(uri: String, trackUri: String, occurrence: Int, onAccepted: suspend () -> Unit = {}) {
        val target = playlistEntryUri(trackUri) ?: throw SpotifyFailure("A Spotify playlist only holds Spotify tracks and local files.")
        val session = session()
        retryOnConflict {
            val (revision, rows) = rows(session, uri)
            val row = rows.filter { it.uri == target }.getOrNull(occurrence)
                ?: throw SpotifyFailure("This track is no longer in the playlist.")
            postChanges(session, playlistPath(uri), listChanges(revision, listOf(removeOp(row)), session.username()), "Remove from playlist")
        }
        onAccepted()
    }

    /** Every row of the playlist, with its index and item ID, and the revision they were read at. */
    private suspend fun rows(session: SpotifySession, uri: String): Pair<ByteArray?, List<PlaylistRow>> {
        val result = ArrayList<PlaylistRow>()
        var revision: ByteArray? = null
        while (true) {
            val url = spotifyUrl(playlistPath(uri)).newBuilder()
                .addQueryParameter("from", result.size.toString()).addQueryParameter("length", ROW_PAGE.toString())
                .addQueryParameter("decorate", "revision,length").build()
            val page = Proto(authenticatedSpotifyRequest(SpotifyAccount.transport, session, url, "Remove from playlist"))
            val pageRevision = page.bytes(1)
            // The list changed while it was being read: start again at the new revision.
            if (revision != null && pageRevision != null && !revision.contentEquals(pageRevision)) result.clear()
            revision = pageRevision ?: revision
            val items = decodePlaylistRows(page, result.size)
            result += items
            if (items.isEmpty() || result.size >= (page.number(2) ?: result.size)) return revision to result
        }
    }

    /** Removes a playlist from the account's library. Only for a list created by a test or requested by its owner. */
    suspend fun unfollow(uri: String) {
        val session = session()
        retryOnConflict {
            val entry = rootEntry(session, uri) ?: return@retryOnConflict
            postChanges(session, rootlistPath(session),
                listChanges(entry.revision, listOf(removeOp(PlaylistRow(entry.index, entry.uri, null))), session.username()),
                "Remove playlist")
        }
    }

    /** Image bytes go directly to the same dedicated upload host as SetPlaylistPictureWorker. */
    suspend fun setCover(uri: String, jpeg: ByteArray) {
        require(jpeg.isNotEmpty() && jpeg.size <= 4 * 1024 * 1024)
        val session = session()
        val host = "image-upload.spotify.com"
        val headers = session.headers(host)
        val uploaded = SpotifyAccount.transport.send(Request.Builder().url("https://$host/v4/playlist")
            .header("Authorization", "Bearer ${headers.user}").header("client-token", headers.client)
            .header("Spotify-App-Version", SpotifyAndroid.VERSION).header("App-Platform", "Android")
            .header("Content-Encoding", "identity").post(jpeg.toRequestBody("image/jpeg".toMediaType())).build(), "Spotify cover")
        val token = JSONObject(uploaded.decodeToString()).getString("uploadToken")
        val registered = authenticatedSpotifyRequest(SpotifyAccount.transport, session,
            spotifyUrl("${playlistPath(uri)}/register-image"),
            "Cover registration", proto { writeString(1, token) }, "application/protobuf")
        val picture = pictureId(Proto(registered).bytes(1)) ?: throw SpotifyFailure("Spotify did not save the cover.")
        changePlaylist(uri, "Playlist cover", listOf(updateListAttributesOp(proto { writeByteArray(3, picture) })))
    }

    suspend fun isOwned(uri: String): Boolean {
        val session = SpotifyAccount.require(context)
        return head(session, uri, "Spotify playlist").text(16) == session.username()
    }

    suspend fun published(uri: String): Boolean = rootEntry(SpotifyAccount.require(context), uri)?.public ?: false

    /** Interleaved radios of the playlist's last few tracks, without the tracks it already holds. */
    suspend fun recommendations(uri: String, tracks: List<Music>, fallback: List<Music>): List<Music> {
        playlistUri(uri)
        val excluded = tracks.map(Music::id).toSet()
        val seeds = tracks.takeLast(3).ifEmpty { fallback.take(1) }
        if (seeds.isEmpty()) return emptyList()
        val radio = SpotifyRadio(SpotifyAccount.transport, SpotifyAccount.require(context))
        val stations = seeds.map { seed -> radio.station(seed).tracks }
        return interleaveStations(stations).filter { it.id !in excluded }.take(20)
    }
}

internal fun playlistUri(uri: String): String = canonicalMusicUri(uri)?.takeIf { it.startsWith("spotify:playlist:") }
    ?: throw SpotifyFailure("Invalid Spotify playlist.")
private fun spotifyTracks(tracks: List<Music>): List<String> = tracks.map { track ->
    canonicalMusicUri(track.id)?.takeIf { it.startsWith("spotify:track:") }
        ?: spotifyLocalUri(track)
        ?: throw SpotifyFailure("A Spotify playlist only accepts Spotify tracks and local files.")
}

/**
 * The reference Spotify's apps use for a local file in a playlist:
 * `spotify:local:{artist}:{album}:{title}:{seconds}`, each part form-encoded. Every Spotify app
 * that lists the device's local files plays the file whose tags match.
 */
internal fun spotifyLocalUri(track: Music): String? {
    if (track.id.startsWith(SPOTIFY_LOCAL_PREFIX)) return track.id
    if (!track.id.startsWith(LOCAL_TRACK_PREFIX) || track.title.isBlank()) return null
    fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
    return SPOTIFY_LOCAL_PREFIX + listOf(encode(track.subtitle), encode(track.album), encode(track.title), (track.durationMs / 1000).toString())
        .joinToString(":")
}
private const val ROW_PAGE = 500
private const val ADD_CHUNK = 100

/** A playlist row: its position, its entry URI as stored and its Playlist4 item ID. */
internal class PlaylistRow(val index: Int, val uri: String, val itemId: ByteArray?)

/** A playlist entry as stored by Spotify: a track URI, or a `spotify:local:` reference kept as written. */
internal fun playlistEntryUri(uri: String): String? =
    if (uri.startsWith(SPOTIFY_LOCAL_PREFIX)) uri else canonicalMusicUri(uri)?.takeIf { it.startsWith("spotify:track:") }

/** The rows of a `SelectedListContent` page (5 → 3 items: 1 URI, 2 → 12 item ID), numbered from the page's position. */
internal fun decodePlaylistRows(page: Proto, from: Int): List<PlaylistRow> {
    val contents = page.message(5) ?: return emptyList()
    val start = contents.number(1) ?: from
    return contents.messages(3).mapIndexedNotNull { index, item ->
        val uri = item.text(1)?.let(::playlistEntryUri) ?: return@mapIndexedNotNull null
        PlaylistRow(start + index, uri, item.message(2)?.bytes(12))
    }
}

/** The 20-byte picture ID from `register-image`, sent raw or as 40 hex characters. */
internal fun pictureId(value: ByteArray?): ByteArray? = when {
    value == null -> null
    value.size == 20 -> value
    else -> value.decodeToString().takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }
        ?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
}

// Playlist4 `ListChanges` and its parts (playlist4_external.proto):
// ListChanges 1 base_revision, 2 deltas. Delta 1 base_version, 2 ops, 4 info (ChangeInfo 1 user, 2 timestamp).
// Op 1 kind, 2 add, 3 rem, 5 update_item_attributes, 6 update_list_attributes.
// Item 1 uri, 2 attributes (ItemAttributes 2 timestamp, 10 public, 12 item_id).
internal const val OP_ADD = 2
internal const val OP_REM = 3
internal const val OP_UPDATE_ITEM_ATTRIBUTES = 5
internal const val OP_UPDATE_LIST_ATTRIBUTES = 6

internal fun listChanges(revision: ByteArray?, ops: List<ByteArray>, username: String, now: Long = System.currentTimeMillis()): ByteArray = proto {
    if (revision != null) writeByteArray(1, revision)
    writeByteArray(2, proto {
        if (revision != null) writeByteArray(1, revision)
        ops.forEach { writeByteArray(2, it) }
        writeByteArray(4, proto { writeString(1, username); writeInt64(2, now) })
    })
}

/** `Add` (2 items, 4 add_last, 5 add_first): appends [uris], or puts them first, keeping order and duplicates. */
internal fun addOp(uris: List<String>, first: Boolean = false, public: Boolean? = null, now: Long = System.currentTimeMillis()): ByteArray = proto {
    writeEnum(1, OP_ADD)
    writeByteArray(2, proto {
        uris.forEach { uri -> writeByteArray(2, proto {
            writeString(1, uri)
            writeByteArray(2, proto { writeInt64(2, now); if (public != null) writeBool(10, public) })
        }) }
        if (first) writeBool(5, true) else writeBool(4, true)
    })
}

/** `Rem` (1 from_index, 2 length, 3 items, 7 items_as_key): one row, matched by its item ID when it has one. */
internal fun removeOp(row: PlaylistRow): ByteArray = proto {
    writeEnum(1, OP_REM)
    writeByteArray(3, proto {
        writeInt32(1, row.index); writeInt32(2, 1)
        writeByteArray(3, proto {
            writeString(1, row.uri)
            row.itemId?.let { id -> writeByteArray(2, proto { writeByteArray(12, id) }) }
        })
        writeBool(7, true)
    })
}

/** `UpdateItemAttributes` (1 index, 2 new_attributes → 1 values): sets the given `ItemAttributes` fields of one row. */
internal fun updateItemAttributesOp(index: Int, attributes: ByteArray): ByteArray = proto {
    writeEnum(1, OP_UPDATE_ITEM_ATTRIBUTES)
    writeByteArray(5, proto { writeInt32(1, index); writeByteArray(2, proto { writeByteArray(1, attributes) }) })
}

/** `UpdateListAttributes` (1 new_attributes → 1 values): sets the given `ListAttributes` fields (1 name, 2 description, 3 picture). */
internal fun updateListAttributesOp(attributes: ByteArray): ByteArray = proto {
    writeEnum(1, OP_UPDATE_LIST_ATTRIBUTES)
    writeByteArray(6, proto { writeByteArray(1, proto { writeByteArray(1, attributes) }) })
}

/** `POST playlist/v2/playlist`: an `OpList` (1 ops) naming the new list. The reply is `CreateListReply` (1 uri, 2 revision). */
internal fun createPlaylistRequest(name: String): ByteArray = proto {
    writeByteArray(1, updateListAttributesOp(proto { writeString(1, name) }))
}
