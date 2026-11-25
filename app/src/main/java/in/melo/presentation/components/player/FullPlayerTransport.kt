package `in`.melo.presentation.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.background
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
import androidx.compose.material3.ColorScheme
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import `in`.melo.R
import `in`.melo.data.model.Song
import `in`.melo.data.preferences.FullPlayerLoadingTweaks
import `in`.melo.presentation.components.LocalMaterialTheme
import `in`.melo.presentation.viewmodel.PlayerSheetState
import kotlinx.collections.immutable.ImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import `in`.melo.presentation.components.ToggleSegmentButton

// The transport: play, pause and skip, the toggle row under them, and the skip prediction for the cover carousel.
// Split out of FullPlayerContent.kt; behaviour unchanged.

@Composable
internal fun FullPlayerControlsSection(
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    transportPlayPauseColors: TransportButtonColors,
    transportSkipColors: TransportButtonColors,
    isShuffleEnabledProvider: () -> Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatModeProvider: () -> Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    /** Extra room under the transport, granted by a tall screen. */
    toggleGap: Dp = 0.dp,
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().height(182.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.42f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(182.dp))
            } else {
                ControlsPlaceholder(placeholderColor, placeholderOnColor)
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlayingProvider = isPlayingProvider,
                isBufferingProvider = isBufferingProvider,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                height = 80.dp,
                colorPlayPause = transportPlayPauseColors.container,
                tintPlayPauseIcon = transportPlayPauseColors.content,
            )

            Spacer(modifier = Modifier.height(14.dp + toggleGap))

            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp, max = 86.dp)
                    .padding(horizontal = 26.dp, vertical = 0.dp)
                    .padding(bottom = 6.dp),
                isShuffleEnabled = isShuffleEnabledProvider(),
                isShuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatMode = repeatModeProvider(),
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }
}

internal fun resolveQueueIndex(
    queue: ImmutableList<Song>,
    songId: String,
    currentMediaItemIndex: Int
): Int? {
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
}

internal fun predictSkipNextCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex < queue.lastIndex -> safeCurrentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }
}

internal fun predictSkipPreviousCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    currentPositionMs: Long,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    if (currentPositionMs > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex > 0 -> safeCurrentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> queue.lastIndex
        else -> null
    }
}

/**
 * The player's dock: a frosted pill that lets the backdrop's
 * colour through, a sheen along its top edge and a hairline rim that is brighter on top.
 * Also the player's top buttons and the lyrics chip, so every control on it is one glass.
 */
internal fun Modifier.playerDockGlass(shape: Shape, dark: Boolean): Modifier {
    val fill = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.70f), Color.White.copy(alpha = if (dark) 0.06f else 0.20f)),
    )
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.08f else 0.18f),
        0.55f to Color.Transparent,
    )
    return this
        .clip(shape)
        .background(fill)
        .background(sheen)
        .border(BorderStroke(0.75.dp, rim), shape)
}

internal data class TransportButtonColors(
    val container: Color,
    val content: Color
)

internal fun expressivePlayPauseButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.tertiaryFixedDim,
        content = colorScheme.onTertiaryFixed
    )
}

internal fun expressiveSkipButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.secondaryFixedDim,
        content = colorScheme.onSecondaryFixed
    )
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    isShuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.onSurface.copy(alpha = 0.07f)
    val inactiveContentColor = LocalMaterialTheme.current.onSurface


    Box(
        modifier = modifier.playerDockGlass(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            ),
            dark = LocalMaterialTheme.current.surface.luminance() < 0.5f,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                enabled = !isShuffleTransitionInProgress,
                activeColor = LocalMaterialTheme.current.primaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onPrimaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = stringResource(R.string.queue_cd_toggle_shuffle_action)
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = LocalMaterialTheme.current.secondaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onSecondaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = stringResource(R.string.queue_cd_toggle_repeat_action)
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = LocalMaterialTheme.current.tertiaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onTertiaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = stringResource(if (isFavorite) R.string.song_info_cd_remove_from_favorites else R.string.song_info_cd_add_to_favorites)
            )
        }
    }
}
