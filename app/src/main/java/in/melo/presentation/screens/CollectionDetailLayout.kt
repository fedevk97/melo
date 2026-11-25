package `in`.melo.presentation.screens

import `in`.melo.ui.glass.backdrop.backdrops.layerBackdrop
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.size.Size
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.navigation.NavHostController
import `in`.melo.data.Music
import `in`.melo.data.model.toAlbum
import `in`.melo.presentation.components.ExpressiveScrollBar
import `in`.melo.presentation.components.MiniPlayerHeight
import `in`.melo.ui.theme.LocalMeloDarkTheme
import `in`.melo.ui.theme.LocalShowScrollbar
import `in`.melo.presentation.viewmodel.PlayerViewModel


import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CollectionDetailLayout(
    item: Music, count: Int, ui: PlayerViewModel, navController: NavHostController,
    lazyListState: LazyListState,
    /** Items above the visible start of the list, revealed by pulling down once the header is open. */
    hiddenLeadingItems: Int = 0,
    content: LazyListScope.() -> Unit,
) {
    val isDarkTheme = LocalMeloDarkTheme.current
    val settings by ui.settings.collectAsStateWithLifecycle()
    val palette = rememberPagePalette(item.imageUrl.takeIf { settings.detailPageTint }, isDarkTheme)
    val base = if (settings.detailPageTint) MaterialTheme.colorScheme.tintedBy(palette) else MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val hasSong by ui.derivedHasSong.collectAsStateWithLifecycle()
    val fabBottomPadding by animateDpAsState(if (hasSong) MiniPlayerHeight + 16.dp else 16.dp, label = "detailBottom")
    MaterialTheme(colorScheme = base) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val minTopBarHeight = 64.dp + statusBarHeight
        // The full-screen cover shows the whole square, the page's width tall; on a screen too
        // wide for that to leave room for the list, the cropped banner stays.
        val fullScreenCover = settings.fullScreenDetailCover &&
            configuration.screenWidthDp <= configuration.screenHeightDp * 0.62f
        val maxTopBarHeight = if (fullScreenCover) configuration.screenWidthDp.dp else 300.dp

        val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
        val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
        val headerImageRequestSize = remember(
            configuration.screenWidthDp,
            density.density,
            maxTopBarHeightPx
        ) {
            Size(
                width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                height = maxTopBarHeightPx.roundToInt()
            )
        }

        val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
        val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
            derivedStateOf {
                1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(
                    0f,
                    1f
                )
            }
        }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val delta = available.y
                    val isScrollingDown = delta < 0

                    if (!isScrollingDown && !lazyListState.atCollectionTop(hiddenLeadingItems)) {
                        return Offset.Zero
                    }

                    val previousHeight = topBarHeight.value
                    val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                    val consumed = newHeight - previousHeight

                    if (consumed.roundToInt() != 0) {
                        coroutineScope.launch {
                            topBarHeight.snapTo(newHeight)
                        }
                    }

                    val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                    return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    return super.onPostFling(consumed, available)
                }
            }
        }

        LaunchedEffect(lazyListState.isScrollInProgress) {
            if (!lazyListState.isScrollInProgress) {
                val shouldExpand =
                    topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
                val canExpand = lazyListState.atCollectionTop(hiddenLeadingItems)

                val targetValue = if (shouldExpand && canExpand) {
                    maxTopBarHeightPx
                } else {
                    minTopBarHeightPx
                }

                if (topBarHeight.value != targetValue) {
                    coroutineScope.launch {
                        topBarHeight.animateTo(
                            targetValue,
                            spring(stiffness = Spring.StiffnessMedium)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .nestedScroll(nestedScrollConnection)
        ) {
            val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
            val showScrollBar =
                LocalShowScrollbar.current &&
                collapseFraction > 0.95f &&
                    (lazyListState.canScrollForward || lazyListState.canScrollBackward)

            val listBackdrop = rememberPageBackdrop(blurAllowed = !settings.disableBlur)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (listBackdrop != null) Modifier.layerBackdrop(listBackdrop) else Modifier)
                    .offset {
                        val extraHeight =
                            (topBarHeight.value - minTopBarHeightPx).roundToInt()
                        IntOffset(0, extraHeight)
                    },
                contentPadding = PaddingValues(
                    top = minTopBarHeight + 8.dp,
                    // Song rows carry their own 16dp inset, so their artwork lines up with the
                    // title and back button; header items add the same gutter themselves.
                    start = 0.dp,
                    end = if (showScrollBar) 8.dp else 0.dp,
                    bottom = fabBottomPadding + 80.dp // To account for FAB
                ),
                verticalArrangement = Arrangement.spacedBy(`in`.melo.presentation.components.listSpacing()),
                content = content,
            )

            if (showScrollBar) {
                ExpressiveScrollBar(
                    listState = lazyListState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = minTopBarHeight + 12.dp,
                            bottom = fabBottomPadding + 80.dp
                        )
                )
            }

            CollapsingAlbumTopBar(
                album = item.toAlbum(),
                songsCount = count,
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                headerImageRequestSize = headerImageRequestSize,
                artTopLuminance = palette.topLuminance,
                listBackdrop = listBackdrop,
                onBackPressed = { navController.popBackStack() },
            )
                }
    }
}

/** At the start of the list, hidden leading items (shown or not) excepted. */
private fun LazyListState.atCollectionTop(hidden: Int): Boolean =
    firstVisibleItemIndex < hidden || (firstVisibleItemIndex == hidden && firstVisibleItemScrollOffset == 0)
