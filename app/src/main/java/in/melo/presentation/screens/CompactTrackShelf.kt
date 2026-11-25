package `in`.melo.presentation.screens

import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.ui.theme.artworkOutline
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import `in`.melo.ui.theme.label
import `in`.melo.ui.theme.secondaryLabel
import `in`.melo.ui.theme.separator
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.R
import `in`.melo.data.Music
import `in`.melo.presentation.components.SmartImage
import `in`.melo.presentation.components.SmartImageListTargetSize
import `in`.melo.presentation.components.artworkRim
import `in`.melo.ui.theme.MeloLayout

/** Four-row track shelf: each column pages sideways, leaving the next cover in view. */
@Composable
internal fun CompactTrackShelf(
    tracks: List<Music>,
    onClick: (Music) -> Unit,
    onOptions: (Music) -> Unit,
) {
    if (tracks.isEmpty()) return
    val pages = remember(tracks) { tracks.chunked(4) }
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = (maxWidth - MeloLayout.PageGutter * 2 - 24.dp).coerceAtLeast(160.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Start),
            contentPadding = PaddingValues(horizontal = MeloLayout.PageGutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(pages, key = { index, page -> "$index:${page.first().id}" }) { _, page ->
                Column(Modifier.width(width)) {
                    page.forEachIndexed { row, track ->
                        CompactTrackRow(track, { onClick(track) }, { onOptions(track) }, showSeparator = row < page.lastIndex)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTrackRow(track: Music, onClick: () -> Unit, onOptions: () -> Unit, showSeparator: Boolean) {
    // A song row as Apple Music lays it out: small rounded cover, title over artist, a
    // quiet horizontal "more" glyph, and a hairline under the text (not under the cover).
    val colors = MaterialTheme.colorScheme
    val separator = colors.separator
    val shape = MeloLayout.SmallArtworkShape
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowHeight)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onOptions),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmartImage(
            model = track.imageUrl, contentDescription = null,
            modifier = Modifier.size(ArtSize).artworkOutline(shape), shape = shape,
            targetSize = SmartImageListTargetSize,
        )
        Row(
            Modifier.weight(1f).heightIn(min = RowHeight).padding(start = MeloLayout.Space3)
                .drawBehind {
                    if (showSeparator) {
                        val y = size.height - MeloLayout.Hairline.toPx() / 2f
                        drawLine(separator, Offset(0f, y), Offset(size.width, y), MeloLayout.Hairline.toPx())
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, color = colors.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOptions, modifier = Modifier.size(MeloLayout.ControlSize)) {
                Icon(MeloIcons.MoreHoriz, stringResource(R.string.player_cd_more_options),
                    tint = colors.label, modifier = Modifier.size(MeloLayout.IconMedium))
            }
        }
    }
}

private val RowHeight = 60.dp
private val ArtSize = 48.dp
