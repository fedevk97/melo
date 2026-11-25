package `in`.melo.playback.auto

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import `in`.melo.playback.mediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lets Android Auto (and the Assistant) browse Melo and start playback. The app's own player
 * connects through the same session and is unaffected: it sends complete items with plain track ids.
 */
@UnstableApi
internal class AutoCallback(context: Context, private val scope: CoroutineScope) : MediaLibraryService.MediaLibrarySession.Callback {
    private val app = context.applicationContext
    private val library = AutoLibrary(app)

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        if (controller.packageName == app.packageName || controller.isTrusted || session.isAutoController(controller))
            super.onConnect(session, controller)
        else MediaSession.ConnectionResult.reject()

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = completed(LibraryResult.ofItem(library.root(), library.rootParams()))

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        library.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo,
        parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        LibraryResult.ofItemList(ImmutableList.copyOf(library.children(parentId, page, pageSize)), params)
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        session.notifySearchResultChanged(browser, query, library.search(query).size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession, browser: MediaSession.ControllerInfo,
        query: String, page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        LibraryResult.ofItemList(ImmutableList.copyOf(library.searchResults(query, page, pageSize)), params)
    }

    /** A track chosen in Android Auto becomes the queue of the list it was in; everything else is queued as sent. */
    override fun onSetMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val chosen = mediaItems.singleOrNull()?.mediaId?.let { library.queueFor(it) }
        if (chosen != null) MediaSession.MediaItemsWithStartPosition(chosen.first.map { mediaItem(it) }, chosen.second, startPositionMs)
        else MediaSession.MediaItemsWithStartPosition(resolveAll(mediaItems), startIndex, startPositionMs)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = future { resolveAll(mediaItems).toMutableList() }

    /** Items named by an Android Auto node become playable; items the app built itself pass through. */
    private suspend fun resolveAll(items: List<MediaItem>): List<MediaItem> = items.mapNotNull { item ->
        val (parent, trackId) = AutoIds.parseTrack(item.mediaId) ?: return@mapNotNull item
        val pool = if (parent == AutoIds.SEARCH) library.searchPool() else library.tracks(parent)
        pool.firstOrNull { it.id == trackId }?.let { mediaItem(it) }
    }

    private fun <T> completed(value: T): ListenableFuture<T> = SettableFuture.create<T>().also { it.set(value) }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch {
            try { result.set(block()) }
            catch (cancelled: CancellationException) { result.cancel(true); throw cancelled }
            catch (error: Exception) { result.setException(error) }
        }
        result.addListener({ if (result.isCancelled) job.cancel() }, Runnable::run)
        return result
    }
}

/** Android Auto on the phone, or the car's own system, as opposed to any other app that asks. */
internal fun MediaSession.isAutoController(controller: MediaSession.ControllerInfo): Boolean =
    isAutomotiveController(controller) || isAutoCompanionController(controller)
