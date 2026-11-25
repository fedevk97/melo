package `in`.melo.presentation.components

import `in`.melo.ui.glass.meloLightGlass
import `in`.melo.ui.glass.meloGlass
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import `in`.melo.ui.theme.separator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout

val LocalSurfaceGradients = staticCompositionLocalOf { true }

/**
 * True for rows placed straight inside a [MeloGroupedList]: their cards and panels draw
 * flat so the group's one rounded surface and hairlines do the grouping. Anything nested
 * inside such a row's card is back to false and keeps its own surface.
 */
val LocalMeloGrouped = staticCompositionLocalOf { false }

/**
 * An inset grouped list in the Apple style: one rounded surface holding its rows, each
 * separated by a hairline that starts at the text inset rather than the edge.
 */
@Composable
fun MeloGroupedList(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val separator = colors.separator
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) { MeloLayout.PageGutter.roundToPx() }
    androidx.compose.ui.layout.SubcomposeLayout(
        modifier.fillMaxWidth().clip(MeloLayout.PanelShape).background(pageCardColor(colors.surfaceContainer)),
    ) { constraints ->
        val loose = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0,
            maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val rows = subcompose("rows") {
            androidx.compose.runtime.CompositionLocalProvider(LocalMeloGrouped provides true) { content() }
        }.map { it.measure(loose) }.filter { it.height > 0 }
        val lineWidth = (constraints.maxWidth - inset).coerceAtLeast(0)
        val lines = subcompose("lines") {
            repeat((rows.size - 1).coerceAtLeast(0)) {
                Box(Modifier.fillMaxWidth().height(MeloLayout.Hairline).background(separator))
            }
        }.map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(lineWidth)) }
        val height = rows.sumOf { it.height }
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEachIndexed { index, row ->
                row.placeRelative(0, y)
                y += row.height
                lines.getOrNull(index)?.placeRelative(inset, y - lines[index].height)
            }
        }
    }
}

/** The same quiet surface, soft gradient and rim across settings, sheets and controls. */
fun Modifier.meloPanel(shape: Shape = MeloLayout.PanelShape, color: Color? = null): Modifier = composed {
    if (LocalMeloGrouped.current) return@composed Modifier
    val colors = MaterialTheme.colorScheme
    val ground = color ?: pageCardColor(colors.surface)
    clip(shape)
        .background(ground)
        .background(Brush.verticalGradient(listOf(
            colors.onSurface.copy(alpha = if (LocalSurfaceGradients.current) 0.025f else 0f), Color.Transparent,
        )))
        .border(0.75.dp, Brush.verticalGradient(listOf(
            colors.onSurface.copy(alpha = 0.16f), colors.onSurface.copy(alpha = 0.055f),
            colors.onSurface.copy(alpha = 0.09f),
        )), shape)
}

/**
 * The material of everything that floats over the page (tab bar, mini player): a
 * see-through fill, a hairline round it and a soft shadow, so content scrolling under
 * it stays faintly visible, the way iOS's bar material reads.
 */
fun Modifier.meloBarMaterial(shape: Shape, elevation: Dp = BarShadow): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    // Over the recorded pages the bar is the app's material (Glass or Frost); elsewhere, and in
    // the Solid style, it keeps its own lifted fill.
    if (`in`.melo.ui.glass.LocalAppBackdrop.current != null) return@composed meloGlass(shape)
    shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
        .background(barMaterialColor(), shape)
        .border(MeloLayout.Hairline, colors.separator, shape)
}

/**
 * Glass for a control lying on a page rather than floating over the pages, such as the top
 * capsule and the Home chips. Over the recorded pages it is the app's glass, as the bars are.
 * Inside a page, which nothing there can sample, it is the lightweight glass: the glass's own
 * fill (with the user's tint), light and hairline, and the colour background showing through it.
 */
fun Modifier.meloPageGlass(shape: Shape): Modifier = composed {
    if (`in`.melo.ui.glass.LocalAppBackdrop.current != null) meloGlass(shape)
    else with(Modifier) { meloLightGlass(shape) }
}

/**
 * Every pushed page's frame (Settings and its pages, History, the audio cache, a show):
 * [pageBackground] under it, so the colour background shows through as it does on the tabs,
 * and [topBar] riding up with the content as it scrolls, with the soft fade under the status
 * bar rather than a filled bar. Give the bar [meloPageBarColors].
 */
@Composable
fun MeloPageScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrolled = rememberScrolledUnder()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrolled.connection),
        containerColor = pageBackground(),
        topBar = {
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, scrolled.offsetPx.toInt()) }) { topBar() }
            StatusBarFade { scrolled.fraction }
        },
        content = content,
    )
}

/** A see-through top bar for [MeloPageScaffold], also once content has scrolled under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun meloPageBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

/** The fill behind [meloBarMaterial]: lifted grey in dark, white in light, both see-through. */
@Composable
fun barMaterialColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (`in`.melo.ui.theme.LocalMeloDarkTheme.current) colors.surfaceContainerHigh.copy(alpha = BarAlpha)
    else colors.surface.copy(alpha = BarAlpha)
}

private const val BarAlpha = 0.88f
private val BarShadow = 3.dp

/**
 * Keeps Material's dismissal, focus, insets and drag behavior with Melo's shared surface.
 *
 * With no [containerColor] the sheet wears the app's material:
 * in Glass and Frost it is the screen under it seen through the glass, over the menus' own
 * scrim, so the Language and Theme pickers and every other sheet read as the menus do. Solid,
 * or a device without the glass, keeps the opaque [sheetColor]. A [containerColor] given
 * (a sheet themed by the playing song) keeps that fill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeloBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = MeloLayout.SheetShape,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    dragHandle: @Composable (() -> Unit)? = { MeloSheetGrabber() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    content: @Composable ColumnScope.() -> Unit,
) {
    val screen = `in`.melo.ui.glass.LocalPopupBackdrop.current
    val glass = containerColor == null && screen != null &&
        `in`.melo.ui.glass.LocalMeloSurface.current.style != `in`.melo.ui.glass.MeloSurfaceStyle.SOLID &&
        `in`.melo.ui.glass.isGlassSupported()
    val scrim = `in`.melo.ui.glass.menuScrimColor()
    if (glass) {
        // Material lays out and drags the sheet; the glass, the grabber and the insets are drawn
        // inside it, so the glass moves with the sheet and reaches down behind the gesture bar.
        val insets = contentWindowInsets()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            scrimColor = scrim,
            tonalElevation = 0.dp,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .meloGlass(shape, carriesText = true, backdrop = screen, underlay = scrim)
                    .windowInsetsPadding(insets),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                dragHandle?.invoke()
                // Over glass, grouped rows take the page's translucent veil rather than solid cards.
                androidx.compose.runtime.CompositionLocalProvider(LocalMeloGrouped provides false, LocalAmbientBackground provides true) {
                    Column(Modifier.fillMaxWidth(), content = content)
                }
            }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor ?: sheetColor(),
        contentColor = contentColor,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
    ) {
        // A sheet is solid, so what lies on it takes the solid fills, not the page's veil.
        androidx.compose.runtime.CompositionLocalProvider(LocalMeloGrouped provides false, LocalAmbientBackground provides false) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * Every sheet's fill, like an iOS sheet: the raised grey in dark (so it stands off the
 * black page behind), plain white in light.
 */
@Composable
fun sheetColor(): Color =
    if (`in`.melo.ui.theme.LocalMeloDarkTheme.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

/** The small grey bar at the top of a sheet that says it can be dragged down. */
@Composable
fun MeloSheetGrabber(color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)) {
    Box(
        Modifier.padding(top = MeloLayout.Space2, bottom = MeloLayout.Space3)
            .size(width = 36.dp, height = 5.dp)
            .clip(MeloLayout.PillShape)
            .background(color),
    )
}
