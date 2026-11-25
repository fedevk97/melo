package `in`.melo.presentation.components

import `in`.melo.R

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import `in`.melo.ui.glass.meloGlass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import `in`.melo.data.model.Song
import `in`.melo.presentation.components.scoped.PlayerAlbumNavigationEffect
import `in`.melo.presentation.components.scoped.PlayerArtistNavigationEffect
import `in`.melo.presentation.components.scoped.PlayerSheetPredictiveBackHandler
import `in`.melo.presentation.components.scoped.QueueSheetRuntimeEffects
import `in`.melo.presentation.components.scoped.SheetMotionController
import `in`.melo.presentation.components.scoped.miniPlayerDismissHorizontalGesture
import `in`.melo.presentation.components.FloatingNavBarCornerRadius
import `in`.melo.presentation.components.scoped.playerSheetVerticalDragGesture
import `in`.melo.presentation.components.scoped.rememberFullPlayerCompositionPolicy
import `in`.melo.presentation.components.scoped.rememberFullPlayerVisualState
import `in`.melo.presentation.components.scoped.rememberMiniPlayerDismissGestureHandler
import `in`.melo.presentation.components.scoped.rememberPrewarmFullPlayer
import `in`.melo.presentation.components.scoped.rememberQueueSheetState
import `in`.melo.presentation.components.scoped.rememberSheetActionHandlers
import `in`.melo.presentation.components.scoped.rememberSheetBackAndDragState
import `in`.melo.presentation.components.scoped.rememberSheetInteractionState
import `in`.melo.presentation.components.scoped.rememberSheetModalOverlayController
import `in`.melo.presentation.components.scoped.rememberSheetOverlayState
import `in`.melo.presentation.components.scoped.rememberSheetThemeState
import `in`.melo.presentation.components.scoped.rememberSheetVisualState
import `in`.melo.presentation.viewmodel.PlayerSheetState
import `in`.melo.presentation.viewmodel.PlayerViewModel
import `in`.melo.presentation.viewmodel.StablePlayerState
import `in`.melo.ui.theme.LocalMeloDarkTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PlayerUiSheetSliceV2(
    val currentQueueSourceName: String = "",
    val preparingSongId: String? = null
)

/**
 * V2 real host: no longer delegates to the legacy `UnifiedPlayerSheet`.
 *
 * This path keeps behavior parity, but now owns its own runtime wiring so we can
 * profile and optimize V2 independently while preserving the Experimental switch.
 */
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnifiedPlayerSheetV2(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    navController: NavHostController,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)
    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                playerViewModel.toastEvents.collect { message ->
                    Toast.makeText(latestContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val infrequentPlayerStateReference = playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val infrequentPlayerState = infrequentPlayerStateReference.value
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentPositionState = playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val positionToDisplayProvider = remember { { currentPositionState.value } }

    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()

    val playerUiSheetSlice by remember {
        playerViewModel.playerUiState
            .map { state ->
                PlayerUiSheetSliceV2(
                    currentQueueSourceName = state.currentQueueSourceName,
                    preparingSongId = state.preparingSongId
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerUiSheetSliceV2())
    val currentQueueSourceName = playerUiSheetSlice.currentQueueSourceName
    val preparingSongId = playerUiSheetSlice.preparingSongId

    val currentSheetContentState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
    val predictiveBackSwipeEdge by playerViewModel.predictiveBackSwipeEdge.collectAsStateWithLifecycle()
    val prewarmFullPlayer = rememberPrewarmFullPlayer(infrequentPlayerState.currentSong?.id)

    val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
    val navBarStyle = playerConfig.navBarStyle
    val carouselStyle = playerConfig.carouselStyle
    val fullPlayerLoadingTweaks = playerConfig.fullPlayerLoadingTweaks
    val tapBackgroundClosesPlayer = playerConfig.tapBackgroundClosesPlayer
    val useSmoothCorners = playerConfig.useSmoothCorners
    val playerThemePreference = playerConfig.playerThemePreference

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }
    val swipeDismissProgress by remember(dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting = false
    val showPlayerContentArea by remember(infrequentPlayerState.currentSong) {
        derivedStateOf { infrequentPlayerState.currentSong != null }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val isPlayerFullyExpanded by remember(playerContentExpansionFraction, currentSheetContentState) {
        derivedStateOf {
            playerContentExpansionFraction.value >= 0.99f &&
                currentSheetContentState == PlayerSheetState.EXPANDED
        }
    }
    val visualOvershootScaleY = remember { Animatable(1f) }
    // The full player no longer slides in under the panel: it rides the
    // panel's top edge, so the panel and what is on it move as one.
    val initialFullPlayerOffsetY = 0f
    val reducedMotion = `in`.melo.presentation.utils.rememberReducedMotion()
    val sheetAnimationSpec = remember(reducedMotion) {
        if (reducedMotion) androidx.compose.animation.core.snap<Float>()
        // The player spring: critically damped, stiffness 195, carrying the finger's
        // speed, so the sheet settles without a timed curve's start and stop or any bounce.
        else androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 195f)
    }
    val sheetAnimationMutex = remember { MutatorMutex() }
    val sheetExpandedTargetY = 0f
    val initialY =
        if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY
        else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }
    val sheetMotionController = remember(
        currentSheetTranslationY,
        playerContentExpansionFraction,
        sheetAnimationMutex,
        sheetAnimationSpec
    ) {
        SheetMotionController(
            translationY = currentSheetTranslationY,
            expansionFraction = playerContentExpansionFraction,
            mutex = sheetAnimationMutex,
            defaultAnimationSpec = sheetAnimationSpec,
            expandedY = sheetExpandedTargetY
        )
    }

    PlayerArtistNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )
    PlayerAlbumNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )

    // FullPlayerVisualState now holds lazy getters that read from the Animatable
    // inside graphicsLayer (draw-phase), avoiding per-frame recomposition.
    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = playerContentExpansionFraction,
        initialOffsetY = initialFullPlayerOffsetY
    )
    val fullPlayerCompositionPolicy = rememberFullPlayerCompositionPolicy(
        currentSongId = infrequentPlayerState.currentSong?.id,
        currentSheetState = currentSheetContentState,
        expansionFraction = playerContentExpansionFraction
    )
    val shouldRenderFullPlayer = fullPlayerCompositionPolicy.shouldRenderFullPlayer

    // Battery: tell the PlaybackStateHolder when the slider-bearing UI is
    // actually rendered. When it isn't (mini-player only), the position
    // ticker drops from 250 ms to 1 s — slider precision isn't needed.
    DisposableEffect(shouldRenderFullPlayer) {
        playerViewModel.setSliderUiMounted(shouldRenderFullPlayer)
        onDispose { playerViewModel.setSliderUiMounted(false) }
    }

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        sheetMotionController.animateTo(
            targetExpanded = targetExpanded,
            canExpand = showPlayerContentArea,
            collapsedY = sheetCollapsedTargetY,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity
        )
    }

    val sheetTargetExpanded by rememberUpdatedState(
        showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
    )
    LaunchedEffect(sheetCollapsedTargetY, sheetMotionController) {
        // Keep the mini player anchored to the latest collapsed target whenever
        // the navbar height/visibility changes under it. The tab bar hides as the player opens,
        // so the anchor moves several times during an open: the animation is carried on to the
        // new anchor in the sheet's own scope, since this effect (and anything it runs) is
        // cancelled at the next move, which used to leave the player stopped short of the top.
        if (sheetMotionController.isAnimating) {
            scope.launch { animatePlayerSheet(targetExpanded = sheetTargetExpanded) }
        } else {
            sheetMotionController.syncToExpansion(sheetCollapsedTargetY)
        }
    }

    LaunchedEffect(showPlayerContentArea, currentSheetContentState, reducedMotion) {
        visualOvershootScaleY.snapTo(1f)
        animatePlayerSheet(
            targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        )
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = showPlayerContentArea,
        collapsedStateHorizontalPadding = collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress = predictiveBackCollapseProgress,
        predictiveBackSwipeEdge = predictiveBackSwipeEdge,
        currentSheetContentState = currentSheetContentState,
        playerContentExpansionFraction = playerContentExpansionFraction,
        containerHeight = containerHeight,
        currentSheetTranslationY = currentSheetTranslationY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        navBarStyle = navBarStyle,
        floatingNavBarCornerRadiusDp = FloatingNavBarCornerRadius,
        isNavBarHidden = isNavBarHidden,
        isPlaying = infrequentPlayerState.isPlaying,
        hasCurrentSong = infrequentPlayerState.currentSong != null,
        swipeDismissProgress = swipeDismissProgress
    )
    val currentBottomPadding = sheetVisualState.currentBottomPadding
    val baseBottomPadding = sheetVisualState.baseBottomPadding
    val playerContentAreaHeightPxProvider = sheetVisualState.playerContentAreaHeightPxProvider
    val visualSheetTranslationYProvider = sheetVisualState.visualSheetTranslationYProvider
    val overallSheetTopCornerRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider
    val playerContentActualBottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider
    val currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider
    val currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider

    val queueSheetState = rememberQueueSheetState(
        scope = scope,
        screenHeightPx = screenHeightPx,
        density = density,
        currentBottomPadding = currentBottomPadding,
        showPlayerContentArea = showPlayerContentArea,
        isPlayerFullyExpanded = isPlayerFullyExpanded
    )
    val showQueueSheet = queueSheetState.showQueueSheet
    val allowQueueSheetInteraction = queueSheetState.allowQueueSheetInteraction
    val queueSheetOffset = queueSheetState.queueSheetOffset
    val queueSheetHeightPx = queueSheetState.queueSheetHeightPx
    val queueHiddenOffsetPx = queueSheetState.queueHiddenOffsetPx
    val queueSheetController = queueSheetState.queueSheetController
    val onQueueSheetHeightPxChange = queueSheetState.onQueueSheetHeightPxChange

    val sheetBackAndDragState = rememberSheetBackAndDragState(
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val canHandlePlayerBack by remember(
        sheetBackAndDragState.predictiveBackEnabled,
        showQueueSheet
    ) {
        derivedStateOf { sheetBackAndDragState.predictiveBackEnabled && !showQueueSheet }
    }
    val velocityTracker = remember { VelocityTracker() }
    val sheetModalOverlayController = rememberSheetModalOverlayController(
        scope = scope,
        queueSheetController = queueSheetController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onCollapsePlayerSheet = { playerViewModel.collapsePlayerSheet() }
    )
    val selectedSongForInfo = sheetModalOverlayController.selectedSongForInfo
    val sheetActionHandlers = rememberSheetActionHandlers(
        scope = scope,
        navController = navController,
        playerViewModel = playerViewModel,
        sheetMotionController = sheetMotionController,
        queueSheetController = queueSheetController,
        sheetModalOverlayController = sheetModalOverlayController,
        sheetCollapsedTargetY = sheetCollapsedTargetY
    )

    val hapticFeedback = LocalHapticFeedback.current
    val swipeSettings by playerViewModel.settings.collectAsStateWithLifecycle()
    val miniSwipe = swipeSettings.miniPlayerSwipe
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() },
        onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) },
        swipeChangesTrack = miniSwipe == `in`.melo.data.preferences.AppSettings.MINI_SWIPE_TRACKS,
        onSwipeTrack = { next -> if (next) playerViewModel.nextSong() else playerViewModel.previousTrack() },
    )

    QueueSheetRuntimeEffects(
        queueSheetController = queueSheetController,
        queueSheetOffset = queueSheetOffset,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        onTopEdgeReached = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    PlayerSheetPredictiveBackHandler(
        enabled = canHandlePlayerBack,
        playerViewModel = playerViewModel,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        sheetMotionController = sheetMotionController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onSwipeEdgeChanged = { playerViewModel.updatePredictiveBackSwipeEdge(it) },
        registrationKey = currentBackStackEntry?.id
    )

    val queuePredictiveBackProgress = remember { Animatable(0f) }
    var queuePredictiveBackSwipeEdge by remember { mutableStateOf<Int?>(null) }

    val sheetOverlayState = rememberSheetOverlayState(
        density = density,
        showPlayerContentArea = showPlayerContentArea,
        hideMiniPlayer = hideMiniPlayer,
        showQueueSheet = showQueueSheet,
        isQueueCollapsing = queueSheetState.isCollapsing,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        screenHeightPx = screenHeightPx,
        castSheetOpenFraction = 0f,
        queueSheetOffset = queueSheetOffset,
        queuePredictiveBackProgress = queuePredictiveBackProgress
    )
    val internalIsKeyboardVisible = sheetOverlayState.internalIsKeyboardVisible
    val actuallyShowSheetContent = sheetOverlayState.actuallyShowSheetContent
    val isQueueVisible = sheetOverlayState.isQueueVisible
    val bottomSheetOpenFraction = sheetOverlayState.bottomSheetOpenFraction
    val queueScrimAlpha = sheetOverlayState.queueScrimAlpha
    val shouldRenderQueueHost by remember(internalIsKeyboardVisible, selectedSongForInfo) {
        derivedStateOf {
            !internalIsKeyboardVisible || selectedSongForInfo != null
        }
    }
    val isQueueTelemetryActive = showQueueSheet

    LaunchedEffect(showQueueSheet) {
        playerViewModel.updateQueueSheetVisibility(showQueueSheet)
    }
    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.updateQueueSheetVisibility(false)
        }
    }

    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val isDarkTheme = LocalMeloDarkTheme.current
    val currentSong = infrequentPlayerState.currentSong
    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = preparingSongId,
        systemColorScheme = MaterialTheme.colorScheme
    )
    val albumColorScheme = sheetThemeState.albumColorScheme
    val miniPlayerScheme = sheetThemeState.miniPlayerScheme
    val isPreparingPlayback = sheetThemeState.isPreparingPlayback
    val miniReadyAlpha = sheetThemeState.miniReadyAlpha
    val miniAppearScale = sheetThemeState.miniAppearScale
    val playerAreaBackground = sheetThemeState.playerAreaBackground
    val miniCardColor = miniPlayerColor()
    // The pages are recorded (Glass or Frost): the collapsed card is the same material as the
    // tab bar, and fills in with the player colour as it opens.
    val miniGlass = `in`.melo.ui.glass.LocalAppBackdrop.current != null
    val miniCardHairline = if (miniGlass) `in`.melo.ui.glass.surfaceEdgeColor(miniCardColor.luminance() < 0.5f)
        else MaterialTheme.colorScheme.outlineVariant
    // Elevation is only visible in the mini/collapsed state (expansion < 0.18).
    // miniReadyAlpha fades the shadow in during the initial song-appear animation.
    val isDragging = sheetBackAndDragState.isDragging
    // The player's own fill covers the mini card's glass early in the open:
    // held for the first few percent, then opaque by about a quarter of the way. Left
    // to fade in over the whole travel, the glass (its bent top band and its rim) showed through
    // the growing player and read as a second edge across its top.
    val pillUntil = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.12f else 0.2f
    fun playerFill(open: Float) = ((open - 0.06f) / pillUntil).coerceIn(0f, 1f)
    val playerFullyOpen by remember(pillUntil) {
        derivedStateOf { playerFill(playerContentExpansionFraction.value) >= 1f }
    }
    val visualCardShadowElevation by remember(showQueueSheet, miniReadyAlpha, isDragging) {
        derivedStateOf {
            if (
                showQueueSheet ||
                isDragging ||
                playerContentExpansionFraction.isRunning ||
                playerContentExpansionFraction.value > 0.18f
            ) {
                0.dp
            } else {
                (3f * miniReadyAlpha).dp
            }
        }
    }

    val sheetInteractionState = rememberSheetInteractionState(
        scope = scope,
        velocityTracker = velocityTracker,
        sheetMotionController = sheetMotionController,
        playerContentExpansionFraction = playerContentExpansionFraction,
        currentSheetTranslationY = currentSheetTranslationY,
        visualOvershootScaleY = visualOvershootScaleY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        miniPlayerContentHeightPx = miniPlayerContentHeightPx,
        currentSheetContentState = currentSheetContentState,
        showPlayerContentArea = showPlayerContentArea,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        useSmoothCorners = useSmoothCorners,
        isDragging = sheetBackAndDragState.isDragging,
        onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
            if (animationSpec == null) {
                animatePlayerSheet(targetExpanded = targetExpanded, initialVelocity = initialVelocity)
            } else {
                animatePlayerSheet(
                    targetExpanded = targetExpanded,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity
                )
            }
        },
        onExpandSheetState = { playerViewModel.expandPlayerSheet() },
        onCollapseSheetState = { playerViewModel.collapsePlayerSheet() },
        onDraggingChange = sheetBackAndDragState.onDraggingChange,
        onDraggingPlayerAreaChange = sheetBackAndDragState.onDraggingPlayerAreaChange
    )

    if (!actuallyShowSheetContent) return

    val playerSheetSemanticsDescription = androidx.compose.ui.res.stringResource(
        R.string.melo_player_description, infrequentPlayerState.currentSong?.title.orEmpty()
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val translationY = visualSheetTranslationYProvider().roundToInt()
                val overshoot = if (currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging) {
                    -translationY
                } else {
                    if (translationY < 0) -translationY else 0
                }
                val targetHeight = constraints.maxHeight + overshoot
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = targetHeight,
                        maxHeight = targetHeight
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, translationY)
                }
            },
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = currentBottomPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = offsetAnimatable.value
                                scaleX = miniAppearScale
                                scaleY = visualOvershootScaleY.value * miniAppearScale
                                alpha = miniReadyAlpha
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            // outerLayout:
                            // Measures downstream chain with innerWidth and targetHeightPx.
                            // Places child at startPaddingPx to center it horizontally.
                            // Reports full screen width to parent to satisfy fillMaxWidth() constraints.
                            .layout { measurable, constraints ->
                                val targetHeightPx = playerContentAreaHeightPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val endPaddingPx = currentHorizontalPaddingEndPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    .coerceAtLeast(0)
                                
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = innerWidth,
                                        maxWidth = innerWidth,
                                        minHeight = targetHeightPx,
                                        maxHeight = targetHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, targetHeightPx) {
                                    placeable.placeRelative(startPaddingPx, 0)
                                }
                            }
                            // Always apply Modifier.shadow with the dynamic elevation
                            // (0.dp renders nothing). Keeping the modifier chain
                            // structurally stable avoids the costly relayout/redraw
                            // restructure when the elevation crosses 0.dp during
                            // expand/collapse or right after play/pause.
                            .shadow(
                                elevation = visualCardShadowElevation,
                                shape = sheetInteractionState.playerShadowShape,
                                clip = false
                            )
                            // The glass and its rim belong to the mini card; once the player's fill has
                            // covered it they go, so no band or line is left along the player's edges.
                            .then(
                                if (miniGlass && !playerFullyOpen) Modifier.meloGlass(sheetInteractionState.playerShadowShape)
                                else Modifier
                            )
                            .drawBehind {
                                val open = playerFill(playerContentExpansionFraction.value)
                                val outline = sheetInteractionState.playerShadowShape
                                    .createOutline(size, layoutDirection, this)
                                if (miniGlass) drawOutline(outline, playerAreaBackground, alpha = open)
                                else drawOutline(outline, androidx.compose.ui.graphics.lerp(miniCardColor, playerAreaBackground, open))
                                if (open < 1f) drawOutline(
                                    outline, miniCardHairline, alpha = 1f - open,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(`in`.melo.ui.theme.MeloLayout.Hairline.toPx()),
                                )
                            }
                            .clip(sheetInteractionState.playerShadowShape)
                            // innerLayout:
                            // Measures the actual player content with full screen height targetContentHeightPx
                            // so that it can render correctly, while reporting targetHeightPx to the outer
                            // clip/background/shadow so that they are perfectly constrained to the miniplayer card bounds.
                            // During drag/animation, we measure at stable full-screen constraints to prevent jank.
                            .layout { measurable, constraints ->
                                val targetContentHeightPx = containerHeight.roundToPx()
                                val fraction = playerContentExpansionFraction.value
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider().toInt()
                                val measureWidth = if (fraction > 0f) {
                                    screenWidthPx.roundToInt()
                                } else {
                                    constraints.maxWidth
                                }
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = measureWidth,
                                        maxWidth = measureWidth,
                                        minHeight = targetContentHeightPx,
                                        maxHeight = targetContentHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    val xOffset = if (fraction > 0f) -startPaddingPx else 0
                                    placeable.placeRelative(xOffset, 0)
                                }
                            }
                            .miniPlayerDismissHorizontalGesture(
                                enabled = currentSheetContentState == PlayerSheetState.COLLAPSED &&
                                    miniSwipe != `in`.melo.data.preferences.AppSettings.MINI_SWIPE_OFF,
                                handler = miniDismissGestureHandler
                            )
                            .playerSheetVerticalDragGesture(
                                enabled = sheetInteractionState.canDragSheet,
                                handler = sheetInteractionState.sheetVerticalDragGestureHandler
                            )
                            .clickable(
                                enabled = tapBackgroundClosesPlayer || currentSheetContentState == PlayerSheetState.COLLAPSED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playerViewModel.togglePlayerSheetState()
                            }
                            .semantics {
                                contentDescription = playerSheetSemanticsDescription
                            }
                    ) {
                        UnifiedPlayerMiniAndFullLayers(
                            currentSong = infrequentPlayerState.currentSong,
                            miniPlayerScheme = miniPlayerScheme,
                            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
                            infrequentPlayerState = infrequentPlayerState,
                            isCastConnecting = isCastConnecting,
                            isPreparingPlayback = isPreparingPlayback,
                            playerContentExpansionFraction = playerContentExpansionFraction,
                            albumColorScheme = albumColorScheme,
                            bottomSheetOpenFraction = bottomSheetOpenFraction,
                            fullPlayerVisualState = fullPlayerVisualState,
                            containerHeight = containerHeight,
                            currentQueueSourceName = currentQueueSourceName,
                            currentSheetContentState = currentSheetContentState,
                            carouselStyle = carouselStyle,
                            fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                            isSheetDragGestureActive = sheetBackAndDragState.isDraggingPlayerArea,
                            playerViewModel = playerViewModel,
                            currentPositionProvider = positionToDisplayProvider,
                            isFavorite = isFavorite,
                            shouldRenderFullPlayer = shouldRenderFullPlayer,
                            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
                            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider,
                            onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                    onShowSongOptions = { song -> sheetActionHandlers.onSelectedSongForInfoChange(song) },
                            onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                            onQueueDrag = sheetActionHandlers.dragQueueBy,
                            onQueueRelease = sheetActionHandlers.endQueueDrag
                        )
                    }
                }

                UnifiedPlayerPrewarmLayer(
                    prewarmFullPlayer = prewarmFullPlayer && !shouldRenderFullPlayer,
                    currentSong = infrequentPlayerState.currentSong,
                    containerHeight = containerHeight,
                    albumColorScheme = albumColorScheme,
                    currentQueueSourceName = currentQueueSourceName,
                    infrequentPlayerState = infrequentPlayerState,
                    carouselStyle = carouselStyle,
                    fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = positionToDisplayProvider,
                    isCastConnecting = isCastConnecting,
                    isFavorite = isFavorite,
                    onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                    onShowSongOptions = { song -> sheetActionHandlers.onSelectedSongForInfoChange(song) },
                    onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                    onQueueDrag = sheetActionHandlers.dragQueueBy,
                    onQueueRelease = sheetActionHandlers.endQueueDrag
                )
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PredictiveBackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) { progressFlow ->
                    try {
                        progressFlow.collect { backEvent ->
                            queuePredictiveBackSwipeEdge = backEvent.swipeEdge
                            queuePredictiveBackProgress.snapTo(backEvent.progress)
                        }
                        scope.launch {
                            launch {
                                sheetActionHandlers.animateQueueSheet(false)
                            }
                            launch {
                                queuePredictiveBackProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(ANIMATION_DURATION_MS)
                                )
                                queuePredictiveBackSwipeEdge = null
                            }
                        }
                    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                        scope.launch {
                            queuePredictiveBackProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(ANIMATION_DURATION_MS)
                            )
                            queuePredictiveBackSwipeEdge = null
                        }
                    }
                }
            } else {
                BackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) {
                    sheetActionHandlers.animateQueueSheet(false)
                }
            }

            val queuePredictiveBackSwipeEdgeState = rememberUpdatedState(queuePredictiveBackSwipeEdge)

            UnifiedPlayerQueueAndSongInfoHost(
                shouldRenderHost = shouldRenderQueueHost,
                keepQueueSheetWarm = currentSheetContentState == PlayerSheetState.EXPANDED &&
                    !internalIsKeyboardVisible,
                isQueueTelemetryActive = isQueueTelemetryActive,
                albumColorScheme = albumColorScheme,
                queueScrimAlpha = queueScrimAlpha,
                showQueueSheet = showQueueSheet,
                isQueueCollapsing = queueSheetState.isCollapsing,
                queueHiddenOffsetPx = queueHiddenOffsetPx,
                queueSheetOffset = queueSheetOffset,
                queueSheetHeightPx = queueSheetHeightPx,
                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                configurationResetKey = configuration,
                currentQueueSourceName = currentQueueSourceName,
                infrequentPlayerState = infrequentPlayerState,
                playerViewModel = playerViewModel,
                selectedSongForInfo = selectedSongForInfo,
                onSelectedSongForInfoChange = sheetActionHandlers.onSelectedSongForInfoChange,
                onAnimateQueueSheet = sheetActionHandlers.animateQueueSheet,
                onBeginQueueDrag = sheetActionHandlers.beginQueueDrag,
                onDragQueueBy = sheetActionHandlers.dragQueueBy,
                onEndQueueDrag = sheetActionHandlers.endQueueDrag,
                onNavigateToAlbum = sheetActionHandlers.onNavigateToAlbum,
                onNavigateToArtist = sheetActionHandlers.onNavigateToArtist,
                onStartRadio = sheetActionHandlers.onStartRadio,
                queuePredictiveBackProgress = queuePredictiveBackProgress,
                queuePredictiveBackSwipeEdge = queuePredictiveBackSwipeEdgeState
            )
        }
    }
}
