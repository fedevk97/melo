package `in`.melo.presentation.screens

import `in`.melo.ui.glass.MeloAlertDialog
import `in`.melo.ui.glass.MeloSlider
import `in`.melo.ui.glass.MeloSwitch
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.presentation.utils.LocalAppHapticsConfig
import `in`.melo.presentation.utils.performAppCompatHapticFeedback
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import `in`.melo.presentation.components.MeloBottomSheet as ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import `in`.melo.presentation.components.meloPanel
import `in`.melo.R
import `in`.melo.data.model.Song
import `in`.melo.data.model.Album
import `in`.melo.data.model.Artist
import `in`.melo.data.model.Playlist
import `in`.melo.ui.theme.GoogleSansRounded
import `in`.melo.ui.theme.LocalMeloDarkTheme
import `in`.melo.presentation.viewmodel.ColorSchemePair
import `in`.melo.presentation.viewmodel.PlayerViewModel
import `in`.melo.presentation.components.SmartImage
import `in`.melo.presentation.components.ShimmerBox
import `in`.melo.presentation.components.AutoScrollingTextOnDemand
import `in`.melo.presentation.components.subcomps.AutoSizingTextToFill
import `in`.melo.presentation.components.subcomps.TightWrapText
import `in`.melo.presentation.components.MiniPlayerHeight
import `in`.melo.utils.formatDuration
import `in`.melo.ui.theme.ExpTitleTypography
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.MeloAlpha
import `in`.melo.presentation.components.MeloButton
import `in`.melo.presentation.components.MeloButtonSize
import `in`.melo.presentation.components.MeloButtonStyle
import `in`.melo.presentation.components.MeloCard


/** Small grey heading over a settings group, inset to line up with the rows' text. */
@Composable
fun SettingsSection(
    title: String?,
    icon: @Composable () -> Unit = {},
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = if (title != null) MeloLayout.Space2 else 0.dp)) {
        if (title != null) Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MeloLayout.PageGutter, bottom = MeloLayout.Space2),
        )
        `in`.melo.presentation.components.MeloGroupedList { content() }
        if (footer != null) SettingsFootnote(footer, Modifier.padding(top = MeloLayout.Space1))
    }
}

/** Short grey note under a settings group, the way grouped lists explain themselves. */
@Composable
fun SettingsFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = MeloLayout.PageGutter).padding(top = MeloLayout.Space1),
    )
}

private val SettingsTileSize = 30.dp
private val SettingsRowMinHeight = 52.dp

/**
 * A row's icon, plain on the row, in a fixed-width slot so
 * every row's text starts on the same line. Pictures such as avatars fill the slot.
 */
@Composable
fun SettingsIconTile(content: @Composable () -> Unit) {
    Box(Modifier.size(SettingsTileSize), contentAlignment = Alignment.Center) { content() }
}

/** The one settings row: icon, title over an optional short note, and what sits on its end. */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String?,
    leadingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val dim = if (enabled) 1f else MeloAlpha.Disabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .padding(horizontal = MeloLayout.PageGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space3),
    ) {
        if (leadingIcon != null) {
            Box(Modifier.graphicsLayer { alpha = dim }) { SettingsIconTile { leadingIcon() } }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor.copy(alpha = titleColor.alpha * dim),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                )
            }
        }
        trailing()
    }
}

/** The grey chevron that ends a row which opens something. */
@Composable
fun SettingsChevron() {
    Icon(
        imageVector = MeloIcons.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
    )
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        onClick: () -> Unit,
        titleColor: Color = Color.Unspecified,
) {
    MeloCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            titleColor = titleColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
        ) { trailingIcon() }
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val toggle = { newValue: Boolean ->
        if (enabled) {
            performAppCompatHapticFeedback(view, appHapticsConfig, HapticFeedbackConstantsCompat.GESTURE_START)
            onCheckedChange(newValue)
        }
    }

    Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().meloPanel()
    ) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            enabled = enabled,
            // The whole row flips the switch, as in a grouped list.
            modifier = Modifier.clickable(enabled = enabled) { toggle(!checked) },
        ) {
            MeloSwitch(
                checked = checked,
                onCheckedChange = { toggle(it) },
                enabled = enabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                        label = "switch_thumb_icon"
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) MeloIcons.Check else MeloIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/**
 * A choice row: the current value in grey at its end with a chevron, and a sheet of the options
 * as one grouped list where a checkmark marks the chosen one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    MeloCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showSheet = true },
    ) {
        // Title and value only; the description is the note under the options in the sheet.
        SettingsRow(title = label, subtitle = null, leadingIcon = leadingIcon) {
            Text(
                text = selectedOption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            SettingsChevron()
        }
    }

    if (showSheet) {
        SettingsOptionsSheet(
            title = label,
            note = description,
            options = options,
            selectedKey = selectedKey,
            onSelect = { key ->
                onSelectionChanged(key)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

/** A sheet of mutually exclusive options: centred title, one grouped list, a checkmark on the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOptionsSheet(
    title: String,
    options: Map<String, String>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    details: Map<String, String> = emptyMap(),
) {
    `in`.melo.presentation.components.MeloBottomSheet(
        onDismissRequest = onDismiss,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = MeloLayout.PageGutter)
                .padding(bottom = MeloLayout.Space6)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = MeloLayout.Space1, bottom = MeloLayout.Space4),
            )
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                `in`.melo.presentation.components.MeloGroupedList {
                    options.forEach { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SettingsRowMinHeight)
                                .clickable { onSelect(key) }
                                .padding(horizontal = MeloLayout.PageGutter, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                details[key]?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = MeloIcons.Check,
                                    contentDescription = stringResource(R.string.common_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
                if (!note.isNullOrBlank()) SettingsFootnote(note, Modifier.padding(top = MeloLayout.Space1))
            }
        }
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)? = null,
        valueText: (Float) -> String,
        enabled: Boolean = true,
) {
    Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().meloPanel()
    ) {
        Column(modifier = Modifier.padding(start = MeloLayout.PageGutter, end = MeloLayout.PageGutter, top = MeloLayout.Space3, bottom = MeloLayout.Space1)) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = MeloAlpha.Disabled)
                )
                Spacer(modifier = Modifier.width(MeloLayout.Space2))
                Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = MeloAlpha.Disabled),
                        maxLines = 1,
                        softWrap = false
                )
            }
            MeloSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
            )
        }
    }
}

/**
 * A row with an action: the action is a tinted word at the row's end; with a second action both
 * sit under the text, end-aligned, so neither crowds the title.
 */
@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val twoActions = secondaryActionLabel != null && onSecondaryAction != null
    MeloCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SettingsRow(title = title, subtitle = subtitle, leadingIcon = icon) {
                if (!twoActions) {
                    MeloButton(
                        text = primaryActionLabel,
                        onClick = onPrimaryAction,
                        enabled = enabled,
                        size = MeloButtonSize.Medium,
                        style = MeloButtonStyle.Tonal,
                    )
                }
            }
            if (twoActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = MeloLayout.PageGutter + SettingsTileSize + MeloLayout.Space3, end = MeloLayout.PageGutter, bottom = MeloLayout.Space3),
                    horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space2),
                ) {
                    MeloButton(
                        text = primaryActionLabel,
                        onClick = onPrimaryAction,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        size = MeloButtonSize.Medium,
                        style = MeloButtonStyle.Tonal,
                    )
                    MeloButton(
                        text = secondaryActionLabel!!,
                        onClick = onSecondaryAction!!,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        size = MeloButtonSize.Medium,
                        style = MeloButtonStyle.Outlined,
                    )
                }
            }
        }
    }
}


/**
 * Red for actions that end something; the theme's error colour is neutral in Melo's palette.
 * A touch deeper on light backgrounds so it keeps its contrast there.
 */
val SettingsDestructiveRed: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFFF3B30) else Color(0xFFFF453A)

/** A centred red row for actions that end something, such as signing out, set apart at the end of a page. */
@Composable
fun SettingsDestructiveRow(text: String, onClick: () -> Unit) {
    MeloCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Box(Modifier.fillMaxWidth().heightIn(min = SettingsRowMinHeight).padding(horizontal = MeloLayout.PageGutter), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = SettingsDestructiveRed, textAlign = TextAlign.Center)
        }
    }
}

/** Asks before an action that cannot be taken back; [confirm] is drawn in red. */
@Composable
fun SettingsConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MeloAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            `in`.melo.presentation.components.MeloButton(
                text = confirm,
                onClick = { onDismiss(); onConfirm() },
                size = `in`.melo.presentation.components.MeloButtonSize.Medium,
                style = `in`.melo.presentation.components.MeloButtonStyle.Tonal,
                contentColor = SettingsDestructiveRed,
            )
        },
        dismissButton = {
            `in`.melo.presentation.components.MeloButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                size = `in`.melo.presentation.components.MeloButtonSize.Medium,
                style = `in`.melo.presentation.components.MeloButtonStyle.Text,
            )
        },
    )
}

/** An account at the top of a page: a large round picture, the name and what it is, opening [onClick]. */
@Composable
fun SettingsAccountRow(title: String, subtitle: String, picture: @Composable () -> Unit, onClick: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = { SettingsChevron() }) {
    MeloCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = MeloLayout.PageGutter, vertical = MeloLayout.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space3),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { picture() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}
