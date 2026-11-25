/*
 * Melo's menus and alerts: an iOS-style panel over a blurred, dimmed app.
 *
 * Menus and alerts are windows of their own, so the frost comes from the
 * system's cross-window blur (Android 12+) under a translucent slab instead.
 * Solid style, older Android, or blur turned off by the system (battery saver)
 * get the opaque slab.
 */
package `in`.melo.ui.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * A menu panel's colours: a charcoal or frost slab with its own label greys,
 * rather than the theme's surface roles, which were tuned for pages, not for a
 * panel floating over them.
 */
@Immutable
data class MeloMenuColors(
    val dark: Boolean,
    val fill: Color,
    val rim: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    /** The rounded wash under the row a finger is on. */
    val pressed: Color,
)

private val DarkMenu = MeloMenuColors(
    dark = true,
    fill = Color(0xFF1C1C1E),
    rim = Color.White.copy(alpha = 0.10f),
    label = Color.White,
    secondaryLabel = Color.White.copy(alpha = 0.70f),
    separator = Color.White.copy(alpha = 0.10f),
    pressed = Color.White.copy(alpha = 0.10f),
)

private val LightMenu = MeloMenuColors(
    dark = false,
    fill = Color(0xFFF6F6F7),
    rim = Color.Black.copy(alpha = 0.06f),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF1A1A1A).copy(alpha = 0.55f),
    separator = Color.Black.copy(alpha = 0.09f),
    pressed = Color.Black.copy(alpha = 0.07f),
)

/** The panel colours for the theme in force. */
@Composable
@ReadOnlyComposable
fun meloMenuColors(): MeloMenuColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkMenu else LightMenu

/** How hard the app behind is blurred while a menu, or an alert, is up. */
private val MenuBlur = 14.dp
private val AlertBlur = 18.dp

/** A menu's silhouette, and an alert's. */
val MeloMenuShape = RoundedCornerShape(26.dp)
val MeloAlertShape = RoundedCornerShape(28.dp)

private val MenuMinWidth = 220.dp
private val MenuMaxWidth = 280.dp
private val AlertMaxWidth = 320.dp

/**
 * True when a popup window can be frosted: the style isn't Solid, the device
 * has cross-window blur (Android 12), and the system hasn't switched it off.
 */
@Composable
fun rememberPopupFrost(): Boolean {
    val solid = LocalMeloSurface.current.style == MeloSurfaceStyle.SOLID
    // Over the recorded pages the panel is the app's own glass, sampling them itself: the
    // window behind is left sharp under the scrim.
    if (LocalPopupBackdrop.current != null) return false
    val context = LocalContext.current
    val available = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
    return !solid && available
}

/**
 * Called inside a full-screen [Dialog]: drops the system's dim (the popup draws
 * its own scrim) and, when [frost], blurs the app behind by [blurRadius], scaled
 * by [progress] so the blur comes and goes with the panel.
 */
@Composable
fun PopupWindowEffects(frost: Boolean, blurRadius: Dp, progress: () -> Float = { 1f }) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    LaunchedEffect(window, frost) {
        window.setDimAmount(0f)
        if (!frost || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        snapshotFlow { (progress().coerceIn(0f, 1f) * radiusPx).roundToInt() }
            .distinctUntilChanged()
            .collect { radius -> window.attributes = window.attributes.also { it.blurBehindRadius = radius } }
    }
}

/**
 * The panel a menu, alert or popup card is drawn on. Over the recorded pages it is the
 * app's chosen material, so Liquid Glass bends and blurs the page under every menu and
 * Frost frosts it. Frosted without them: a
 * translucent slab over the blurred app, lit along its top. Otherwise the opaque slab,
 * with the shadow it casts (a shadow would show through the frosted one).
 */
@Composable
fun Modifier.meloPopupPanel(shape: Shape, frost: Boolean, colors: MeloMenuColors = meloMenuColors()): Modifier {
    val pages = LocalPopupBackdrop.current
    if (pages != null && LocalMeloSurface.current.style != MeloSurfaceStyle.SOLID && isGlassSupported()) {
        return clip(shape).meloGlass(shape, carriesText = true, dark = colors.dark, backdrop = pages, underlay = menuScrimColor(colors.dark))
    }
    val base = if (frost) this else shadow(
        elevation = 24.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.30f),
        clip = false,
    )
    val fill = if (frost) colors.fill.copy(alpha = if (colors.dark) 0.72f else 0.78f) else colors.fill
    return base
        .clip(shape)
        .background(fill)
        .then(if (frost) Modifier.background(PanelSheen) else Modifier)
        .border(0.5.dp, colors.rim, shape)
}

private val PanelSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.07f),
    0.4f to Color.Transparent,
)

/**
 * A menu hung off the button it's composed next to (inside the button's Box,
 * as a DropdownMenu would be). Its trailing edge lines up with the button's;
 * it grows out of the button on a spring and shrinks back into it.
 */
@Composable
fun MeloMenuPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    // A node of no size in the button's Box: its parent's bounds are the button's.
    Layout(
        modifier = Modifier.onGloballyPositioned { anchor = it.parentLayoutCoordinates?.boundsInWindow() },
        content = {},
    ) { _, _ -> layout(0, 0) {} }

    val progress = remember { Animatable(0f) }
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            showing = true
            progress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 520f))
        } else if (showing) {
            progress.animateTo(0f, tween(150, easing = FastOutLinearInEasing))
            showing = false
        }
    }
    if (!expanded && !showing) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val frost = rememberPopupFrost()
        PopupWindowEffects(frost, MenuBlur) { progress.value }
        val colors = meloMenuColors()
        val scrim = menuScrimColor()
        val insets = WindowInsets.safeDrawing
        var overlayOffset by remember { mutableStateOf(Offset.Zero) }
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayOffset = it.positionInWindow() },
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                        .background(scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = expanded,
                            onClick = onDismiss,
                        ),
                )
                CompositionLocalProvider(LocalContentColor provides colors.label) {
                    Column(
                        Modifier
                            // Taps between rows land on the panel, not the scrim under it.
                            .pointerInput(Unit) {}
                            .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                            .width(IntrinsicSize.Max)
                            .meloPopupPanel(MeloMenuShape, frost, colors)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        content = content,
                    )
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val margin = 12.dp.roundToPx()
            val insetTop = insets.getTop(this)
            val top = insetTop + 8.dp.roundToPx()
            val bottom = height - insets.getBottom(this) - 12.dp.roundToPx()
            val scrimPlaceable = measurables[0].measure(Constraints.fixed(width, height))
            val panel = measurables[1].measure(
                Constraints(
                    maxWidth = (width - margin * 2).coerceAtLeast(0),
                    maxHeight = (bottom - top).coerceAtLeast(0),
                ),
            )
            val bounds = anchor?.translate(-overlayOffset)
            val right = bounds?.right?.roundToInt() ?: (width - margin)
            val panelX = (right - panel.width).coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin))
            val panelY = hungMenuTop(
                anchorTop = bounds?.top?.roundToInt(),
                anchorBottom = bounds?.bottom?.roundToInt(),
                panelHeight = panel.height,
                insetTop = insetTop,
                top = top,
                bottom = bottom,
                screenHeight = height,
            )
            // Grows from the button's centre (or the top-right corner, with no button).
            val originX = bounds?.let { ((it.center.x - panelX) / panel.width).coerceIn(0f, 1f) } ?: 1f
            val originY = bounds?.let { ((it.center.y - panelY) / panel.height).coerceIn(0f, 1f) } ?: 0f
            layout(width, height) {
                scrimPlaceable.place(0, 0)
                panel.placeWithLayer(panelX, panelY) {
                    val p = progress.value
                    val scale = lerp(0.5f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                    alpha = (p / 0.35f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(originX, originY)
                }
            }
        }
    }
}

/**
 * Where a menu hung off a button goes: for a button in the upper half it hangs
 * down from the button's top; lower down it settles over the button from below.
 * Never past [top] or [bottom].
 */
internal fun hungMenuTop(
    anchorTop: Int?,
    anchorBottom: Int?,
    panelHeight: Int,
    insetTop: Int,
    top: Int,
    bottom: Int,
    screenHeight: Int,
): Int {
    if (anchorTop == null || anchorBottom == null) return (bottom - panelHeight).coerceAtLeast(top)
    val ceiling = anchorTop.coerceIn(minOf(insetTop, top), top)
    val lowest = (bottom - panelHeight).coerceAtLeast(ceiling)
    val hangsDown = (anchorTop + anchorBottom) / 2f < screenHeight / 2f
    val preferred = if (hangsDown) anchorTop else lowest
    val coverLow = anchorBottom - panelHeight
    return preferred.coerceIn(minOf(coverLow, anchorTop), anchorTop).coerceIn(ceiling, lowest)
}

/**
 * An alert in Melo's material, taking the same arguments as Material's
 * AlertDialog so it drops in where one was: centred title, the message, and the
 * buttons side by side under a hairline, each in its own half.
 *
 * [shape], [containerColor] and [tonalElevation] are accepted and ignored: the
 * card is always the popup panel.
 */
@Composable
fun MeloAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val frost = rememberPopupFrost()
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
        PopupWindowEffects(frost, AlertBlur) { progress.value }
        val colors = meloMenuColors()
        val scrim = menuScrimColor()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = properties.dismissOnClickOutside,
                        onClick = onDismissRequest,
                    ),
            )
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier
                        .pointerInput(Unit) {}
                        .widthIn(max = AlertMaxWidth)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = progress.value
                            val scale = lerp(1.12f, 1f, p)
                            scaleX = scale
                            scaleY = scale
                            alpha = p.coerceIn(0f, 1f)
                        }
                        .meloPopupPanel(MeloAlertShape, frost, colors),
                ) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (icon != null) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor.orElse(colors.label)) {
                                Box(Modifier.padding(bottom = 12.dp)) { icon() }
                            }
                        }
                        if (title != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides titleContentColor.orElse(colors.label),
                                LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                ),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().padding(bottom = if (text != null) 8.dp else 0.dp),
                                    contentAlignment = Alignment.Center,
                                ) { title() }
                            }
                        }
                        if (text != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides textContentColor.orElse(colors.secondaryLabel),
                                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                            ) {
                                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { text() }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (dismissButton != null) {
                            Box(
                                Modifier.weight(1f).heightIn(min = 52.dp).padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) { dismissButton() }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .then(
                                    if (dismissButton != null) Modifier.drawBehind {
                                        drawLine(colors.separator, Offset(0f, 0f), Offset(0f, size.height), 0.5.dp.toPx())
                                    } else Modifier,
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) { confirmButton() }
                    }
                }
            }
        }
    }
}

private fun Color.orElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
