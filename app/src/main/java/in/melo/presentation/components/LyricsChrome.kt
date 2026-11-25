package `in`.melo.presentation.components

import `in`.melo.presentation.components.player.LyricsHeaderArtCorner
import `in`.melo.presentation.components.player.MorphPart
import `in`.melo.presentation.components.player.lyricsMorphHeader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.R
import `in`.melo.data.model.Song
import `in`.melo.presentation.components.player.playerDockGlass
import `in`.melo.presentation.components.subcomps.PlayingEqIcon
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.utils.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToLong

// The lyrics screen's chrome: the now-playing header along the top, and the
// scrubber, transport and mode switch along the bottom. Drawn in one ink over
// either the artwork backdrop or the sheet's own colour.

private val HeaderArtSize = 54.dp
private val HeaderArtShape = RoundedCornerShape(LyricsHeaderArtCorner)
private val ChromeButtonSize = 40.dp

/**
 * The song now playing, at the top: its cover, title and artist, with the
 * lyrics options on the right. The cover and title lead back to the player.
 */
@Composable
internal fun LyricsHeader(
    song: Song?,
    ink: Color,
    dark: Boolean,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Starts choosing lines for a share card, or stops; null hides the button. */
    onShareClick: (() -> Unit)? = null,
    picking: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = song,
            contentKey = { it?.id },
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            modifier = Modifier.weight(1f),
            label = "lyricsHeaderSong",
        ) { shown ->
            if (shown == null) {
                Spacer(Modifier.height(HeaderArtSize))
                return@AnimatedContent
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        onClickLabel = stringResource(R.string.lyrics_return_to_player),
                        role = Role.Button,
                        onClick = onOpenPlayer,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmartImage(
                    model = shown.albumArtUriString ?: R.drawable.rounded_album_24,
                    shape = HeaderArtShape,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .lyricsMorphHeader(MorphPart.ART)
                        .size(HeaderArtSize)
                        .clip(HeaderArtShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = shown.title,
                        color = ink,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.lyricsMorphHeader(MorphPart.TITLE),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shown.displayArtist,
                            color = ink.copy(alpha = 0.62f),
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .lyricsMorphHeader(MorphPart.ARTIST),
                        )
                        PlayingEqIcon(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(width = 13.dp, height = 11.dp),
                            color = ink.copy(alpha = 0.62f),
                            isPlaying = isPlaying,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
        if (onShareClick != null) {
            LyricsGlassButton(
                icon = if (picking) MeloIcons.Close else MeloIcons.Share,
                contentDescription = stringResource(if (picking) R.string.common_cancel else R.string.lyrics_share_button),
                ink = ink,
                dark = dark,
                onClick = onShareClick,
            )
            Spacer(Modifier.width(10.dp))
        }
        LyricsGlassButton(
            icon = MeloIcons.MoreHoriz,
            contentDescription = stringResource(R.string.lyrics_options),
            ink = ink,
            dark = dark,
            onClick = onMoreClick,
        )
    }
}

/** A round glass button, the same glass as the player's own top buttons. */
@Composable
internal fun LyricsGlassButton(
    icon: ImageVector,
    contentDescription: String,
    ink: Color,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ChromeButtonSize,
    extraScale: () -> Float = { 1f },
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lyricsGlassPress",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = pressScale * extraScale()
                scaleX = s
                scaleY = s
            }
            .size(size)
            .playerDockGlass(CircleShape, dark)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * A hairline position bar with the elapsed time under its start and the time
 * left under its end. While a finger is on it the lyrics follow the finger
 * ([onSeekPreviewChange]); the seek itself happens on release.
 */
@Composable
internal fun LyricsScrubber(
    playbackPositionFlow: StateFlow<Long>,
    totalDuration: Long,
    ink: Color,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val position by playbackPositionFlow.collectAsStateWithLifecycle()
    val liveFraction = if (totalDuration > 0) (position.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // Where a seek was let go, held until playback catches up with it so the
    // bar doesn't flick back to the old position for a moment.
    var heldFraction by remember { mutableStateOf<Float?>(null) }
    val lastTick = remember { intArrayOf(-1) }

    LaunchedEffect(liveFraction, heldFraction) {
        val held = heldFraction ?: return@LaunchedEffect
        if (abs(held - liveFraction) < 0.01f) heldFraction = null
    }
    LaunchedEffect(heldFraction) {
        if (heldFraction != null) {
            delay(4_000)
            heldFraction = null
        }
    }

    val shown = dragFraction ?: heldFraction ?: liveFraction
    val shownState = rememberUpdatedState(shown)
    val shownMs = (shown * totalDuration.coerceAtLeast(0L)).roundToLong()

    Column(modifier = modifier.fillMaxWidth()) {
        MeloScrubber(
            value = { shownState.value },
            onValueChange = { fraction ->
                val f = fraction.coerceIn(0f, 1f)
                val step = (f * 20f).toInt()
                if (step != lastTick[0]) {
                    lastTick[0] = step
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                dragFraction = f
                onSeekPreviewChange((f * totalDuration).roundToLong())
            },
            onValueCommit = { fraction ->
                val f = fraction.coerceIn(0f, 1f)
                dragFraction = null
                heldFraction = f
                onSeekPreviewChange(null)
                onSeekTo((f * totalDuration).roundToLong())
            },
            activeTrackColor = ink.copy(alpha = 0.9f),
            inactiveTrackColor = ink.copy(alpha = 0.22f),
            isPlaying = isPlaying,
            semanticsLabel = stringResource(R.string.lyrics_playback_position),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val timeStyle = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            )
            Text(
                text = formatDuration(shownMs),
                style = timeStyle,
                color = ink.copy(alpha = 0.55f),
            )
            Text(
                text = "-" + formatDuration((totalDuration - shownMs).coerceAtLeast(0L)),
                style = timeStyle,
                color = ink.copy(alpha = 0.55f),
            )
        }
    }
}

/** Previous, play / pause and next as bare glyphs, the way the system player draws them. */
@Composable
internal fun LyricsTransport(
    showPause: Boolean,
    isBuffering: Boolean,
    ink: Color,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(
            ink = ink,
            size = 60.dp,
            contentDescription = stringResource(R.string.melo_previous),
            onClick = onPrevious,
        ) {
            Icon(MeloIcons.SkipPrevious, contentDescription = null, tint = ink, modifier = Modifier.size(30.dp))
        }
        TransportGlyph(
            ink = ink,
            size = 76.dp,
            contentDescription = stringResource(if (showPause) R.string.melo_pause else R.string.common_play),
            onClick = onPlayPause,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = ink,
                    strokeWidth = 3.dp,
                )
            } else {
                AnimatedContent(
                    targetState = showPause,
                    transitionSpec = {
                        (fadeIn(tween(160)) + androidx.compose.animation.scaleIn(initialScale = 0.6f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(120)) + androidx.compose.animation.scaleOut(targetScale = 0.6f, animationSpec = tween(160)))
                    },
                    label = "lyricsPlayPause",
                ) { pause ->
                    Icon(
                        imageVector = if (pause) MeloIcons.Pause else MeloIcons.PlayArrow,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        TransportGlyph(
            ink = ink,
            size = 60.dp,
            contentDescription = stringResource(R.string.melo_next),
            onClick = onNext,
        ) {
            Icon(MeloIcons.SkipNext, contentDescription = null, tint = ink, modifier = Modifier.size(30.dp))
        }
    }
}

/** A glyph with no button around it; a soft disc comes up behind it while pressed. */
@Composable
private fun TransportGlyph(
    ink: Color,
    size: Dp,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "transportPress",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val s = lerp(1f, 0.86f, press)
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (press > 0f) drawCircle(ink.copy(alpha = 0.14f * press))
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
