package `in`.melo.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import `in`.melo.presentation.components.OptimizedAlbumArt
import coil.size.Size
import `in`.melo.presentation.components.artworkBottomFade
import kotlin.math.roundToInt

/** The pieces that travel between the player and the lyrics header. */
internal enum class MorphPart { ART, TITLE, ARTIST }

/** How long the player takes to fold into the lyrics header, and to unfold back out of it. */
internal const val LYRICS_MORPH_MS = 420

/** The lyrics header's cover, where the sleeve lands. */
internal val LyricsHeaderArtCorner = 10.dp

/**
 * One movement between the player and its lyrics: the cover shrinks from the player into the
 * lyrics header's thumbnail as the credits slide up beside it, and both go back on the way out.
 * Each end reports where its cover, title and artist sit; while [progress] is between the two
 * ends, both ends hide theirs and the overlay draws the travelling copies.
 */
@Stable
internal class LyricsMorph(val progress: Animatable<Float, AnimationVector1D>) {
    private val player = Array(MorphPart.entries.size) { mutableStateOf<Rect?>(null) }
    private val header = Array(MorphPart.entries.size) { mutableStateOf<Rect?>(null) }

    /** The player cover's corner: none on a full-screen cover, the card's own on a card. */
    var playerArtCorner by mutableStateOf(0.dp)

    /** Whether the player cover dissolves at its foot, as the full-screen one does. */
    var playerArtFades by mutableStateOf(false)

    fun playerRect(part: MorphPart): Rect? = player[part.ordinal].value
    fun headerRect(part: MorphPart): Rect? = header[part.ordinal].value
    fun setPlayer(part: MorphPart, rect: Rect) { if (player[part.ordinal].value != rect) player[part.ordinal].value = rect }
    fun setHeader(part: MorphPart, rect: Rect) { if (header[part.ordinal].value != rect) header[part.ordinal].value = rect }

    /** Read only at layout and draw, so the travel never recomposes the screens around it. */
    fun value(): Float = progress.value
}

internal val LocalLyricsMorph = staticCompositionLocalOf<LyricsMorph?> { null }

/** Marks [part] on the player: reported where it sits, and hidden while its copy travels. */
internal fun Modifier.lyricsMorphPlayer(part: MorphPart, rectOf: (Rect) -> Rect = { it }): Modifier = composed {
    val morph = LocalLyricsMorph.current ?: return@composed Modifier
    onGloballyPositioned { morph.setPlayer(part, rectOf(it.boundsInWindow())) }
        .graphicsLayer { alpha = if (morph.value() > 0f) 0f else 1f }
}

/** Marks [part] in the lyrics header: reported where it sits, and shown only once its copy has landed. */
internal fun Modifier.lyricsMorphHeader(part: MorphPart): Modifier = composed {
    val morph = LocalLyricsMorph.current ?: return@composed Modifier
    onGloballyPositioned { morph.setHeader(part, it.boundsInWindow()) }
        .graphicsLayer { alpha = if (morph.value() < 1f) 0f else 1f }
}

/**
 * The travelling cover and credits, drawn over both screens while the lyrics open or close.
 * Composed only during the travel; each frame of it is a relayout of these three alone.
 */
@Composable
internal fun LyricsMorphOverlay(
    morph: LyricsMorph,
    artModel: Any?,
    /** The player cover's own request size, so the copy is the same cached picture. */
    artTargetSize: Size,
    title: String,
    artist: String,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    titleInk: Color,
    artistInk: Color,
    headerTitleStyle: TextStyle,
    headerArtistStyle: TextStyle,
    headerInk: Color,
    headerArtistInk: Color,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val travelling by remember { derivedStateOf { morph.progress.value > 0f && morph.progress.value < 1f } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        if (!travelling) return@Box
        fun at(part: MorphPart): Rect? {
            val from = morph.playerRect(part) ?: return null
            val to = morph.headerRect(part) ?: from
            return lerp(from, to, morph.value()).translate(-origin)
        }
        OptimizedAlbumArt(
            uri = artModel,
            title = title,
            targetSize = artTargetSize,
            modifier = Modifier
                .placedAt { at(MorphPart.ART) }
                .graphicsLayer {
                    val t = morph.value()
                    clip = true
                    shape = RoundedCornerShape(lerp(morph.playerArtCorner.toPx(), LyricsHeaderArtCorner.toPx(), t))
                }
                .then(
                    if (morph.playerArtFades) Modifier.artworkBottomFade(floor = { morph.value() }) else Modifier
                ),
        )
        TravellingText(title, titleStyle, headerTitleStyle, titleInk, headerInk, morph, MorphPart.TITLE) { at(it) }
        TravellingText(artist, artistStyle, headerArtistStyle, artistInk, headerArtistInk, morph, MorphPart.ARTIST) { at(it) }
    }
}

/**
 * A line of the credits on its way: set in the player's type and scaled toward the header's,
 * its colour moving from the player's ink to the header's.
 */
@Composable
private fun TravellingText(
    text: String,
    from: TextStyle,
    to: TextStyle,
    fromInk: Color,
    toInk: Color,
    morph: LyricsMorph,
    part: MorphPart,
    rect: (MorphPart) -> Rect?,
) {
    val endScale = if (from.fontSize.isSp && to.fontSize.isSp && from.fontSize.value > 0f) {
        to.fontSize.value / from.fontSize.value
    } else 1f
    Text(
        text = text,
        style = from,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        modifier = Modifier
            .layout { measurable, _ ->
                val r = rect(part)
                if (r == null) return@layout layout(0, 0) {}
                val t = morph.value()
                val scale = lerp(1f, endScale, t)
                val width = (r.width / scale).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(r.left.roundToInt(), r.top.roundToInt()) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
            .colorLayer { lerp(fromInk, toInk, morph.value()) },
    )
}

/** Paints the text drawn under it in [color], read at draw so the colour can move every frame. */
private fun Modifier.colorLayer(color: () -> Color): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(color(), blendMode = BlendMode.SrcIn)
        }

/** Places the content at [rect] each frame, at its size; nothing while there is no rect yet. */
private fun Modifier.placedAt(rect: () -> Rect?): Modifier = layout { measurable, _ ->
    val r = rect() ?: return@layout layout(0, 0) {}
    val w = r.width.roundToInt().coerceAtLeast(1)
    val h = r.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
}
