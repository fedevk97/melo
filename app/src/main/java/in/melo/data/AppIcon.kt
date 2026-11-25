package `in`.melo.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import `in`.melo.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The icons Melo can wear on the home screen. Each is a launcher entry in the manifest pointing at
 * the main screen; exactly one is switched on at a time, and that one is the icon the launcher shows.
 *
 * Each icon also carries the colours of its M, so the mark inside the app and the glow behind an
 * empty Home match whichever icon is on the home screen.
 */
enum class AppIcon(
    private val entry: String,
    @param:DrawableRes @get:DrawableRes val preview: Int,
    @param:StringRes @get:StringRes val label: Int,
    /** The M's top and bottom colours on a dark background. */
    val markDark: Pair<Color, Color>,
    /** The M's top and bottom colours on a light background. */
    val markLight: Pair<Color, Color>,
    /** The glow behind Home when nothing is playing. */
    val ambient: Color,
) {
    MINT(
        "LauncherMint", R.drawable.app_icon_preview, R.string.app_icon_mint,
        markDark = Color(0xFFCBE4D8) to Color(0xFF8FE3BC),
        markLight = Color(0xFF3F9E7A) to Color(0xFF1C8A5E),
        ambient = Color(0xFF2FCB8E),
    ),
    ICE(
        "LauncherIce", R.drawable.app_icon_preview_ice, R.string.app_icon_ice,
        markDark = Color(0xFFD0E3F1) to Color(0xFF80BDEB),
        markLight = Color(0xFF4A86BD) to Color(0xFF1F6DB3),
        ambient = Color(0xFF3B8EEB),
    ),
    AMETHYST(
        "LauncherAmethyst", R.drawable.app_icon_preview_amethyst, R.string.app_icon_amethyst,
        markDark = Color(0xFFD6CBE8) to Color(0xFFAA8CE4),
        markLight = Color(0xFF8063C2) to Color(0xFF6542B9),
        ambient = Color(0xFF8B5CF0),
    ),
    EMERALD(
        "LauncherEmerald", R.drawable.app_icon_preview_emerald, R.string.app_icon_emerald,
        markDark = Color(0xFF6FD3A2) to Color(0xFF2DB774),
        markLight = Color(0xFF207C52) to Color(0xFF199559),
        ambient = Color(0xFF1F9E62),
    ),
    MONO(
        "LauncherMono", R.drawable.app_icon_preview_mono, R.string.app_icon_mono,
        markDark = Color(0xFFFFFFFF) to Color(0xFFE4E4E6),
        markLight = Color(0xFF2A2A2D) to Color(0xFF0B0B0C),
        ambient = Color(0xFF7D8594),
    );

    private fun component(context: Context) = ComponentName(context.packageName, "in.melo.$entry")

    companion object {
        /** The icon in the manifest that's on until another is chosen. */
        val Default = MINT

        private val live = MutableStateFlow<AppIcon?>(null)

        /** The icon on the home screen, updated the moment another is chosen. */
        fun live(context: Context): StateFlow<AppIcon?> {
            if (live.value == null) live.value = current(context)
            return live.asStateFlow()
        }

        /** The icon the launcher is showing now. */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }?.let { return it }
            return Default
        }

        /**
         * Puts [icon] on the home screen. The new entry goes on before the others go off, so there's
         * never a moment with no way into Melo from the launcher.
         */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            live.value = icon
        }
    }
}
