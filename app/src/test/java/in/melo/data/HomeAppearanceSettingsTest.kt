package `in`.melo.data

import `in`.melo.data.preferences.AppSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeAppearanceSettingsTest {
    @Test fun existingSettingsKeepTheirDialChoicesAndReceiveAppearanceDefaults() {
        val legacy = JSONObject("""{"homeDialLabels":false,"homeDialRows":3,"homeArtistDialEnabled":true}""")
        val settings = AppSettings.fromJson(legacy)
        assertFalse(settings.homeDialLabels)
        assertEquals(3, settings.homeDialRows)
        assertTrue(settings.homeArtistDialEnabled)
        assertTrue(settings.homeGradients)
        assertTrue(settings.playerArtworkGradient)
    }

    @Test fun disabledEffectsSurviveSavingAndReloading() {
        val settings = AppSettings(homeGradients = false, playerArtworkGradient = false)
        val restored = AppSettings.fromJson(JSONObject(settings.toJson().toString()))
        assertFalse(restored.homeGradients)
        assertFalse(restored.playerArtworkGradient)
        assertEquals(settings.homeDialRows, restored.homeDialRows)
        assertEquals(settings.homeDialTileStyle, restored.homeDialTileStyle)
    }

    @Test fun removedManualRecommendationCountryIsIgnored() {
        val settings = AppSettings.fromJson(JSONObject("""{"youtubeContentCountry":"US","language":"fr"}"""))
        assertEquals("fr", settings.language)
        assertFalse(settings.toJson().has("youtubeContentCountry"))
    }
}
