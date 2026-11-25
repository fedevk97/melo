package `in`.melo.presentation.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.slideIn
import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import `in`.melo.ui.theme.artworkOutline
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import `in`.melo.R
import `in`.melo.data.HomeRecommendationPick
import `in`.melo.data.Music
import `in`.melo.data.YouTubeHomeChip
import `in`.melo.presentation.components.SmartImage
import `in`.melo.presentation.components.MeloIconButton
import `in`.melo.presentation.components.artworkRim
import `in`.melo.presentation.components.homePress
import `in`.melo.presentation.components.LocalHomeGradients
import `in`.melo.presentation.components.moodColors
import `in`.melo.data.HomeMood
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.LocalMeloDarkTheme
import `in`.melo.ui.theme.MeloAlpha
import `in`.melo.presentation.utils.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class HomeSpeedDialEntry(
    val item: Music,
    val title: String = item.title,
    val artworkUrl: String? = item.imageUrl,
    val recommendation: HomeRecommendationPick? = null,
    val isNewRelease: Boolean = false,
)

private const val SPEED_DIAL_COLUMNS = 3
private const val SLOT_BLANK = -1
private const val SLOT_LUCKY = -2
private const val SLOT_HISTORY = -3
internal const val SPEED_DIAL_MAX_ITEMS = 54
/** The speed dial's tiles at most, past the pins. */
internal const val SPEED_DIAL_SIZE = 36
internal const val SPEED_DIAL_MAX_PAGES = 3
/** The page's 16dp gutters, 8dp between tiles, 10dp corners. */
private val SPEED_DIAL_GAP = 8.dp
private val SPEED_DIAL_EDGE = 16.dp
private val SPEED_DIAL_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
/** A short page shrinks the square tiles, to no less than this much of a third of the width. */
private const val SPEED_DIAL_MIN_SCALE = 0.8f
/** The page dots' row under the grid: 10dp over a 16dp row. */
private val SPEED_DIAL_DOTS_HEIGHT = 16.dp

/** The chevron under the dots: 4dp of air and the 22dp glyph. */
private val SPEED_DIAL_HINT_HEIGHT = 26.dp

/** Between the pager's pages: the next page's first column peeks in at the edge. */
private val SPEED_DIAL_PAGE_SPACING = 12.dp

/**
 * How far a dial tile reaches past its cell on each side: the tiles read a touch bigger, with 4dp between them.
 */
private val SPEED_DIAL_TILE_OUTSET = 2.dp

/** Lays the tile out [SPEED_DIAL_TILE_OUTSET] past its cell on every side, without moving the grid. */
private fun Modifier.dialTileOutset(): Modifier = layout { measurable, constraints ->
    val grow = SPEED_DIAL_TILE_OUTSET.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * grow, 2 * grow))
    layout((placeable.width - 2 * grow).coerceAtLeast(0), (placeable.height - 2 * grow).coerceAtLeast(0)) {
        placeable.place(-grow, -grow)
    }
}
/** One size of art for every tile. */
internal val DIAL_ART_SIZE = coil.size.Size(480, 480)

/**
 * A tile's art at one size whichever list the item came
 * from (history gives YouTube art at 120px, the feed at 544px), so a tile never redraws in a
 * sharper copy of the same picture. Spotify art is its largest; YouTube video stills are kept.
 */
internal fun dialArtwork(url: String?): String? {
    val art = `in`.melo.data.largestSpotifyImage(url) ?: return null
    if (!art.contains("googleusercontent.com") && !art.contains("ggpht.com")) return art
    return art.replace(Regex("w\\d+-h\\d+"), "w480-h480").replace(Regex("=s\\d+"), "=s480")
}

/** The first page's art, fetched before a new dial is shown so its tiles come in sharp (at most [timeoutMs]). */
internal suspend fun prefetchDialArt(context: android.content.Context, entries: List<HomeSpeedDialEntry>, timeoutMs: Long = 1_500L) {
    val urls = entries.take(SPEED_DIAL_COLUMNS * 4).mapNotNull { dialArtwork(it.artworkUrl) }.distinct()
    if (urls.isEmpty()) return
    val loader = coil.Coil.imageLoader(context)
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        kotlinx.coroutines.coroutineScope {
            urls.map { url ->
                async { loader.execute(coil.request.ImageRequest.Builder(context).data(url).size(DIAL_ART_SIZE).build()) }
            }.forEach { it.await() }
        }
    }
}
/** How much of the shelf after Speed Dial shows above the mini player on first open. */
internal val HOME_DIAL_PEEK = 56.dp
/** The pinned mood-chip row over Speed Dial (chip plus its vertical padding). */
internal val HOME_CHIPS_ROW_HEIGHT = 52.dp
/** Room under a dial's grid for its page dots and the scroll-to-feed chevron. */
internal val HOME_DIAL_HERO_GRID_RESERVE = 96.dp
/** Visible gap between the pinned chips and a dial's first row. */
internal val HOME_DIAL_TOP_GAP = 16.dp
/** How far a dial is drawn up into the list's section gap to leave [HOME_DIAL_TOP_GAP]. */
internal val HOME_DIAL_PULL_UP = MeloLayout.SectionGap - HOME_DIAL_TOP_GAP
internal val HOME_DIAL_PAGER_HEADER_HEIGHT = 76.dp
internal val HOME_DIAL_PAGER_GRID_RESERVE = 148.dp

private enum class SpeedDialAction { LUCKY, HISTORY }

/** What a tile's art animates on: the new-release tag and badges change in place. */
private data class SpeedDialTileVisual(
    val title: String,
    val artworkUrl: String?,
)

@Composable
private fun artworkForeground(): Color = if (LocalMeloDarkTheme.current) {
    MaterialTheme.colorScheme.onSurface
} else {
    MaterialTheme.colorScheme.onPrimary
}

/**
 * Where everything on the dial goes, slot by slot: an entry's index, Lucky, History or nothing.
 *
 * Pages fill in order, so no page but the last has a gap. The dial ends on a full page where it
 * can: the last entries (the least wanted; pins come first) are left off to make it so, unless
 * that would drop a pin or leave nothing, and then the last page keeps its gaps. Lucky owns the
 * first page's bottom-right tile and History the last page's (just before Lucky on a one-page dial).
 */
private fun speedDialSlots(
    count: Int,
    lucky: Boolean,
    history: Boolean,
    rows: Int,
    maxPages: Int?,
    isPinned: (Int) -> Boolean = { false },
): IntArray {
    val pageSize = SPEED_DIAL_COLUMNS * rows
    val actionCount = (if (lucky) 1 else 0) + (if (history) 1 else 0)
    var itemCount = maxPages?.let { count.coerceAtMost((pageSize * it - actionCount).coerceAtLeast(0)) } ?: count
    var needed = itemCount + actionCount
    if (needed == 0) return IntArray(0)
    val spill = needed % pageSize
    if (needed > pageSize && spill != 0) {
        val trimmed = itemCount - spill
        if (trimmed > 0 && (trimmed until itemCount).none(isPinned)) {
            itemCount = trimmed
            needed = itemCount + actionCount
        }
    }
    val pageCount = (needed + pageSize - 1) / pageSize
    val slots = IntArray(pageCount * pageSize) { SLOT_BLANK }
    if (lucky) slots[pageSize - 1] = SLOT_LUCKY
    if (history) slots[if (pageCount == 1 && lucky) pageSize - 2 else slots.lastIndex] = SLOT_HISTORY
    var next = 0
    for (slot in slots.indices) {
        if (next >= itemCount) break
        if (slots[slot] == SLOT_BLANK) slots[slot] = next++
    }
    return slots
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSpeedDial(
    entries: List<HomeSpeedDialEntry>,
    pinnedIds: Set<String>,
    categoryChips: List<YouTubeHomeChip>,
    /** False hides the page's own heading where something else already names it (the dial switch). */
    showHeading: Boolean = true,
    selectedCategory: YouTubeHomeChip?,
    onCategoryClick: (YouTubeHomeChip) -> Unit,
    onItemClick: (HomeSpeedDialEntry) -> Unit,
    onItemLongPress: (HomeSpeedDialEntry) -> Unit,
    hasLuckyTile: Boolean,
    onLuckyClick: () -> Job?,
    onHistoryClick: () -> Unit,
    onReorderPins: (() -> Unit)?,
    onCustomize: (() -> Unit)? = null,
    title: String? = null,
    /** Whether [title] is written over the grid; off where the top bar already names the page. */
    showTitleText: Boolean = true,
    sharedPagerHeader: Boolean = false,
    maxPages: Int? = null,
    rows: Int = 4,
    /** The first Speed Dial / Artist Dial page fills the visible Home viewport. */
    pageHeight: Dp? = null,
    footerReserve: Dp = 0.dp,
    /** Available height for square tiles after the mood row and page controls. */
    maxGridHeight: Dp? = null,
    showLabels: Boolean = true,
    showPinIcon: Boolean = true,
    tileStyle: String = `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS,
    showNewReleaseTags: Boolean = true,
    showHistoryTile: Boolean = true,
    /** Marks each tile as a mix with a small round play badge. */
    showMixBadges: Boolean = false,
    onMissingArtwork: ((Music) -> Unit)? = null,
    loading: Boolean = false,
    onScrollToFeed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!loading && entries.isEmpty() && categoryChips.isEmpty() && title == null && !hasLuckyTile && !showHistoryTile) return
    val safeRows = rows.coerceIn(3, 4)
    val pageSize = SPEED_DIAL_COLUMNS * safeRows
    val reservedActionCount = (if (hasLuckyTile) 1 else 0) + (if (showHistoryTile) 1 else 0)
    val visibleEntries = maxPages?.let {
        entries.take((pageSize * it - reservedActionCount).coerceAtLeast(0))
    } ?: entries
    val slots = if (loading) IntArray(pageSize) { it }
        else if (visibleEntries.isEmpty() && !hasLuckyTile && !showHistoryTile) IntArray(0)
        else speedDialSlots(visibleEntries.size, hasLuckyTile, history = showHistoryTile, rows = safeRows, maxPages = maxPages,
            isPinned = { visibleEntries[it].item.id in pinnedIds })
    val pageCount = slots.size / pageSize
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // The first time a dial shows its tiles after launch they rise in row by row; after that
    // (tab switches, coming back from a page) they are simply there.
    val reducedMotion = LocalReducedMotion.current
    val entering = remember(loading || slots.isEmpty()) { !loading && slots.isNotEmpty() && !reducedMotion && !dialEnteredThisLaunch }
    if (!loading && slots.isNotEmpty()) SideEffect { dialEnteredThisLaunch = true }

    val columnModifier = modifier.fillMaxWidth().then(
        if (pageHeight != null) Modifier.height(pageHeight) else Modifier.padding(bottom = 24.dp),
    )
    Column(columnModifier.padding(bottom = footerReserve), verticalArrangement = Arrangement.spacedBy(MeloLayout.Space3)) {
        if (sharedPagerHeader) {
            Box(Modifier.fillMaxWidth().height(HOME_DIAL_PAGER_HEADER_HEIGHT)) {
                if (title != null || onReorderPins != null || onCustomize != null) {
                    HomeSpeedDialHeading(
                        title = title.takeIf { showTitleText },
                        onReorderPins = onReorderPins,
                        onCustomize = onCustomize,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (categoryChips.isNotEmpty()) {
                    HomeCategoryChips(
                        chips = categoryChips,
                        selected = selectedCategory,
                        onClick = onCategoryClick,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        } else {
            if (showHeading && (title != null || onReorderPins != null || onCustomize != null)) {
                HomeSpeedDialHeading(
                    title = title.takeIf { showTitleText },
                    onReorderPins = onReorderPins,
                    onCustomize = onCustomize,
                )
            } else if (showHeading) {
                // Dial leaves a little breathing room above the first row of art.
                Spacer(Modifier.height(MeloLayout.Space4))
            }

            if (categoryChips.isNotEmpty()) {
                HomeCategoryChips(categoryChips, selectedCategory, onCategoryClick)
            }
        }

        // Square tiles a third of the content width (16dp
        // gutters, 8dp gaps), shrunk to no less than 80% of that only on a page too short for
        // them, centred, with the dots right under the grid. The page's room leaves the mini
        // player's place whether or not a song is on, so a song starting never reshapes the tiles.
        val fillsPage = pageHeight != null
        if (loading || slots.isNotEmpty()) BoxWithConstraints(
            Modifier.fillMaxWidth().then(if (fillsPage) Modifier.weight(1f) else Modifier),
        ) {
            val contentWidth = maxWidth - SPEED_DIAL_EDGE * 2
            val full = (contentWidth - SPEED_DIAL_GAP * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
            val dotsHeight = if (pageCount > 1) SPEED_DIAL_DOTS_HEIGHT else 0.dp
            val hintUnderDots = fillsPage && onScrollToFeed != null
            val hintHeight = if (hintUnderDots) SPEED_DIAL_HINT_HEIGHT else 0.dp
            val room = if (fillsPage) maxHeight - dotsHeight - hintHeight else maxGridHeight
            val fit = room?.let { (it - SPEED_DIAL_GAP * (safeRows - 1)) / safeRows }
            val side = fit?.let { minOf(full, maxOf(it, full * SPEED_DIAL_MIN_SCALE)) } ?: full
            val gridHeight = side * safeRows + SPEED_DIAL_GAP * (safeRows - 1)
            val sideInset = ((contentWidth - side * SPEED_DIAL_COLUMNS - SPEED_DIAL_GAP * (SPEED_DIAL_COLUMNS - 1)) / 2).coerceAtLeast(0.dp)
            val tile = Modifier.size(side).dialTileOutset()
            Column(Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(gridHeight),
                    // The pages sit in the gutters 12dp apart, so a sliver of
                    // the next one shows at the edge.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = SPEED_DIAL_EDGE),
                    pageSpacing = SPEED_DIAL_PAGE_SPACING,
                ) { page ->
                    val pageSlots = slots.drop(page * pageSize).take(pageSize)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = sideInset),
                        verticalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP),
                    ) {
                        repeat(safeRows) { rowIndex ->
                            Row(
                                modifier = Modifier.dialEntrance(entering && page == 0, rowIndex),
                                horizontalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP),
                            ) {
                                repeat(SPEED_DIAL_COLUMNS) { columnIndex ->
                                    val slot = pageSlots[rowIndex * SPEED_DIAL_COLUMNS + columnIndex]
                                    when {
                                        loading -> HomeSkeletonTile(tile)
                                        slot >= 0 -> {
                                            val entry = visibleEntries[slot]
                                            LaunchedEffect(entry.item.id, entry.artworkUrl) {
                                                if (entry.artworkUrl.isNullOrBlank()) onMissingArtwork?.invoke(entry.item)
                                            }
                                            SpeedDialTile(
                                                entry = entry,
                                                pinned = entry.item.id in pinnedIds,
                                                showLabel = showLabels,
                                                showPinIcon = showPinIcon,
                                                tileStyle = tileStyle,
                                                showNewReleaseTag = showNewReleaseTags,
                                                showMixBadge = showMixBadges,
                                                animationStaggerIndex = rowIndex + columnIndex,
                                                modifier = tile,
                                                onClick = { onItemClick(entry) },
                                                onLongPress = { onItemLongPress(entry) },
                                            )
                                        }
                                        slot == SLOT_LUCKY -> LuckyDiceTile(
                                            modifier = tile,
                                            tileStyle = tileStyle,
                                            onRoll = onLuckyClick,
                                        )
                                        slot == SLOT_HISTORY -> HistoryDialTile(
                                            modifier = tile,
                                            tileStyle = tileStyle,
                                            showLabel = showLabels,
                                            onClick = onHistoryClick,
                                        )
                                        else -> Spacer(tile)
                                    }
                                }
                            }
                        }
                    }
                }
                if (pageCount > 1) SpeedDialDots(pagerState.currentPage, pageCount)
                if (hintUnderDots) SpeedDialMoreHint(onScrollToFeed!!, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (!loading && entries.isEmpty() && title != null) {
            Text(
                text = stringResource(R.string.home_dial_mixes_empty),
                modifier = Modifier.fillMaxWidth().padding(horizontal = MeloLayout.PageGutter, vertical = 28.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onScrollToFeed != null && (!fillsPage || slots.isEmpty() && !loading)) {
            Spacer(Modifier.weight(1f))
            SpeedDialMoreHint(onScrollToFeed, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * The faint chevron under the page dots: there is more below. Drawn as a bare
 * 22dp rounded chevron 4dp under the dots, and still a tap down to the feed.
 */
@Composable
private fun SpeedDialMoreHint(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_dial_scroll_to_feed)
    Box(
        modifier
            .padding(top = 4.dp)
            .size(width = 56.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MeloIcons.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SpeedDialDots(current: Int, count: Int) {
    val pageLabel = stringResource(R.string.home_speed_dial_page, current + 1, count)
    val color = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SPEED_DIAL_DOTS_HEIGHT)
            .padding(top = 10.dp)
            .semantics { contentDescription = pageLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (index == current) 0.9f else 0.3f)),
            )
        }
    }
}

@Composable
private fun HomeSpeedDialHeading(
    title: String?,
    onReorderPins: (() -> Unit)?,
    onCustomize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MeloLayout.PageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onReorderPins != null) {
            MeloIconButton(MeloIcons.SwapVert, stringResource(R.string.pins_reorder), onReorderPins)
        }
        if (onCustomize != null) {
            MeloIconButton(MeloIcons.Tune, stringResource(R.string.home_dial_customize), onCustomize)
        }
    }
}

/**
 * The Home tab's taps, for the dial pager. [requests]: Home tapped again while on Home, which
 * turns the pager back to Speed Dial. [entries]: Home tapped from another tab, which opens it
 * on Speed Dial, rather than on the Mixes Dial it may have been left on. Coming
 * back from a page a mix opened isn't an entry, so that lands on the mixes again.
 */
object HomeReselect {
    var requests by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var entries by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun request() { requests++ }
    fun entered() { entries++ }
}

private const val MIXES_PAGE = 0
private const val SPEED_DIAL_PAGE = 1

/** The slowest flick that turns a page, as a pager's own swipes have it. */
private val MIN_DIAL_FLING_VELOCITY = 400.dp

/** How far off a page counts as between two, as a share of a page. */
private const val DIAL_SETTLED_FRACTION = 0.001f

/** How many frames the dial in view has to itself before the one beside it is made up. */
private const val DIAL_BESIDE_AFTER_FRAMES = 3

/**
 * The page a swipe let go at [position] (pages, with the part of the next one dragged in)
 * lands on. [velocity] is the finger's along the pager, negative towards the next page. A
 * flick faster than [minFling] turns the page its way however little was dragged; a slower
 * one settles on the nearer page.
 */
internal fun dialSwipeTarget(position: Float, velocity: Float, minFling: Float, pageCount: Int): Int {
    val page = when {
        velocity <= -minFling -> kotlin.math.ceil(position)
        velocity >= minFling -> kotlin.math.floor(position)
        else -> kotlin.math.round(position)
    }
    return page.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

/**
 * Ends a swipe that a dial inside carried over to this pager. The dials are pagers too, so
 * every swipe starts in one of them; past its first page the drag moves this pager, but the
 * flick that ends it was spent on the dial inside and reached this one as no speed at all, so
 * the page only turned when dragged more than halfway. This takes the flick first while the
 * pager is between pages and turns it, so the mixes swipe in as easily as the dial's own
 * pages.
 */
private class DialFlingHandoff(
    private val state: androidx.compose.foundation.pager.PagerState,
    private val minFling: Float,
    private val reversed: Boolean,
) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
        val offset = state.currentPageOffsetFraction
        if (kotlin.math.abs(offset) < DIAL_SETTLED_FRACTION) return androidx.compose.ui.unit.Velocity.Zero
        val target = dialSwipeTarget(
            position = state.currentPage + offset,
            velocity = if (reversed) -available.x else available.x,
            minFling = minFling,
            pageCount = state.pageCount,
        )
        state.animateScrollToPage(
            target,
            animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        )
        return available
    }
}

/**
 * Speed Dial with the Mixes Dial to its left: no switch over them, the swipe is
 * the way across. Swiping right from Speed Dial's first page turns to the mixes and swiping
 * left turns back; Speed Dial's own pages swipe inside it as before. Home tapped again turns
 * back to Speed Dial.
 */
@Composable
internal fun HomeDialPager(
    mixesAvailable: Boolean,
    speedDial: @Composable () -> Unit,
    mixesDial: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!mixesAvailable) {
        Box(modifier.fillMaxWidth()) { speedDial() }
        return
    }
    val state = rememberPagerState(initialPage = SPEED_DIAL_PAGE, pageCount = { 2 })
    val resetRequests = HomeReselect.requests
    var resetsSeen by remember { androidx.compose.runtime.mutableIntStateOf(resetRequests) }
    LaunchedEffect(resetRequests) {
        if (resetRequests == resetsSeen) return@LaunchedEffect
        resetsSeen = resetRequests
        if (state.currentPage != SPEED_DIAL_PAGE) state.animateScrollToPage(SPEED_DIAL_PAGE)
    }
    val minFling = with(androidx.compose.ui.platform.LocalDensity.current) { MIN_DIAL_FLING_VELOCITY.toPx() }
    val reversed = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val handoff = remember(state, minFling, reversed) { DialFlingHandoff(state, minFling, reversed) }
    // The frame that brings Home in builds the dial in view and nothing beside it; the other is
    // made up a few frames on, so coming back to Home isn't one long frame.
    var besideReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(DIAL_BESIDE_AFTER_FRAMES) { androidx.compose.runtime.withFrameNanos { } }
        besideReady = true
    }
    HorizontalPager(
        state = state,
        verticalAlignment = Alignment.Top,
        key = { it },
        beyondViewportPageCount = if (besideReady) 1 else 0,
        pageSpacing = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) { page ->
        Box(Modifier.fillMaxWidth().nestedScroll(handoff)) {
            if (page == MIXES_PAGE) mixesDial() else speedDial()
        }
    }
}

@Composable
internal fun HomeCategoryChips(
    chips: List<YouTubeHomeChip>,
    selected: YouTubeHomeChip?,
    onClick: (YouTubeHomeChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(chips, selected?.params) {
        val index = chips.indexOfFirst { it.params == selected?.params }
        if (selected != null && index >= 0) rowState.animateScrollToItem(index)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = MeloLayout.PageGutter),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        items(chips, key = YouTubeHomeChip::params) { chip ->
            `in`.melo.presentation.components.MeloChip(chip.title, chip.params == selected?.params, { onClick(chip) }, onWash = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    entry: HomeSpeedDialEntry,
    pinned: Boolean,
    showLabel: Boolean,
    showPinIcon: Boolean,
    tileStyle: String,
    showNewReleaseTag: Boolean,
    showMixBadge: Boolean = false,
    animationStaggerIndex: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val foreground = artworkForeground()
    val interactions = remember { MutableInteractionSource() }
    val reducedMotion = LocalReducedMotion.current
    val visual = SpeedDialTileVisual(title = entry.title, artworkUrl = dialArtwork(entry.artworkUrl))
    val isNewRelease = entry.isNewRelease || entry.recommendation?.type == `in`.melo.data.HomeRecommendationType.LATEST_RELEASE
    Box(
        modifier = modifier
            .homePress(interactions)
            .clip(SPEED_DIAL_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (tileStyle == `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS) Modifier
                else Modifier.artworkOutline(SPEED_DIAL_SHAPE))
            .combinedClickable(
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = listOf(entry.title, entry.item.subtitle)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
                role = Role.Button
            },
    ) {
        AnimatedContent(
            targetState = visual,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // [animationStaggerIndex] is the tile's diagonal (row + column), so the new art
                // sweeps across the grid corner to corner.
                val stagger = if (reducedMotion) 0 else animationStaggerIndex * 70
                val enter = if (reducedMotion) {
                    fadeIn(tween(durationMillis = 160))
                } else {
                    val timing = tween<Float>(
                        durationMillis = 380,
                        delayMillis = stagger,
                        easing = FastOutSlowInEasing,
                    )
                    fadeIn(timing) + scaleIn(initialScale = 0.94f, animationSpec = timing) +
                        slideIn(
                            animationSpec = tween(durationMillis = 380, delayMillis = stagger, easing = FastOutSlowInEasing),
                            initialOffset = { size -> androidx.compose.ui.unit.IntOffset(-size.width / 10, -size.height / 10) },
                        )
                }
                val exit = if (reducedMotion) {
                    fadeOut(tween(durationMillis = 120))
                } else {
                    val timing = tween<Float>(
                        durationMillis = 170,
                        delayMillis = stagger,
                        easing = FastOutSlowInEasing,
                    )
                    fadeOut(timing) + scaleOut(targetScale = 0.98f, animationSpec = timing)
                }
                enter togetherWith exit
            },
            label = "speedDialRefreshTile",
        ) { displayedTile ->
            Box(Modifier.fillMaxSize()) {
                SmartImage(
                    model = displayedTile.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    targetSize = DIAL_ART_SIZE,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showLabel) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
                    )
                    Text(
                        text = displayedTile.title,
                        modifier = Modifier.align(Alignment.BottomStart).padding(DialLabelPadding),
                        style = dialLabelStyle(),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pinned && showPinIcon) {
                    Icon(
                        imageVector = MeloIcons.PushPin,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                        tint = Color.White,
                    )
                }
                if (showMixBadge && !(showNewReleaseTag && isNewRelease)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(MeloLayout.Space2)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = MeloAlpha.Scrim))
                            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MeloIcons.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                    }
                }
                if (showNewReleaseTag && isNewRelease) {
                    Text(
                        text = stringResource(R.string.home_new_music_tag),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(MeloLayout.Space2)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = MeloAlpha.Scrim))
                            .padding(horizontal = MeloLayout.Space2, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground,
                        maxLines = 1,
                    )
                }
                when (tileStyle) {
                    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_EDGE -> {
                        Box(Modifier.fillMaxSize().artworkRim(SPEED_DIAL_SHAPE))
                    }
                    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .artworkRim(SPEED_DIAL_SHAPE, glass = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun speedDialRim(tileStyle: String): Modifier = when (tileStyle) {
    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_EDGE -> Modifier.artworkRim(SPEED_DIAL_SHAPE)
    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS -> Modifier.artworkRim(SPEED_DIAL_SHAPE, glass = true)
    else -> Modifier.artworkOutline(SPEED_DIAL_SHAPE)
}

/**
 * A dark see-through tile with the history clock in the
 * middle and, with labels on, its name at the bottom.
 */
@Composable
private fun HistoryDialTile(modifier: Modifier, tileStyle: String, showLabel: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.history_title)
    val description = stringResource(R.string.home_speed_dial_history_description)
    val interactions = remember { MutableInteractionSource() }
    val labelStyle = MaterialTheme.typography.labelLarge
    Box(
        modifier = modifier
            .homePress(interactions)
            .clip(SPEED_DIAL_SHAPE)
            .background(Color(0xFF141414).copy(alpha = 0.5f))
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. $description" },
    ) {
        Icon(
            MeloIcons.History,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).fillMaxSize(0.36f),
            tint = Color.White.copy(alpha = 0.85f),
        )
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier.align(Alignment.BottomStart).padding(DialLabelPadding),
                style = labelStyle.copy(fontSize = (labelStyle.fontSize.value - 2f).sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxSize().then(speedDialRim(tileStyle)))
    }
}

/**
 * A die of nine squares on a dark ground (light in the light
 * theme) with soft washes of colour. A tap sets a light chasing round its edge at one even speed
 * until the pick is found (at least 450ms, at most 12s), then it comes to rest and lands on a face.
 */
@Composable
private fun LuckyDiceTile(modifier: Modifier, tileStyle: String, onRoll: () -> Job?) {
    val label = stringResource(R.string.home_speed_dial_lucky)
    val description = stringResource(R.string.home_speed_dial_lucky_description)
    val findingDescription = stringResource(R.string.home_speed_dial_lucky_finding)
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var face by remember { androidx.compose.runtime.mutableIntStateOf(3) }
    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
    val settle = remember { androidx.compose.animation.core.Animatable(1f) }
    var rolling by remember { mutableStateOf(false) }
    val look = if (LocalMeloDarkTheme.current) DICE_DARK else DICE_LIGHT
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .homePress(interactions)
            .clip(SPEED_DIAL_SHAPE)
            .background(look.ground)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button) {
                if (rolling) return@clickable
                rolling = true
                val found = onRoll()
                scope.launch {
                    try {
                        if (reducedMotion) {
                            found?.join()
                            face = (1..6).random()
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            return@launch
                        }
                        spin.snapTo(0f)
                        // A light tick each time the light moves on a square.
                        var square = 0
                        val tick: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.() -> Unit = {
                            val now = value.toInt()
                            if (now != square) {
                                square = now
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        kotlinx.coroutines.coroutineScope {
                            val chase = launch {
                                spin.animateTo(
                                    DICE_WAIT_MAX_MS * DICE_SPEED,
                                    tween(DICE_WAIT_MAX_MS.toInt(), easing = LinearEasing),
                                    block = tick,
                                )
                            }
                            settle.animateTo(0f, tween(DICE_FADE_MS))
                            delay(DICE_MIN_MS - DICE_FADE_MS)
                            kotlinx.coroutines.withTimeoutOrNull(DICE_WAIT_MAX_MS - DICE_MIN_MS) { found?.join() }
                            chase.cancelAndJoin()
                        }
                        // Out of that speed to a stop with no step in it: the curve starts at twice
                        // its mean speed, so its time makes that the speed the light had.
                        spin.animateTo(
                            spin.value + DICE_REST_STEPS,
                            tween((2 * DICE_REST_STEPS / DICE_SPEED).toInt(), easing = DICE_REST_EASING),
                            block = tick,
                        )
                        face = (1..6).random()
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        settle.animateTo(
                            1f,
                            androidx.compose.animation.core.spring(
                                dampingRatio = 0.55f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                            ),
                        )
                    } finally {
                        rolling = false
                    }
                }
            }
            .semantics {
                contentDescription = "$label. $description"
                if (rolling) stateDescription = findingDescription
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Crimson in from the upper left, violet from the lower right, a little cyan at the upper right.
            DICE_WASHES.forEach { wash ->
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(wash.color.copy(alpha = wash.alpha * look.washStrength), wash.color.copy(alpha = 0f)),
                        center = androidx.compose.ui.geometry.Offset(w * wash.x, h * wash.y),
                        radius = size.maxDimension * wash.radius,
                    ),
                )
            }
            // Three squares of 80 with gaps of 30 on a 512 ground, dead centre.
            val unit = size.minDimension / 512f
            val cellSide = 80f * unit
            val gap = 30f * unit
            val gridSide = cellSide * 3 + gap * 2
            val left = (w - gridSide) / 2f
            val top = (h - gridSide) / 2f
            val head = spin.value % DICE_RING.size
            val toFace = settle.value
            val pips = DICE_FACES[face - 1]
            for (i in 0 until 9) {
                val ring = DICE_RING.indexOf(i)
                val spinner = if (ring < 0) 0f else {
                    val behind = ((head - ring) % DICE_RING.size + DICE_RING.size) % DICE_RING.size
                    (1f - behind / DICE_TAIL).coerceIn(0f, 1f)
                }
                val pip = if (i in pips) 1f else 0f
                val level = (spinner + (pip - spinner) * toFace).coerceIn(0f, 1f)
                // A lit square swells a touch with the landing's overshoot.
                val pop = if (pip > 0f && toFace > 1f) 1f + (toFace - 1f) * 0.6f else 1f
                val side = cellSide * pop
                val center = androidx.compose.ui.geometry.Offset(
                    left + (i % 3) * (cellSide + gap) + cellSide / 2f,
                    top + (i / 3) * (cellSide + gap) + cellSide / 2f,
                )
                if (level > 0f) {
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(look.lit.copy(alpha = look.glow * level), look.lit.copy(alpha = 0f)),
                            center = center,
                            radius = side * 1.05f,
                        ),
                        radius = side * 1.05f,
                        center = center,
                    )
                }
                drawRoundRect(
                    color = androidx.compose.ui.graphics.lerp(look.unlit, look.lit, level),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - side / 2f, center.y - side / 2f),
                    size = androidx.compose.ui.geometry.Size(side, side),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.08f),
                )
            }
        }
        Box(Modifier.fillMaxSize().then(speedDialRim(tileStyle)))
    }
}

/** A soft wash of colour on the die's ground: where (as fractions of the tile), how big, how strong. */
private class DiceWash(val color: Color, val x: Float, val y: Float, val radius: Float, val alpha: Float)

private val DICE_WASHES = listOf(
    DiceWash(Color(0xFFFA5A72), 0f, 0.15f, 0.62f, 0.26f),
    DiceWash(Color(0xFF8B5CF6), 1f, 0.85f, 0.68f, 0.26f),
    DiceWash(Color(0xFF38BDF8), 1f, 0.1f, 0.5f, 0.18f),
)

/** The die's colours: its ground, its unlit and lit squares, how strong a lit square's glow and the washes are. */
private class DiceLook(val ground: Color, val unlit: Color, val lit: Color, val glow: Float, val washStrength: Float)

private val DICE_DARK = DiceLook(Color(0xFF141414), Color(0xFF262626), Color.White, 0.45f, 1f)
/** The light theme's die: dark squares on a near-white ground, the washes a little fuller to show on it. */
private val DICE_LIGHT = DiceLook(Color(0xFFF4F4F6), Color(0xFFDCDCE2), Color(0xFF161618), 0.18f, 1.25f)

/** The squares round the edge in the order the light goes, and each face's lit squares. */
private val DICE_RING = intArrayOf(0, 1, 2, 5, 8, 7, 6, 3)
private val DICE_FACES = listOf(
    setOf(4),
    setOf(2, 6),
    setOf(0, 4, 8),
    setOf(0, 2, 6, 8),
    setOf(0, 2, 4, 6, 8),
    setOf(0, 2, 3, 5, 6, 8),
)

/** The light's speed in squares a millisecond (once round in 720ms), its least time, its rest and its tail. */
private const val DICE_SPEED = 8f / 720f
private const val DICE_MIN_MS = 450L
private const val DICE_REST_STEPS = 2f
private val DICE_REST_EASING = androidx.compose.animation.core.Easing { 1f - (1f - it) * (1f - it) }
private const val DICE_TAIL = 3.2f
private const val DICE_FADE_MS = 140
/** The longest the die waits for the pick, so it can never be left turning. */
private const val DICE_WAIT_MAX_MS = 12_000L

/** Whether a dial has already played its entrance since the app started. */
private var dialEnteredThisLaunch = false

/** A dial row rising into place, [row] by row, when [entering]; nothing otherwise. */
@Composable
private fun Modifier.dialEntrance(entering: Boolean, row: Int): Modifier {
    if (!entering) return this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(row * 70L)
        progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.92f + 0.08f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 24.dp.toPx()
    }
}

/** One round's dealt tiles for a dial: [key] is what they were dealt for (the source and its settings). */
internal data class DealtDial(val round: Int, val key: String, val items: List<Music>)

/**
 * The dials' rounds: a round starts when the app does and at each pull to refresh,
 * and each dial is dealt once in it, from the feed that round fetched, so a song starting or a
 * tab switch doesn't move the tiles. Held for the process, so Home keeps them when it's left.
 */
internal object HomeDialRounds {
    var round by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    /** When this round began; the feed must have arrived after it to be dealt from. */
    var roundStartedAt = 0L
        private set
    var speedDial by androidx.compose.runtime.mutableStateOf<DealtDial?>(null)
    var mixes by androidx.compose.runtime.mutableStateOf<DealtDial?>(null)
    /** What the Spotify picks are shuffled with: held for the process, so only a refresh deals them anew. */
    var shuffle by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun nextRound(reshuffle: Boolean) {
        roundStartedAt = System.currentTimeMillis()
        round++
        if (reshuffle) shuffle++
    }
}

/** A dial tile's label inset: 8dp by 6dp, less what the tile grows past its cell. */
private val DialLabelPadding = androidx.compose.foundation.layout.PaddingValues(
    horizontal = 8.dp - SPEED_DIAL_TILE_OUTSET * 1.5f,
    vertical = 6.dp - SPEED_DIAL_TILE_OUTSET,
)

/** A dial tile's label: labelLarge less 2sp. */
@Composable
private fun dialLabelStyle() = MaterialTheme.typography.labelLarge.let {
    it.copy(fontSize = (it.fontSize.value - 2f).sp)
}
