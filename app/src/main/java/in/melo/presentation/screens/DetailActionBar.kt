package `in`.melo.presentation.screens

import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.R
import `in`.melo.data.preferences.DetailAction
import `in`.melo.data.preferences.DetailPage
import `in`.melo.presentation.viewmodel.PlayerViewModel
import `in`.melo.presentation.components.MeloMenu
import `in`.melo.presentation.components.MeloMenuItem
import `in`.melo.presentation.components.MeloIconButton
import `in`.melo.ui.theme.MeloLayout

/** One button a page can offer: in the bar when the layout shows it, in the ⋮ menu otherwise. */
internal data class ActionSpec(
    val action: DetailAction,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** The page's buttons, in the order and split chosen in Settings → Album and playlist pages. */
@Composable
internal fun DetailActionBar(page: DetailPage, actions: List<ActionSpec>, ui: PlayerViewModel) {
    val settings by ui.settings.collectAsStateWithLifecycle()
    val available = actions.associateBy { it.action }
    val ordered = settings.actions(page).mapNotNull { slot -> available[slot.action]?.let { it to slot.shown } }
    val bar = ordered.filter { it.second }.map { it.first }
    val menu = ordered.filterNot { it.second }.map { it.first }
    var expanded by remember { mutableStateOf(false) }
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bar.forEach { spec ->
            // Plain buttons are glass, like the play row above them; on, or destructive, keeps its colour.
            if (!spec.selected && !spec.destructive) GlassCircleButton(
                icon = spec.icon,
                contentDescription = spec.label,
                onClick = spec.onClick,
                size = MeloLayout.ControlSize,
                enabled = spec.enabled,
            ) else MeloIconButton(
                icon = spec.icon,
                contentDescription = spec.label,
                onClick = spec.onClick,
                enabled = spec.enabled,
                containerColor = if (spec.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (spec.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (menu.isNotEmpty()) Box {
            GlassCircleButton(
                icon = MeloIcons.MoreVert,
                contentDescription = stringResource(R.string.detail_more_actions),
                onClick = { expanded = true },
                size = MeloLayout.ControlSize,
            )
            MeloMenu(expanded = expanded, onDismiss = { expanded = false }) {
                menu.forEach { spec ->
                    MeloMenuItem(
                        label = spec.label,
                        icon = { Icon(spec.icon, null) },
                        enabled = spec.enabled,
                        destructive = spec.destructive,
                        onClick = { expanded = false; spec.onClick() },
                    )
                }
            }
        }
    }
}

/** The download button of a page, reflecting what is already kept on the device. */
@Composable
internal fun downloadAction(ui: PlayerViewModel, item: `in`.melo.data.Music): ActionSpec {
    val collections by ui.downloadCollections.collectAsStateWithLifecycle()
    val preparations by ui.downloadPreparations.collectAsStateWithLifecycle()
    val progress = collections[item.id]
    val complete = progress?.isComplete == true
    val requested = progress != null || item.id in preparations
    return ActionSpec(
        DetailAction.DOWNLOAD,
        if (complete) MeloIcons.Downloaded else MeloIcons.DownloadForOffline,
        stringResource(if (complete) R.string.download_remove else if (requested) R.string.download_cancel else R.string.download_action),
        onClick = { ui.toggleDownloadItem(item) }, selected = complete,
    )
}

/** Names of the buttons in Settings. */
@Composable
internal fun actionName(action: DetailAction): String = stringResource(when (action) {
    DetailAction.DOWNLOAD -> R.string.download_action
    DetailAction.ADD_TRACKS -> R.string.playlist_add_tracks
    DetailAction.SAVE -> R.string.saved_add
    DetailAction.PIN -> R.string.pin_add
    DetailAction.RADIO -> R.string.radio
    DetailAction.SHARE -> R.string.detail_share
    DetailAction.SORT -> R.string.playlist_sort
    DetailAction.REORDER -> R.string.reorder
    DetailAction.EDIT -> R.string.detail_edit
    DetailAction.EXPORT -> R.string.playlist_export_csv
    DetailAction.CONVERT -> R.string.convert_to
    DetailAction.DELETE -> R.string.playlist_delete
})
