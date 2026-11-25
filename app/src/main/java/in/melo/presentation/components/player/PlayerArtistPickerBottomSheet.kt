package `in`.melo.presentation.components.player

import `in`.melo.ui.theme.controlFill
import `in`.melo.ui.theme.selectedFill
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.ui.theme.MeloAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import `in`.melo.presentation.components.MeloBottomSheet as ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import `in`.melo.R
import `in`.melo.data.model.Artist
import `in`.melo.data.model.Song
import `in`.melo.presentation.components.SmartImage
import `in`.melo.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import `in`.melo.ui.theme.MeloLayout

private data class PlayerArtistShortcutItem(
    val artist: Artist,
    val isPrimary: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerArtistPickerBottomSheet(
    song: Song,
    artists: List<Artist>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onArtistClick: (Artist) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shortcutItems = remember(song.id, song.artistId, song.artists, artists) {
        val primaryArtist = song.primaryArtist
        val computedItems = artists.mapIndexed { index, artist ->
            val isPrimary = when {
                primaryArtist.id != 0L && primaryArtist.id != -1L -> artist.id == primaryArtist.id
                primaryArtist.name.isNotBlank() -> artist.name.equals(primaryArtist.name, ignoreCase = true)
                else -> index == 0
            }
            PlayerArtistShortcutItem(
                artist = artist,
                isPrimary = isPrimary
            )
        }

        val hasPrimary = computedItems.any { it.isPrimary }
        val normalizedItems = if (hasPrimary || computedItems.isEmpty()) {
            computedItems
        } else {
            computedItems.mapIndexed { index, item ->
                item.copy(isPrimary = index == 0)
            }
        }
        normalizedItems.sortedByDescending { it.isPrimary }
    }

    val countLabel = when (shortcutItems.size) {
        1 -> stringResource(R.string.artist_picker_count_single)
        else -> stringResource(R.string.artist_picker_count_multiple, shortcutItems.size)
    }

    `in`.melo.presentation.components.MeloBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = MeloAlpha.Disabled)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.artist_picker_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MeloLayout.PanelShape),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                shortcutItems.forEachIndexed { index, item ->
                    PlayerArtistShortcutCard(
                        artist = item.artist,
                        isPrimary = item.isPrimary,
                        shape = artistShortcutShape(
                            index = index,
                            count = shortcutItems.size
                        ),
                        onClick = { onArtistClick(item.artist) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlayerArtistShortcutCard(
    artist: Artist,
    isPrimary: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isPrimary) {
        colorScheme.selectedFill
    } else {
        colorScheme.controlFill
    }
    val contentColor = if (isPrimary) {
        colorScheme.onSecondaryContainer
    } else {
        colorScheme.onSurface
    }
    val labelContainerColor = if (isPrimary) {
        colorScheme.tertiary
    } else {
        colorScheme.surfaceContainerHighest
    }
    val labelContentColor = if (isPrimary) {
        colorScheme.onTertiary
    } else {
        colorScheme.onSurfaceVariant
    }
    val avatarBackground = if (isPrimary) {
        colorScheme.onSecondaryContainer.copy(alpha = MeloAlpha.Divider)
    } else {
        colorScheme.surfaceContainerHighest
    }
    val trailingContainerColor = contentColor.copy(alpha = MeloAlpha.Divider)
    val avatarSize = 52.dp

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeloLayout.Space4, vertical = MeloLayout.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space4)
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(avatarBackground),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    placeholderResId = R.drawable.rounded_artist_24,
                    errorResId = R.drawable.rounded_artist_24,
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    targetSize = Size(180, 180),
                    placeHolderBackgroundColor = Color.Transparent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MeloLayout.Space2)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    color = labelContainerColor,
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(
                            if (isPrimary) {
                                R.string.artist_picker_primary_label
                            } else {
                                R.string.artist_picker_shortcut_label
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelContentColor,
                        modifier = Modifier.padding(horizontal = MeloLayout.Space3, vertical = MeloLayout.Space2)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(trailingContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MeloIcons.ArrowForward,
                    contentDescription = null,
                    tint = contentColor
                )
            }
        }
    }
}

private fun artistShortcutShape(
    index: Int,
    count: Int
): RoundedCornerShape {
    val outerCorner = 26.dp
    val innerCorner = 10.dp
    return when {
        count <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        else -> RoundedCornerShape(innerCorner)
    }
}
