package `in`.melo.playback

import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import `in`.melo.playback.auto.AutoIds
import `in`.melo.playback.auto.AutoLimits
import `in`.melo.playback.auto.autoPage
import `in`.melo.playback.auto.distinctTracks
import `in`.melo.playback.auto.matching
import `in`.melo.playback.auto.resolveAutoQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoBrowseTest {
    private fun track(n: Int) = Music("spotify:track:$n", "Song $n", "Artist", MusicKind.Track)

    @Test fun trackNodeKeepsItsFolderAndTrack() {
        val node = AutoIds.track(AutoIds.RECENT, "spotify:track:abc")
        assertEquals(AutoIds.RECENT to "spotify:track:abc", AutoIds.parseTrack(node))
    }

    @Test fun playlistFolderKeepsItsId() {
        assertEquals("local-playlist:1", AutoIds.playlistIdOf(AutoIds.playlist("local-playlist:1")))
        assertNull(AutoIds.playlistIdOf(AutoIds.LOCAL))
    }

    @Test fun plainTrackIdsAndFoldersAreNotTrackNodes() {
        assertNull(AutoIds.parseTrack("spotify:track:abc"))
        assertNull(AutoIds.parseTrack(AutoIds.ROOT))
        assertNull(AutoIds.parseTrack("other|spotify:track:abc"))
        assertNull(AutoIds.parseTrack("${AutoIds.RECENT}|"))
    }

    @Test fun pagesAreBoundedAndNeverRunPastTheEnd() {
        val items = (1..250).toList()
        assertEquals((1..100).toList(), items.autoPage(0, Int.MAX_VALUE))
        assertEquals((101..200).toList(), items.autoPage(1, 100))
        assertEquals((201..250).toList(), items.autoPage(2, 100))
        assertEquals(emptyList<Int>(), items.autoPage(3, 100))
        assertEquals((1..10).toList(), items.autoPage(-1, 10))
    }

    @Test fun chosenTrackIsQueuedWithItsNeighbours() {
        val list = (1..10).map(::track)
        val (queue, index) = resolveAutoQueue(list, "spotify:track:4")!!
        assertEquals(list, queue)
        assertEquals("spotify:track:4", queue[index].id)
    }

    @Test fun longListsAreQueuedAroundTheChosenTrack() {
        val list = (1..2000).map(::track)
        val (queue, index) = resolveAutoQueue(list, "spotify:track:1000")!!
        assertEquals(AutoLimits.QUEUE_BEHIND + AutoLimits.QUEUE_AHEAD + 1, queue.size)
        assertEquals("spotify:track:1000", queue[index].id)
        val (start, startIndex) = resolveAutoQueue(list, "spotify:track:1")!!
        assertEquals(0, startIndex)
        assertEquals("spotify:track:1", start.first().id)
    }

    @Test fun unplayableEntriesAndUnknownTracksAreNotQueued() {
        val album = Music("spotify:album:1", "Album", "", MusicKind.Album)
        val list = listOf(album, track(1), track(2))
        val (queue, index) = resolveAutoQueue(list, "spotify:track:2")!!
        assertEquals(listOf(track(1), track(2)), queue)
        assertEquals(1, index)
        assertNull(resolveAutoQueue(list, "spotify:album:1"))
        assertNull(resolveAutoQueue(list, "spotify:track:99"))
    }

    @Test fun repeatedPlaysAreListedOnceAtTheNewest() {
        val history = listOf(track(2), track(1), track(2), track(3))
        assertEquals(listOf(track(2), track(1), track(3)), history.distinctTracks())
    }

    @Test fun fourTabsInSpotifyOrder() {
        assertEquals(listOf(AutoIds.HOME, AutoIds.RECENT, AutoIds.LIBRARY, AutoIds.LOCAL), AutoIds.folders)
    }

    @Test fun searchMatchesTitleArtistAndAlbumIgnoringCase() {
        val list = listOf(
            Music("spotify:track:1", "Leave In Five", "The Maine", MusicKind.Track),
            Music("spotify:track:2", "Other", "Someone", MusicKind.Track, album = "Five Roses"),
            Music("spotify:track:3", "Nothing", "Nobody", MusicKind.Track),
        )
        assertEquals(listOf("spotify:track:1", "spotify:track:2"), list.matching("five").map { it.id })
        assertEquals(listOf("spotify:track:1"), list.matching(" maine ").map { it.id })
        assertEquals(emptyList<Music>(), list.matching("  "))
    }

    @Test fun searchResultsAreTrackNodesOfTheSearchFolder() {
        val node = AutoIds.track(AutoIds.SEARCH, "spotify:track:1")
        assertEquals(AutoIds.SEARCH to "spotify:track:1", AutoIds.parseTrack(node))
    }
}
