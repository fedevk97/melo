package `in`.melo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {
    private val seeds = listOf(
        Color(0xFFE53935), Color(0xFFFFEB3B), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA),
        Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF808080), Color(0xFF3E2723), Color(0xFF00E5FF),
    )

    @Test fun everyTextColourIsReadableOnItsPageInBothThemes() {
        for (dark in listOf(true, false)) for (seed in seeds) {
            val p = artworkPaletteFrom(seed, dark)
            val where = "seed=${seed.value.toString(16)} dark=$dark"
            assertTrue("title $where", contrast(p.onBackground, p.background) >= 7.0)
            assertTrue("second line $where", contrast(p.onBackgroundVariant, p.background) >= MIN_TEXT_CONTRAST)
            assertTrue("accent $where", contrast(p.accent, p.background) >= MIN_TEXT_CONTRAST)
            assertTrue("play label $where", contrast(p.onAccent, p.accent) >= 3.0)
        }
    }

    @Test fun pagesAreNearBlackInDarkAndNearWhiteInLight() {
        for (seed in seeds) {
            assertTrue(Hct.fromInt(artworkPaletteFrom(seed, true).background.toArgb()).tone < 15)
            assertTrue(Hct.fromInt(artworkPaletteFrom(seed, false).background.toArgb()).tone > 90)
        }
    }

    // A pure grey still reads as about 2.5 chroma in HCT, so "grey" is under 4.
    @Test fun greyArtworkGivesAGreyPage() {
        val p = artworkPaletteFrom(Color(0xFF7A7A7C), dark = true)
        assertTrue(Hct.fromInt(p.background.toArgb()).chroma < 4)
        assertTrue(Hct.fromInt(p.accent.toArgb()).chroma < 4)
    }

    @Test fun colourfulArtworkKeepsItsHue() {
        val seed = Hct.fromInt(0xFF1E88E5.toInt())
        val accent = Hct.fromInt(artworkPaletteFrom(Color(0xFF1E88E5), dark = true).accent.toArgb())
        assertTrue(kotlin.math.abs(accent.hue - seed.hue) < 15)
        assertTrue(accent.chroma > 20)
    }

}
