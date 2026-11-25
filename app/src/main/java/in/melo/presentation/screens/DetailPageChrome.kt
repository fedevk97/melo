/*
 * Album, playlist and artist page chrome.
 *
 * The artwork palette (the accent swatch, the dark and light page palettes, the saturation),
 * the chrome theme and the release actions row, on Coil 2 and androidx Palette, with Melo's own
 * colour scheme laid over the page and a frosted film in place of liquid glass (a page cannot
 * bend a recording of itself). Licensed under GPL-3.0; see LICENSE.
 */
package `in`.melo.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.spring
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.roundToInt
import android.os.Build
import coil.size.Size as CoilSize
import `in`.melo.presentation.components.SmartImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.foundation.layout.fillMaxSize
import `in`.melo.ui.glass.backdrop.backdrops.LayerBackdrop
import `in`.melo.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import `in`.melo.ui.glass.backdrop.drawBackdrop
import `in`.melo.ui.glass.backdrop.effects.blur
import `in`.melo.ui.glass.pagesUnderGlass
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import `in`.melo.R
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.presentation.utils.LocalReducedMotion
import `in`.melo.ui.glass.pressShade
import `in`.melo.ui.glass.pressSink
import `in`.melo.ui.glass.rememberPressDent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// The page's colours
// ---------------------------------------------------------------------------------------------

/**
 * The colours a page paints itself in, all taken off its cover: the page [wash], a deeper
 * [background] and an [elevated] fill of the same hue, an [accent] legible on the wash, and the
 * text and hairline colours that go with them. [topLuminance] is how bright the cover's top band
 * is, for the bar's glyphs over it; null until the cover has been read.
 */
@Immutable
internal data class PagePalette(
    val background: Color,
    val wash: Color,
    val elevated: Color,
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
    val topLuminance: Float?,
)

private data class PageSeed(val dominant: Color, val vibrant: Color, val edge: Color, val topLuminance: Float)

private val seedCache = LruCache<String, PageSeed>(128)

/**
 * The page palette for the cover at [imageUrl], in the dark or light set. A cover already read
 * is tinted from the first frame; one seen for the first time starts from the theme and settles
 * into its colours over a short fade (a cut under reduced motion).
 */
@Composable
internal fun rememberPagePalette(imageUrl: String?, dark: Boolean): PagePalette {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReducedMotion.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }
    val knownUpFront = remember(imageUrl) { seed != null }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank() || seed != null) return@LaunchedEffect
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(imageUrl).size(PALETTE_PX).allowHardware(false).build()
        )
        val drawable = (result as? SuccessResult)?.drawable ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) {
            seedOf(drawable.toBitmap(config = Bitmap.Config.ARGB_8888))
        } ?: return@LaunchedEffect
        seedCache.put(imageUrl, found)
        seed = found
    }
    val target = seed?.toPalette(dark) ?: PagePalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
        topLuminance = null,
    )
    val spec: AnimationSpec<Color> = if (reduceMotion || knownUpFront) snap() else tween(TINT_FADE_MS)
    return PagePalette(
        background = animateColorAsState(target.background, spec, label = "pageBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "pageWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "pageElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "pageAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "pageOn").value,
        onBackgroundVariant = animateColorAsState(target.onBackgroundVariant, spec, label = "pageOnVariant").value,
        divider = animateColorAsState(target.divider, spec, label = "pageDivider").value,
        topLuminance = target.topLuminance,
    )
}

/**
 * The app's colour scheme with the page's colours laid over it, so the rows, buttons and text
 * already on the page take the cover's colour without each of them knowing about it.
 */
internal fun ColorScheme.tintedBy(p: PagePalette): ColorScheme {
    // Dark ink once it out-reads white: the crossover sits near relative luminance 0.18.
    val onAccent = if (p.accent.luminance() > 0.18f) Color(0xFF111111) else Color.White
    fun lift(amount: Float) = lerp(p.wash, p.onBackground, amount)
    return copy(
        primary = p.accent,
        onPrimary = onAccent,
        primaryContainer = lerp(p.wash, p.accent, 0.28f),
        onPrimaryContainer = p.onBackground,
        secondaryContainer = p.elevated,
        onSecondaryContainer = p.onBackground,
        background = p.wash,
        onBackground = p.onBackground,
        surface = p.wash,
        onSurface = p.onBackground,
        surfaceVariant = p.elevated,
        onSurfaceVariant = p.onBackgroundVariant,
        surfaceContainerLowest = lift(0.02f),
        surfaceContainerLow = lift(0.05f),
        surfaceContainer = lift(0.08f),
        surfaceContainerHigh = lift(0.11f),
        surfaceContainerHighest = lift(0.14f),
        outline = p.onBackgroundVariant.copy(alpha = 0.5f),
        outlineVariant = p.divider,
    )
}

private const val PALETTE_PX = 128
private const val TINT_FADE_MS = 260
private const val SWATCH_COUNT = 24
private const val EDGE_BAND = 0.18f
private const val TOP_BAND = 0.10f
private const val ACCENT_MIN_LIGHTNESS = 0.16f
private const val ACCENT_PALE_FROM = 0.80f
private const val ACCENT_CHROMA_FLOOR = 0.01f
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

private fun seedOf(bitmap: Bitmap): PageSeed? {
    // Unfiltered: the default filter drops near-black and near-white, which is the wrong answer
    // to "what is this page mostly made of" for a dark photograph or a monochrome sleeve.
    val swatches = Palette.from(bitmap).maximumColorCount(SWATCH_COUNT).clearFilters().generate().swatches
    if (swatches.isEmpty()) return null
    val dominant = swatches.maxBy { it.population }
    return PageSeed(
        dominant = Color(dominant.rgb),
        vibrant = Color(pickAccent(swatches.map { it.rgb to it.population })),
        edge = bitmap.bottomEdgeColor(),
        topLuminance = bitmap.topBandLuminance(),
    )
}

/**
 * The sleeve's one accent: HSL chroma against the square root of population, so a saturated
 * swatch beats a pale one and a speck never beats a colour the sleeve is made of. Near-black is
 * never picked while anything else is there.
 */
private fun pickAccent(swatches: List<Pair<Int, Int>>): Int {
    val hsl = FloatArray(3)
    fun lightness(rgb: Int): Float { ColorUtils.colorToHSL(rgb, hsl); return hsl[2] }
    val candidates = swatches.filter { (rgb, _) -> lightness(rgb) >= ACCENT_MIN_LIGHTNESS }
    if (candidates.isEmpty()) return swatches.maxBy { (rgb, _) -> lightness(rgb) }.first
    return candidates.maxBy { (rgb, population) ->
        ColorUtils.colorToHSL(rgb, hsl)
        val chroma = hsl[1] * (1f - abs(2f * hsl[2] - 1f))
        val pale = if (hsl[2] > ACCENT_PALE_FROM) 0.5f else 1f
        (chroma * pale + ACCENT_CHROMA_FLOOR) * sqrt(population.toFloat())
    }.first
}

private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)
    var r = 0L; var g = 0L; var b = 0L
    pixels.forEach { p -> r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
    val n = pixels.size.coerceAtLeast(1)
    return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
}

private fun Bitmap.topBandLuminance(): Float {
    val band = (height * TOP_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, 0, width, band)
    if (pixels.isEmpty()) return 0f
    return pixels.sumOf { relativeLuminance(it).toDouble() }.div(pixels.size).toFloat()
}

private fun relativeLuminance(argb: Int): Float {
    fun linear(channel: Int): Float {
        val s = channel / 255f
        return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    return 0.2126f * linear((argb shr 16) and 0xFF) + 0.7152f * linear((argb shr 8) and 0xFF) +
        0.0722f * linear(argb and 0xFF)
}

private fun PageSeed.toPalette(dark: Boolean): PagePalette = if (dark) PagePalette(
    // Deep enough that white text clears contrast on any sleeve, not so deep the hue is gone.
    background = dominant.withHsl({ adapted(it, 0.20f, 0.62f) }, { 0.13f }),
    // Follows the cover's foot, so the page reads as the artwork's blur carrying on.
    wash = edge.withHsl({ adapted(it, 0.18f, 0.58f) }, { it.coerceIn(0.14f, 0.24f) }),
    elevated = dominant.withHsl({ adapted(it, 0.20f, 0.62f) }, { 0.22f }),
    accent = vibrant.withHsl({ adapted(it, 0.55f, 1f) }, { it.coerceIn(0.62f, 0.78f) }),
    onBackground = Color.White,
    onBackgroundVariant = Color.White.copy(alpha = 0.80f),
    divider = Color.White.copy(alpha = 0.12f),
    topLuminance = topLuminance,
) else PagePalette(
    background = dominant.withHsl({ adapted(it, 0.14f, 0.50f) }, { 0.91f }),
    wash = edge.withHsl({ adapted(it, 0.12f, 0.46f) }, { it.coerceIn(0.78f, 0.90f) }),
    elevated = dominant.withHsl({ adapted(it, 0.14f, 0.50f) }, { 0.83f }),
    accent = vibrant.withHsl({ adapted(it, 0.55f, 1f) }, { it.coerceIn(0.30f, 0.44f) }),
    onBackground = Color.Black,
    onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
    divider = Color.Black.copy(alpha = 0.10f),
    topLuminance = topLuminance,
)

/** A grey stays grey: HSL's grey has hue zero, and lifting it would invent a maroon. */
private fun adapted(source: Float, minimum: Float, maximum: Float): Float {
    val s = source.coerceIn(0f, 1f)
    return if (s < CHROMATIC_SATURATION_THRESHOLD) s else s.coerceIn(minimum, maximum)
}

private fun Color.withHsl(saturation: (Float) -> Float, lightness: (Float) -> Float): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

// ---------------------------------------------------------------------------------------------
// Chrome tone
// ---------------------------------------------------------------------------------------------

/**
 * The theme for a strip of chrome that may sit on ground of the other tone: [dark] gives white
 * glyphs, light gives near-black ones, whatever the app's theme is, and a change crossfades so
 * the icons turn rather than cut.
 */
@Composable
internal fun ChromeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val reduceMotion = LocalReducedMotion.current
    val t by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(CHROME_TONE_MS),
        label = "chromeTone",
    )
    val ink = lerp(Color(0xFF111111), Color.White, t)
    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = ink,
        onBackground = ink,
        onSurfaceVariant = lerp(Color.Black.copy(alpha = 0.70f), Color.White.copy(alpha = 0.80f), t),
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}

private const val CHROME_TONE_MS = 320

// ---------------------------------------------------------------------------------------------
// Header glass
// ---------------------------------------------------------------------------------------------

/**
 * The film the page's buttons are made of: a frosted fill of the chrome's tone, a sheen along
 * the top and a hairline rim brighter on top. Follows [ChromeTheme] as it crossfades.
 */
@Composable
internal fun Modifier.headerGlass(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier {
    // 1 when the ink is white (dark chrome), 0 when it is near-black.
    val t = MaterialTheme.colorScheme.onSurface.luminance().coerceIn(0f, 1f)
    val fill = lerp(Color.White.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.30f), t)
    val rim = Brush.verticalGradient(
        listOf(
            lerp(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.30f), t),
            lerp(Color.Black.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f), t),
        )
    )
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = lerp(0.22f, 0.10f, t)),
        0.55f to Color.Transparent,
    )
    return this.clip(shape).background(fill).background(sheen).border(BorderStroke(0.75.dp, rim), shape)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** A round glass button floating over the page: back, Search. */
@Composable
internal fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    val dent = rememberPressDent()
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressSink(dent)
            .headerGlass(CircleShape)
            .pressShade(dent)
            .clickable(interactionSource = dent.interactions, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
    }
}

/** A capsule of header glass with an icon and, where there is room, a label: Shuffle. */
@Composable
private fun GlassPill(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dent = rememberPressDent()
    Row(
        modifier = modifier
            .pressSink(dent)
            .headerGlass(CircleShape)
            .pressShade(dent)
            .clickable(interactionSource = dent.interactions, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (label == null) 22.dp else 20.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Play: a wide pill in the page's accent, the one solid thing in the row. */
@Composable
private fun AccentPlayPill(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dent = rememberPressDent()
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    val label = stringResource(R.string.common_play)
    Row(
        modifier = modifier
            .pressSink(dent)
            .clip(CircleShape)
            .background(accent)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.6f to Color.Transparent))
            .pressShade(dent)
            .clickable(interactionSource = dent.interactions, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MeloIcons.PlayArrow, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/** The page's own search, opened from the play row's round button. */
internal class PlayRowSearch(val query: String, val onQueryChange: (String) -> Unit)

/**
 * A page's play row: Shuffle in glass, the round Search where the page has one, and Play in the
 * page's accent, which keeps half the row; Shuffle takes what is left, and names itself when the
 * Search circle is not there to crowd it. Search opens in place: the circle widens into a field
 * across the row while Shuffle and Play shrink away, and Back or ✕ folds it up and clears it.
 */
@Composable
internal fun DetailPlayRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    search: PlayRowSearch? = null,
    enabled: Boolean = true,
) {
    val dim = if (enabled) 1f else 0.45f
    val shuffleLabel = stringResource(R.string.common_shuffle)
    var open by rememberSaveable { mutableStateOf(search?.query?.isNotEmpty() == true) }
    if (search == null && open) open = false
    val reduceMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "playRowSearch",
    )
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun close() {
        focusManager.clearFocus()
        search?.onQueryChange?.invoke("")
        open = false
    }
    LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
    BackHandler(enabled = open) { close() }
    Layout(
        content = {
            Box(Modifier.graphicsLayer { alpha = dim * (1f - progress); scaleX = 1f - 0.4f * progress; scaleY = scaleX }) {
                GlassPill(
                    icon = MeloIcons.Shuffle,
                    label = if (search == null) shuffleLabel else null,
                    contentDescription = shuffleLabel,
                    onClick = onShuffle,
                    enabled = enabled && !open,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.graphicsLayer { alpha = dim * (1f - progress); scaleX = 1f - 0.4f * progress; scaleY = scaleX }) {
                AccentPlayPill(onClick = onPlay, enabled = enabled && !open, modifier = Modifier.fillMaxSize())
            }
            if (search != null) {
                SearchMorph(
                    query = search.query,
                    onQueryChange = search.onQueryChange,
                    progress = progress,
                    open = open,
                    focus = focus,
                    onOpen = { open = true },
                    onClose = ::close,
                    onDone = { focusManager.clearFocus() },
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(PLAY_ROW_HEIGHT),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = PLAY_ROW_HEIGHT.roundToPx()
        val gap = PLAY_ROW_GAP.roundToPx()
        val hasSearch = measurables.size == 3
        val circle = if (hasSearch) height else 0
        val playWidth = ((width - gap) / 2).coerceAtLeast(0)
        val shuffleWidth = (width - playWidth - gap - if (hasSearch) circle + gap else 0).coerceAtLeast(0)
        val shuffle = measurables[0].measure(Constraints.fixed(shuffleWidth, height))
        val play = measurables[1].measure(Constraints.fixed(playWidth, height))
        val circleX = shuffleWidth + gap
        val searchWidth = (circle + (width - circle) * progress).roundToInt()
        val searchX = (circleX * (1f - progress)).roundToInt()
        val field = if (hasSearch) measurables[2].measure(Constraints.fixed(searchWidth, height)) else null
        layout(width, height) {
            // Once the field covers the row, the buttons under it leave the layout, and the
            // accessibility tree with them.
            if (progress < 0.999f) {
                shuffle.place(0, 0)
                play.place(width - playWidth, 0)
            }
            field?.place(searchX, 0)
        }
    }
}

/** The round Search button and the field it opens into, one glass shape throughout. */
@Composable
private fun SearchMorph(
    query: String,
    onQueryChange: (String) -> Unit,
    progress: Float,
    open: Boolean,
    focus: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val dent = rememberPressDent()
    val hint = stringResource(R.string.playlist_filter_hint)
    val clearLabel = stringResource(R.string.playlist_filter_clear)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxSize()
            .pressSink(dent)
            .headerGlass(CircleShape)
            .pressShade(dent)
            .clickable(interactionSource = dent.interactions, indication = null, enabled = !open,
                role = Role.Button, onClick = onOpen)
            .then(if (open) Modifier else Modifier.semantics { contentDescription = hint }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(PLAY_ROW_HEIGHT), contentAlignment = Alignment.Center) {
            Icon(MeloIcons.Search, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = open,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .graphicsLayer { alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.6f), maxLines = 1)
                    inner()
                }
            },
        )
        if (progress > 0.02f) {
            Box(
                modifier = Modifier
                    .size(PLAY_ROW_HEIGHT)
                    .graphicsLayer { alpha = progress }
                    .clickable(enabled = open, role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MeloIcons.Close, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}


/**
 * The cover's lower edge melting into the page: a blurred copy of it, shown only in the bottom
 * band, so the sharp picture softens before the wash takes over. Android 12 and up; the plain
 * gradient carries it below that.
 */
@Composable
internal fun CoverEdgeBlur(model: Any?, targetSize: CoilSize, alpha: Float, modifier: Modifier = Modifier) {
    if (model == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    SmartImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        targetSize = targetSize,
        allowHardware = true,
        crossfadeDurationMillis = 0,
        alpha = alpha,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.42f to Color.Transparent, 0.78f to Color.Black), blendMode = BlendMode.DstIn)
            }
            .blur(28.dp, BlurredEdgeTreatment.Rectangle),
    )
}

/**
 * The page's list, recorded for the folded bar to frost; null where pages draw solid or the
 * user turned blur off, which also leaves the cover's edge unblurred.
 */
@Composable
internal fun rememberPageBackdrop(blurAllowed: Boolean): LayerBackdrop? =
    if (blurAllowed && pagesUnderGlass()) rememberLayerBackdrop() else null

/**
 * The folded bar: the rows scrolling under it, blurred and washed in the page colour, fading in
 * as the header folds. Solid where there is no blur.
 */
@Composable
internal fun Modifier.foldedBar(backdrop: LayerBackdrop?, color: Color, alpha: Float): Modifier {
    if (backdrop == null) return background(color.copy(alpha = alpha))
    val blurPx = with(LocalDensity.current) { FOLDED_BLUR.toPx() } * FOLDED_SCALE
    val base = color.copy(alpha = 1f)
    val wash = color.copy(alpha = 0.62f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = { blur(blurPx) },
        highlight = null,
        shadow = null,
        layerBlock = { this.alpha = alpha },
        // An opaque base, or the sharp rows show through the gaps in their blurred copy.
        onDrawBehind = { drawRect(base) },
        onDrawSurface = { drawRect(wash) },
        backdropScale = FOLDED_SCALE,
    )
}

private val FOLDED_BLUR = 24.dp
private const val FOLDED_SCALE = 0.33f

private val PLAY_ROW_HEIGHT = 50.dp
private val PLAY_ROW_GAP = 12.dp
