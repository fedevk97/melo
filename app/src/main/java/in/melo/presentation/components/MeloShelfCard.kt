package `in`.melo.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.artworkOutline
import `in`.melo.ui.theme.label
import `in`.melo.ui.theme.secondaryLabel

/**
 * The one card for a piece of music in a shelf or grid: square artwork with the hairline
 * outline, then a title and a grey second line straight on the page, no card behind
 * them. The artwork shrinks a little under the finger.
 *
 * [artwork] draws the picture inside the already clipped, outlined square; [overlay]
 * draws over it (selection marks, badges). Artists pass a [shape] of
 * [MeloLayout.ArtistShape] and [centered] text.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeloShelfCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = MeloLayout.ArtworkShape,
    centered: Boolean = false,
    titleLines: Int = 1,
    overlay: @Composable BoxScope.() -> Unit = {},
    artwork: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier
            .meloPress(interactions)
            .combinedClickable(
                interactionSource = interactions, indication = null, role = Role.Button,
                onClick = onClick, onLongClick = onLongClick,
            ),
        verticalArrangement = Arrangement.spacedBy(CardTextGap),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .artworkOutline(shape),
            contentAlignment = Alignment.Center,
        ) {
            artwork()
            overlay()
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                title, Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.label,
                maxLines = titleLines, overflow = TextOverflow.Ellipsis, textAlign = align,
            )
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondaryLabel,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
            )
        }
    }
}

/** Space between a card's artwork and its title. */
private val CardTextGap = 6.dp
