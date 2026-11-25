package `in`.melo.presentation.components

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import `in`.melo.data.canvas.CanvasArtwork

/**
 * A song's moving cover, muted and looping, cropped to fill its box. Its own small player, apart
 * from the one playing music: it takes no audio track and asks for no audio focus, so the song
 * playing underneath is never interrupted. A TextureView, so the blur and scrim over it apply.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CanvasClipPlayer(clip: CanvasArtwork, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val urls = remember(clip) { listOfNotNull(clip.url, clip.fallbackUrl).distinct() }
    val player = remember(clip) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItems(urls.map(MediaItem::fromUri))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val fallBack = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // The first address refused: try the next, if there is one.
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        }
        player.addListener(fallBack)
        onDispose {
            player.removeListener(fallBack)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).also { view ->
                player.setVideoTextureView(view)
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = view.cropTo(videoSize)
                })
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> view.cropTo(player.videoSize) }
            }
        },
        modifier = modifier,
    )
}

/** Scales the frame to cover the view, centred, rather than stretching it to the view's shape. */
private fun TextureView.cropTo(video: VideoSize) {
    val viewW = width.toFloat()
    val viewH = height.toFloat()
    if (viewW <= 0f || viewH <= 0f || video.width <= 0 || video.height <= 0) return
    val videoW = video.width * video.pixelWidthHeightRatio
    val videoH = video.height.toFloat()
    val cover = maxOf(viewW / videoW, viewH / videoH)
    // The texture is stretched to the view first; undo that, then cover.
    val sx = videoW * cover / viewW
    val sy = videoH * cover / viewH
    setTransform(Matrix().apply { setScale(sx, sy, viewW / 2f, viewH / 2f) })
}
