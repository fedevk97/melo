package `in`.melo.presentation.components

import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.presentation.utils.LocalReducedMotion
import `in`.melo.ui.theme.MeloAlpha
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.secondaryLabel
import `in`.melo.ui.theme.label

@Composable
fun MeloListRow(
    title: String,
    subtitle: String,
    imageModel: Any?,
    artContentDescription: String?,
    playContentDescription: String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    artist: Boolean = false,
    fallbackPainter: Painter? = null,
    fallbackContentDescription: String? = null,
    playContainerColor: Color = MaterialTheme.colorScheme.primary,
    playContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSelectionToggle: () -> Unit = {},
) {
    val reducedMotion = LocalReducedMotion.current
    val selectionScale by animateFloatAsState(
        targetValue = if (!reducedMotion && isSelected) 0.98f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "listRowSelectionScale",
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 180),
        label = "listRowSelectionBorder",
    )
    val artworkShape: Shape = if (artist) MeloLayout.ArtistShape else MeloLayout.ArtworkShape
    val interactionSource = remember { MutableInteractionSource() }

    // A flat row, as Apple Music lists results: no card or rim, a pressed fill only while held.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .clip(MeloLayout.CardShape)
            .border(selectionBorderWidth, MaterialTheme.colorScheme.primary, MeloLayout.CardShape)
            .semantics { selected = isSelectionMode && isSelected }
            .combinedClickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = { if (isSelectionMode) onSelectionToggle() else onClick() },
                onLongClick = onLongClick,
            )
            .meloPress(interactionSource),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = MeloLayout.RowMinHeight)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MeloLayout.Space2, vertical = MeloLayout.Space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                if (imageModel == null && fallbackPainter != null) {
                    Image(
                        painter = fallbackPainter,
                        contentDescription = fallbackContentDescription,
                        modifier = Modifier.size(MeloLayout.ArtList)
                            .clip(artworkShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(MeloLayout.Space2),
                    )
                } else {
                    SmartImage(
                        model = imageModel,
                        contentDescription = artContentDescription,
                        targetSize = SmartImageListTargetSize,
                        shape = artworkShape,
                        modifier = Modifier.size(MeloLayout.ArtList),
                    )
                }
                Spacer(Modifier.width(MeloLayout.Space3))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MeloIconButton(
                    icon = MeloIcons.PlayArrow,
                    contentDescription = playContentDescription,
                    onClick = onPlayClick,
                    containerColor = playContainerColor,
                    contentColor = playContentColor,
                )
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(MeloLayout.Space2)
                        .size(MeloLayout.IconDefault)
                        .background(MaterialTheme.colorScheme.primary, MeloLayout.ArtistShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
