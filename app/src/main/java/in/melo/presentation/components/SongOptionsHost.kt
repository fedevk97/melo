package `in`.melo.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import `in`.melo.data.model.Song
import `in`.melo.data.model.toMusic
import `in`.melo.data.model.toSong
import `in`.melo.presentation.navigation.openMusic
import `in`.melo.presentation.viewmodel.PlayerViewModel

/** The track sheet, wired for screens outside the player. */
@Composable
fun SongOptionsHost(
    ui: PlayerViewModel,
    navController: NavController,
    selected: Song?,
    context: List<Song>,
    queueName: String,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onRemoveFromDial: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val song = selected ?: return
    val saved by ui.model.favorites.collectAsStateWithLifecycle()
    val isFavorite = song.id in saved
    SongInfoBottomSheet(
        song = song.copy(isFavorite = isFavorite),
        isFavorite = isFavorite,
        playerViewModel = ui,
        onToggleFavorite = { ui.toggleFavoriteSpecificSong(song) },
        onDismiss = onDismiss,
        onPlaySong = {
            val queue = context.ifEmpty { listOf(song) }
            if (queue.size <= 1) ui.playTrack(song.toMusic()) else ui.playSongs(queue, song, queueName)
        },
        onAddToQueue = { ui.addSongToQueue(song) },
        onAddNextToQueue = { ui.addSongNextToQueue(song) },
        onNavigateToAlbum = { onDismiss(); ui.openAlbumOf(song) },
        onNavigateToArtist = { onDismiss(); ui.openArtistOf(song) },
        onStartRadio = { onDismiss(); ui.startRadio(song.toMusic()) { navController.openMusic(ui, it) } },
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onRemoveFromDial = onRemoveFromDial,
    )
}

/** The long-press menu of any library element: a track menu, or its album/artist/playlist menu. */
@Composable
fun MusicOptionsHost(
    ui: PlayerViewModel,
    navController: NavController,
    selected: Music?,
    onRemoveFromDial: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val item = selected ?: return
    if (item.kind == MusicKind.Track) {
        val song = item.toSong()
        SongOptionsHost(ui, navController, song, listOf(song), item.title, null, onRemoveFromDial, onDismiss)
    } else {
        EntityOptionsSheet(
            item = item,
            playerViewModel = ui,
            onOpen = { navController.openMusic(ui, item) },
            onStartRadio = { onDismiss(); ui.startRadio(item) { navController.openMusic(ui, it) } },
            onRemoveFromDial = onRemoveFromDial,
            onDismiss = onDismiss,
        )
    }
}
