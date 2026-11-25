package `in`.melo.presentation.components.scoped

import `in`.melo.presentation.components.MiniPlayerCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import `in`.melo.data.preferences.NavBarStyle
import `in`.melo.presentation.viewmodel.PlayerSheetState

private const val PREDICTIVE_BACK_SWIPE_EDGE_LEFT = 0
private const val PREDICTIVE_BACK_SWIPE_EDGE_RIGHT = 1

// The panel's shape as it opens. It reaches the
// window's width by 80 % and its bottom by 40 %, easing out, so the opening reads as one
// panel growing rather than a box stretching evenly on every side.
internal fun dialPanelWide(f: Float): Float {
    val x = (f / 0.8f).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

internal fun dialPanelDeep(f: Float): Float {
    val x = (f / 0.4f).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

/** The pill's corner gives way to a 22dp sheet corner, which squares off over the last tenth. */
internal fun dialPanelCorner(pill: Dp, f: Float): Dp {
    val sheet = 22.dp * (f / 0.8f).coerceIn(0f, 1f) * (1f - ((f - 0.9f) / 0.1f).coerceIn(0f, 1f))
    return maxOf(pill * (1f - f), sheet)
}

internal data class SheetVisualState(
    val currentBottomPadding: Dp,
    val baseBottomPadding: Dp,
    /** Draw-phase provider: read this inside graphicsLayer to avoid layout relayout per frame. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Layout-phase provider: read inside .offset { } to avoid recomposition per drag frame. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    /** Draw-phase providers: read inside graphicsLayer to avoid layout relayout per frame. */
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
internal fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    predictiveBackSwipeEdge: Int?,
    currentSheetContentState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    navBarStyle: String,
    floatingNavBarCornerRadiusDp: Dp,
    isNavBarHidden: Boolean,
    isPlaying: Boolean,
    hasCurrentSong: Boolean,
    swipeDismissProgress: Float
): SheetVisualState {
    // Compute in px to be read inside graphicsLayer (draw phase) — zero relayout per drag frame.
    val density = LocalDensity.current
    val baseBottomPadding = remember(containerHeight, sheetCollapsedTargetY, density) {
        val targetYDp = with(density) { sheetCollapsedTargetY.toDp() }
        (containerHeight - `in`.melo.presentation.components.MiniPlayerHeight - targetYDp)
            .coerceAtLeast(0.dp)
    }

    val currentBottomPadding = 0.dp

    val miniHeightPx = remember(density) { with(density) { `in`.melo.presentation.components.MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val baseBottomPaddingPx = remember(baseBottomPadding, density) { with(density) { baseBottomPadding.toPx() } }
    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            currentSheetTranslationY.value * (1f - progress) +
                (sheetCollapsedTargetY * progress)
        }
    }

    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        miniHeightPx,
        containerHeightPx,
        visualSheetTranslationYProvider,
        sheetCollapsedTargetY
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val translationY = visualSheetTranslationYProvider()
                
                if (translationY <= sheetCollapsedTargetY) {
                    val targetBottom = androidx.compose.ui.util.lerp(
                        sheetCollapsedTargetY + miniHeightPx,
                        containerHeightPx,
                        dialPanelDeep(safeFraction)
                    )
                    (targetBottom - translationY).coerceAtLeast(0f)
                } else {
                    androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, dialPanelDeep(safeFraction))
                }
            } else {
                0f
            }
        }
    }

    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        navBarStyle,
        floatingNavBarCornerRadiusDp,
        isNavBarHidden,
        swipeDismissProgress,
        currentSheetContentState
    ) {
        {
            // A rounded rectangle, the same at every corner and with every nav bar style.
            val collapsedCornerTarget = MiniPlayerCorner

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val expandedTarget = 0.dp
            val calculatedNormally = if (showPlayerContentArea) {
                dialPanelCorner(collapsedCornerTarget, safeFraction)
            } else {
                if (navBarStyle == NavBarStyle.DEFAULT) {
                    floatingNavBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    floatingNavBarCornerRadiusDp
                }
            }

            calculatedNormally
        }
    }

    // isPlaying and hasCurrentSong are only used in the fallback branch when
    // !showPlayerContentArea. Reading them via rememberUpdatedState keeps the
    // shape provider lambda stable across play/pause toggles — so the
    // PlayerSheetDynamicShape instance (and the modifier chain that consumes it)
    // is not recreated on every isPlaying flip.
    val isPlayingState = rememberUpdatedState(isPlaying)
    val hasCurrentSongState = rememberUpdatedState(hasCurrentSong)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        swipeDismissProgress,
        isNavBarHidden,
        floatingNavBarCornerRadiusDp,
        currentSheetContentState
    ) {
        {
            val collapsedRadius = MiniPlayerCorner

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val calculatedNormally =
                if (showPlayerContentArea) {
                    dialPanelCorner(collapsedRadius, safeFraction)
                } else {
                    if (!isPlayingState.value || !hasCurrentSongState.value) {
                        if (isNavBarHidden) {
                            32.dp
                        } else if (navBarStyle == NavBarStyle.DEFAULT) {
                            10.dp
                        } else {
                            floatingNavBarCornerRadiusDp
                        }
                    } else {
                        collapsedRadius
                    }
                }

            if (isNavBarHidden) {
                calculatedNormally
            } else if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    calculatedNormally
                } else if (navBarStyle == NavBarStyle.DEFAULT) {
                    MiniPlayerCorner
                } else {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else floatingNavBarCornerRadiusDp
                    lerp(baseCollapsedRadius, floatingNavBarCornerRadiusDp, swipeDismissProgress)
                }
            } else {
                calculatedNormally
            }
        }
    }

    val actualCollapsedStateHorizontalPadding =
        // In line with the nav bar's edges under it.
        if (navBarStyle == NavBarStyle.FULL_WIDTH) 20.dp else collapsedStateHorizontalPadding
    val collapsedStateHorizontalPaddingPx = remember(actualCollapsedStateHorizontalPadding, density) {
        with(density) { actualCollapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase lambda providers for horizontal padding — read inside graphicsLayer to avoid
    // per-frame relayout. The lambda captures Animatable/Float refs and reads them at draw time.
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                collapsedStateHorizontalPaddingPx * (1f - dialPanelWide(safeFraction))
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                collapsedStateHorizontalPaddingPx * (1f - dialPanelWide(safeFraction))
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}
