package `in`.melo.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every measurement a screen needs, named once.
 *
 * A screen should not write its own `dp`: when a row, a card or a heading takes its
 * sizes from here, it lines up with every other one in the app, and a change made
 * here moves all of them together.
 */
object MeloLayout {
    // A 4dp ladder. Gaps are always one of these.
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space12 = 48.dp

    /** The left and right inset of every page's content. */
    val PageGutter = Space4

    /**
     * The inset of the floating bars (mini player, tab bar, top bar controls). Equal to
     * [PageGutter] so the bars' edges line up with the content scrolling under them.
     */
    val BarGutter = PageGutter

    /** Gap between items in a shelf or grid. */
    val ItemGap = Space2

    /** Gap above a section header, between one shelf and the next. */
    val SectionGap = Space8

    /** Gap between a section header and its content. */
    val HeaderGap = Space2

    /** Space between the status bar and a page's large title. */
    val LargeTitleTop = Space2

    // Corners. Artwork stays sharp-ish; the larger the surface, the rounder it gets.
    val CornerTiny = 4.dp
    val CornerSmall = 6.dp
    val CornerArtwork = 8.dp
    val CornerCard = 12.dp
    val CornerPanel = 20.dp
    val CornerSheet = 28.dp

    val SmallArtworkShape = RoundedCornerShape(CornerSmall)
    val ArtworkShape = RoundedCornerShape(CornerArtwork)
    val CardShape = RoundedCornerShape(CornerCard)
    val PanelShape = RoundedCornerShape(CornerPanel)
    val SheetShape = RoundedCornerShape(topStart = CornerSheet, topEnd = CornerSheet)
    val PillShape = RoundedCornerShape(percent = 50)
    val ArtistShape = CircleShape

    /** Touch targets. Never smaller than [ControlSize], whatever the glyph inside. */
    val ButtonLarge = 56.dp
    val ButtonMedium = 48.dp
    val ControlSize = 48.dp
    val ControlVisual = 40.dp

    // Artwork sizes.
    val ArtTiny = 40.dp
    val ArtCompact = 48.dp
    val ArtList = 56.dp
    val ArtHero = 64.dp

    /** A song row's height: [ArtList] plus [Space2] above and below. */
    val RowMinHeight = 72.dp

    /** Gap between a row's artwork and its text. */
    val RowArtGap = Space3

    /** Where a divider under an artwork row starts: at the row's text, clear of the artwork. */
    val RowDividerInset = PageGutter + ArtList + RowArtGap

    /**
     * A card's width in a sideways shelf: a phone shows two whole cards and the edge of
     * a third, which is how a row says it scrolls without showing half a card.
     */
    val ShelfCardWidth = 150.dp

    /** Smallest card in a library grid; the grid fits as many as this allows. */
    val GridCardMinWidth = 140.dp

    /**
     * How wide the floating bars ever get. Clear of the widest phone, so on a phone it
     * does nothing; on a tablet the bars stop growing and centre instead of stretching.
     */
    val FloatingBarMaxWidth = 440.dp

    /** Thickness of hairlines: dividers and the artwork outline. */
    val Hairline = 0.5.dp

    val IconSmall = 16.dp
    val IconMedium = 20.dp
    val IconDefault = 24.dp
    val IconLarge = 32.dp
}

/**
 * The hairline round a piece of artwork (see [artworkOutline]), so a cover the same
 * colour as the page still reads as a square. Use with the artwork's own [shape].
 */
@Composable
fun Modifier.artworkOutline(shape: Shape = MeloLayout.ArtworkShape, width: Dp = 1.dp): Modifier =
    border(width, MaterialTheme.colorScheme.artworkOutline, shape)
