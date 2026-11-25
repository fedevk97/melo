package `in`.melo.playback

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.melo.data.LocalPlaylistStore
import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Browses Melo the way Android Auto does: through the platform media browser, which reaches the
 * service by its `android.media.browse.MediaBrowserService` action. Nothing here plays audio.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val main = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private var playlistId: String? = null

    @Before fun connect() {
        val tracks = listOf(Music("ytm:track:aaa", "First song", "Artist A", MusicKind.Track),
            Music("ytm:track:bbb", "Second song", "Artist B", MusicKind.Track))
        playlistId = LocalPlaylistStore(app).create("Auto test playlist", tracks).id
        val connected = CountDownLatch(1)
        main.post {
            browser = MediaBrowser(app, ComponentName(app, MeloPlaybackService::class.java), object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() = connected.countDown()
            }, null).also { it.connect() }
        }
        assertTrue("The media browser did not connect", connected.await(30, TimeUnit.SECONDS))
    }

    @After fun clean() {
        playlistId?.let { LocalPlaylistStore(app).delete(it) }
        main.post { browser?.disconnect() }
    }

    private fun children(parent: String): List<MediaBrowser.MediaItem> {
        val done = CountDownLatch(1)
        var result: List<MediaBrowser.MediaItem>? = null
        main.post {
            browser!!.subscribe(parent, object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, items: MutableList<MediaBrowser.MediaItem>) { result = items; done.countDown() }
                override fun onError(parentId: String) { done.countDown() }
            })
        }
        assertTrue("No answer for $parent", done.await(30, TimeUnit.SECONDS))
        main.post { browser!!.unsubscribe(parent) }
        return result ?: error("Android Auto would get an error for $parent")
    }

    @Test fun rootListsTheThreeTabs() {
        val root = browser!!.root
        val folders = children(root)
        assertEquals(listOf("Home", "Library", "Downloaded"), folders.map { it.description.title.toString() })
        assertTrue(folders.all { it.isBrowsable })
    }

    @Test fun playlistsListTheDevicePlaylistAndItsTracks() {
        val playlists = children("auto:playlists")
        val ours = playlists.single { it.description.title == "Auto test playlist" }
        val tracks = children(ours.mediaId!!).drop(1) // the first row is "Shuffle play"
        assertEquals(listOf("First song", "Second song"), tracks.map { it.description.title.toString() })
        assertTrue(tracks.all { it.isPlayable })
    }

    @Test fun choosingATrackQueuesItsWholeList() {
        val ours = children("auto:playlists").single { it.description.title == "Auto test playlist" }
        val second = children(ours.mediaId!!).last()
        val token = browser!!.sessionToken
        val queued = CountDownLatch(1)
        var titles: List<String> = emptyList()
        main.post {
            val controller = MediaController(app, token)
            controller.registerCallback(object : MediaController.Callback() {
                override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) {
                    val now = queue.orEmpty().map { it.description.title.toString() }
                    if (now.size == 2) { titles = now; queued.countDown() }
                }
            })
            controller.transportControls.playFromMediaId(second.mediaId!!, Bundle())
        }
        assertTrue("The queue never filled", queued.await(30, TimeUnit.SECONDS))
        assertEquals(listOf("First song", "Second song"), titles)
        main.post { MediaController(app, token).transportControls.stop() }
    }
}
