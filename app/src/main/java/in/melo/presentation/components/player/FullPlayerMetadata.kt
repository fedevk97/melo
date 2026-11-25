package `in`.melo.presentation.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import `in`.melo.presentation.icons.meloPainter
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import `in`.melo.R
import `in`.melo.data.model.Artist
import `in`.melo.data.model.Song
import `in`.melo.data.preferences.FullPlayerLoadingTweaks
import `in`.melo.presentation.viewmodel.PlayerSheetState
import `in`.melo.presentation.viewmodel.PlayerViewModel
import `in`.melo.ui.theme.GoogleSansRounded
import `in`.melo.ui.theme.MeloLayout
import kotlinx.coroutines.launch

// The title, artist and the buttons that sit beside them on the full player.
// Split out of FullPlayerContent.kt; behaviour unchanged.

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullPlayerSongMetadataSection(
    song: Song,
    currentSongArtists: List<Artist>,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isLandscape: Boolean,
    onLyricsClick: () -> Unit,
    playerOnBaseColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onQueueClick: () -> Unit,
    onArtistClick: () -> Unit,
    isPlayingProvider: () -> Boolean = { true }
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.20f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(70.dp))
            } else {
                MetadataPlaceholder(
                    expansionFractionProvider = expansionFractionProvider,
                    color = placeholderColor,
                    onColor = placeholderOnColor,
                    showQueueButtons = isLandscape
                )
            }
        }
    ) {
        SongMetadataDisplaySection(
            modifier = Modifier
                .padding(start = 0.dp),
            song = song,
            currentSongArtists = currentSongArtists,
            expansionFractionProvider = expansionFractionProvider,
            textColor = playerOnBaseColor,
            artistTextColor = playerOnBaseColor.copy(alpha = PLAYER_ARTIST_INK_ALPHA),
            playerViewModel = playerViewModel,
            chipColor = chipColor,
            chipContentColor = chipContentColor,
            onClickLyrics = onLyricsClick,
            showQueueButton = isLandscape,
            onClickQueue = onQueueClick,
            onClickArtist = onArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true }
) {
    // The lyrics and queue chips are the dock's glass, so the player's controls match.
    val chipDark = `in`.melo.presentation.components.LocalMaterialTheme.current.surface.luminance() < 0.5f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                title = currentSong.title,
                artist = currentSong.displayArtist,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                playerViewModel = playerViewModel,
                onClickTitle = { playerViewModel.openAlbumOf(currentSong) },
                onClickArtist = onClickArtist,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                isPlayingProvider = isPlayingProvider
            )
        }
        
        if (!showQueueButton) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .playerDockGlass(CircleShape, chipDark)
                    .clickable(onClick = onClickLyrics),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = meloPainter(R.drawable.rounded_lyrics_24),
                    contentDescription = stringResource(R.string.common_lyrics),
                    tint = chipContentColor
                )
            }
        }
        if (showQueueButton) {
            Box(
                modifier = Modifier
                    .size(height = 42.dp, width = 50.dp)
                    .playerDockGlass(MeloLayout.PanelShape, chipDark)
                    .clickable { onClickLyrics() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = meloPainter(R.drawable.rounded_lyrics_24),
                    contentDescription = stringResource(R.string.common_lyrics),
                    tint = chipContentColor
                )
            }
            Box(
                modifier = Modifier
                    .size(height = 42.dp, width = 50.dp)
                    .playerDockGlass(CircleShape, chipDark)
                    .clickable { onClickQueue() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = meloPainter(R.drawable.rounded_queue_music_24),
                    contentDescription = stringResource(R.string.player_cd_open_queue),
                    tint = chipContentColor
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSongInfo(
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    playerViewModel: PlayerViewModel,
    onClickTitle: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true }
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val visibleTitle = title.ifBlank { stringResource(R.string.common_unknown_track) }
    val visibleArtist = artist.takeIf(String::isNotBlank)
        ?: artists.asSequence().map(Artist::name).firstOrNull(String::isNotBlank)
        ?: stringResource(R.string.common_unknown_artist)
    val canMarquee by remember {
        derivedStateOf { isPlayingProvider() && expansionFractionProvider() > 0.99f }
    }
    val resolvedArtistId by remember(artists, artistId) {
        derivedStateOf { artists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: artistId }
    }
    val titleStyle = playerTitleStyle().copy(color = textColor)
    val artistStyle = playerArtistStyle().copy(color = artistTextColor)

    Column(
        horizontalAlignment = Alignment.Start,
            modifier = modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction // Or apply specific fade logic if desired
                translationY = (1f - fraction) * 24f
            }
    ) {
        // A new song's credits cross over the old ones rather than cutting to them.
        Crossfade(visibleTitle, animationSpec = tween(CREDITS_CROSSFADE_MS), label = "playerTitle") { shownTitle ->
        Text(
            text = shownTitle,
            style = titleStyle,
            maxLines = 1,
            softWrap = false,
            overflow = if (canMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .lyricsMorphPlayer(MorphPart.TITLE)
                .fillMaxWidth()
                .then(if (canMarquee) Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    spacing = MarqueeSpacing(24.dp),
                    velocity = 25.dp,
                    initialDelayMillis = 2_000
                ) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClickTitle
                )
        )
        }
        Spacer(modifier = Modifier.height(2.dp))

        Crossfade(visibleArtist, animationSpec = tween(CREDITS_CROSSFADE_MS), label = "playerArtist") { shownArtist ->
        Text(
            text = shownArtist,
            style = artistStyle,
            maxLines = 1,
            softWrap = false,
            overflow = if (canMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .lyricsMorphPlayer(MorphPart.ARTIST)
                .fillMaxWidth()
                .then(if (canMarquee) Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    spacing = MarqueeSpacing(24.dp),
                    velocity = 25.dp,
                    initialDelayMillis = 2_000
                ) else Modifier)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },
                    onLongClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    }
                )
        )
        }
    }
}

/** The artist line's share of the player's ink: a step back from the title. */
internal const val PLAYER_ARTIST_INK_ALPHA = 0.55f

/** How long a new song's credits take to cross over the last one's. */
private const val CREDITS_CROSSFADE_MS = 300

/** The player's title type, shared with its copy that travels to the lyrics header. */
@Composable
internal fun playerTitleStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontWeight = FontWeight.Bold,
    fontFamily = GoogleSansRounded,
)

/** The player's artist type, shared with its copy that travels to the lyrics header. */
@Composable
internal fun playerArtistStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp)
