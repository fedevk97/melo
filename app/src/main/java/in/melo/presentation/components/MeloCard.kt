package `in`.melo.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout

/** A quiet, rimmed content surface with no tonal or shadow elevation. */
@Composable
fun MeloCard(
    modifier: Modifier = Modifier,
    shape: Shape = MeloLayout.CardShape,
    color: Color = pageCardColor(MaterialTheme.colorScheme.surface),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Inside a grouped list the card is just a row: no rim, no scale, a highlight on press.
    val grouped = LocalMeloGrouped.current
    val shape = if (grouped) androidx.compose.ui.graphics.RectangleShape else shape
    val panelModifier = (if (onClick != null && !grouped) modifier.meloPress(interactionSource) else modifier)
        .meloPanel(shape, color)
    val inner: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(LocalMeloGrouped provides false) { content() }
    }
    if (onClick == null) {
        Surface(
            modifier = panelModifier,
            shape = shape,
            color = Color.Transparent,
        ) { inner() }
    } else {
        Surface(
            onClick = onClick,
            modifier = panelModifier,
            enabled = true,
            shape = shape,
            color = Color.Transparent,
            interactionSource = interactionSource,
        ) { inner() }
    }
}
