package `in`.melo.presentation.components.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import `in`.melo.ui.glass.pressShade
import `in`.melo.ui.glass.pressSink
import `in`.melo.ui.glass.rememberPressDent
import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.melo.R
import `in`.melo.presentation.components.LocalMaterialTheme

/** Fixed controls preserve touch targets during rapid skips and respect reduced motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean = { false },
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val buffering = isBufferingProvider()
    val playing = isPlayingProvider() && !buffering
    val bufferingDescription = stringResource(R.string.melo_preparing)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun feedback(action: () -> Unit) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        action()
    }
    Row(modifier.fillMaxWidth().heightIn(min = height),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        val previousDent = rememberPressDent()
        IconButton(onClick = { feedback(onPrevious) }, modifier = Modifier.size(64.dp).pressSink(previousDent),
            interactionSource = previousDent.interactions) {
            Icon(MeloIcons.SkipPrevious, stringResource(R.string.melo_previous),
                Modifier.size(iconSize), tint = LocalMaterialTheme.current.onSurface)
        }
        // Sinks under the finger and its light turns over, like a pushed-in dish.
        val playDent = rememberPressDent()
        FilledIconButton(onClick = { feedback(onPlayPause) },
            modifier = Modifier.size(height.coerceIn(64.dp, 80.dp)).pressSink(playDent),
            interactionSource = playDent.interactions,
            shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colorPlayPause, contentColor = tintPlayPauseIcon)) {
          Box(Modifier.fillMaxSize().pressShade(playDent), contentAlignment = Alignment.Center) {
            if (buffering) {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp).semantics { contentDescription = bufferingDescription },
                    color = tintPlayPauseIcon,
                )
            } else {
                Icon(if (playing) MeloIcons.Pause else MeloIcons.PlayArrow,
                    stringResource(if (playing) R.string.melo_pause else R.string.common_play),
                    Modifier.size(playPauseIconSize))
            }
          }
        }
        val nextDent = rememberPressDent()
        IconButton(onClick = { feedback(onNext) }, modifier = Modifier.size(64.dp).pressSink(nextDent),
            interactionSource = nextDent.interactions) {
            Icon(MeloIcons.SkipNext, stringResource(R.string.melo_next),
                Modifier.size(iconSize), tint = LocalMaterialTheme.current.onSurface)
        }
    }
}
