package `in`.melo.data

import `in`.melo.data.preferences.AppSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpotifyPlaylistProtocolTest {
    @Test fun createNamesTheListWithOneListAttributesOp() {
        val request = Proto(createPlaylistRequest("My playlist"))
        val op = checkNotNull(request.message(1))
        assertEquals(OP_UPDATE_LIST_ATTRIBUTES, op.number(1))
        assertEquals("My playlist", op.message(6)?.message(1)?.message(1)?.text(1))
    }
    @Test fun addKeepsOrderAndDuplicatesAndChoosesTheEnd() {
        val track = "spotify:track:0123456789abcdef012345"
        val other = "spotify:track:0123456789abcdef012346"
        val op = Proto(addOp(listOf(track, other, track), now = 7))
        assertEquals(OP_ADD, op.number(1))
        val add = checkNotNull(op.message(2))
        assertEquals(listOf(track, other, track), add.messages(2).map { it.text(1) })
        assertEquals(1, add.number(4))
        assertNull(add.number(5))
        assertNull(add.messages(2).first().message(2)?.number(10))
        val first = checkNotNull(Proto(addOp(listOf(track), first = true, public = true)).message(2))
        assertEquals(1, first.number(5))
        assertNull(first.number(4))
        assertEquals(1, first.messages(2).single().message(2)?.number(10))
    }
    @Test fun changesCarryRevisionOpsAndAuthor() {
        val revision = byteArrayOf(0, 0, 0, 3, 9, 9)
        val changes = Proto(listChanges(revision, listOf(addOp(listOf("spotify:track:0123456789abcdef012345"))), "me", now = 42))
        assertArrayEquals(revision, changes.bytes(1))
        val delta = checkNotNull(changes.message(2))
        assertArrayEquals(revision, delta.bytes(1))
        assertEquals(1, delta.messages(2).size)
        assertEquals("me", delta.message(4)?.text(1))
        assertEquals(42, delta.message(4)?.number(2))
    }
    @Test fun accountPlaylistsAndLocalPlaylistsCannotBeConfused() {
        assertEquals("spotify:playlist:0123456789abcdef012345", playlistUri("spotify:playlist:0123456789abcdef012345"))
        assertThrows(SpotifyFailure::class.java) { playlistUri("local:playlist:mine") }
        assertThrows(SpotifyFailure::class.java) { playlistUri("spotify:track:0123456789abcdef012345") }
    }
    @Test fun oldBackupsKeepBoundedQueueAndSettingsRoundTrip() {
        val migrated = AppSettings.fromJson(JSONObject("{\"preloadTracks\":2}"))
        assertEquals(2, migrated.preloadTracks)
        assertTrue(migrated.preloadUpcomingTracks)
        assertEquals(10, AppSettings.fromJson(JSONObject("{\"preloadEntireQueue\":true,\"preloadTracks\":100}")).preloadTracks)
        assertFalse(migrated.preloadOnMobile)
        assertFalse(migrated.offlineMode)
        val chosen = migrated.copy(preloadUpcomingTracks = true, preloadTracks = 20, preloadOnMobile = false, offlineMode = true)
        assertEquals(chosen, AppSettings.fromJson(chosen.toJson()))
        val invalid = AppSettings.fromJson(JSONObject("{\"audioCacheLimitMb\":-1,\"preloadTracks\":1000000}"))
        assertEquals(512, invalid.audioCacheLimitMb)
        assertEquals(20, invalid.preloadTracks)
    }
    @Test fun ownershipSurvivesCatalogueStorage() {
        val playlist = Music("spotify:playlist:0123456789abcdef012345", "P", "0 tracks", MusicKind.Playlist, ownerUsername = "owner")
        assertEquals(listOf(playlist), decodePins(encodePins(listOf(playlist))))
    }
    @Test fun rowsAreNumberedFromThePageAndSkipEpisodes() {
        val id = ByteArray(16) { it.toByte() }
        val page = Proto(proto {
            writeByteArray(5, proto {
                writeInt32(1, 500)
                writeByteArray(3, proto { writeString(1, "spotify:track:0123456789abcdef012345"); writeByteArray(2, proto { writeByteArray(12, id) }) })
                writeByteArray(3, proto { writeString(1, "spotify:episode:0123456789abcdef012345") })
                writeByteArray(3, proto { writeString(1, "spotify:local:A:B:C+d:12") })
            })
        })
        val rows = decodePlaylistRows(page, from = 0)
        assertEquals(listOf(500, 502), rows.map { it.index })
        assertEquals(listOf("spotify:track:0123456789abcdef012345", "spotify:local:A:B:C+d:12"), rows.map { it.uri })
        assertArrayEquals(id, rows[0].itemId)
        assertNull(rows[1].itemId)
    }
    @Test fun pictureIdsAcceptRawAndHexForms() {
        val raw = ByteArray(20) { (it * 13).toByte() }
        assertArrayEquals(raw, pictureId(raw))
        assertArrayEquals(raw, pictureId(raw.joinToString("") { "%02x".format(it.toInt() and 255) }.encodeToByteArray()))
        assertNull(pictureId("not a picture".encodeToByteArray()))
        assertNull(pictureId(null))
    }
    @Test fun removalTargetsRowsAndKeepsLocalReferencesAsWritten() {
        val id = ByteArray(16) { 7 }
        val op = Proto(removeOp(PlaylistRow(4, "spotify:track:0123456789abcdef012345", id)))
        assertEquals(OP_REM, op.number(1))
        val rem = checkNotNull(op.message(3))
        assertEquals(4, rem.number(1))
        assertEquals(1, rem.number(2))
        assertEquals(1, rem.number(7))
        assertEquals("spotify:track:0123456789abcdef012345", rem.message(3)?.text(1))
        assertArrayEquals(id, rem.message(3)?.message(2)?.bytes(12))
        assertEquals("spotify:local:A:B:C+d:12", playlistEntryUri("spotify:local:A:B:C+d:12"))
        assertEquals("spotify:track:0123456789abcdef012345", playlistEntryUri("spotify:track:0123456789abcdef012345"))
        assertNull(playlistEntryUri("spotify:episode:0123456789abcdef012345"))
    }
    @Test fun swipeSettingsDefaultToQueueOnTheRightAndRoundTrip() {
        val defaults = AppSettings.fromJson(JSONObject("{}"))
        assertEquals("queue", defaults.swipeRight)
        assertEquals("none", defaults.swipeLeft)
        val chosen = defaults.copy(swipeRight = "next", swipeLeft = "remove")
        assertEquals(chosen, AppSettings.fromJson(chosen.toJson()))
        assertEquals(`in`.melo.presentation.components.TrackSwipeAction.NONE, `in`.melo.presentation.components.TrackSwipeAction.of("unknown"))
    }
}
