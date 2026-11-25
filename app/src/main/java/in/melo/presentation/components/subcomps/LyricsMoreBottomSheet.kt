package `in`.melo.presentation.components.subcomps

import `in`.melo.ui.theme.controlFill
import `in`.melo.ui.glass.MeloAlertDialog
import `in`.melo.ui.glass.MeloSwitch
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.presentation.icons.meloPainter
import `in`.melo.ui.theme.MeloAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import `in`.melo.presentation.components.MeloBottomSheet as ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import `in`.melo.R
import `in`.melo.data.model.Lyrics
import `in`.melo.presentation.components.MeloButton
import `in`.melo.presentation.components.MeloButtonSize
import `in`.melo.presentation.components.MeloButtonStyle
import `in`.melo.presentation.components.ToggleSegmentButton
import `in`.melo.presentation.components.player.BottomToggleRow
import androidx.compose.ui.text.style.TextOverflow
import `in`.melo.ui.theme.MeloLayout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    onTranslateViaAi: () -> Unit,
    onToggleSyncControls: () -> Unit,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    immersiveLyricsEnabled: Boolean,
    // BottomToggleRow params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    // Colors
    /** Null wears the app's sheet material. */
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showResetDialog by remember { mutableStateOf(false) }

    `in`.melo.presentation.components.MeloBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = MeloLayout.SheetShape,
        contentWindowInsets = { WindowInsets(top = 0, bottom = 0) }
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                //.heightIn(max = screenHeight * 0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navigationBarsPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MeloLayout.Space3)
        ) {
            // No Title - "Expressive" relies on visual grouping

            val itemBackgroundColor = contentColor.copy(alpha = MeloAlpha.Divider)

            // Lyrics Actions Group
            Column(
                verticalArrangement = Arrangement.spacedBy(MeloLayout.Space1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = MeloLayout.Space2, bottom = MeloLayout.Space2),
                    text = stringResource(R.string.lyrics_title),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                 // Save lyrics to .lrc
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lyrics_save_title)) },
                        leadingContent = {
                            Icon(
                                painter = meloPainter(R.drawable.outline_save_24),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Translate via AI

                // Reset imported lyrics
                val resetShape = if (lyrics != null) {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                } else {
                    MeloLayout.PanelShape
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.lyrics_reset_imported)) },
                    leadingContent = {
                        Icon(
                            painter = meloPainter(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(resetShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            showResetDialog = true
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )
            }

            if (showResetDialog) {
                MeloAlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.lyrics_reset_dialog_title)) },
                    text = { Text(stringResource(R.string.lyrics_reset_dialog_message)) },
                    confirmButton = {
                        MeloButton(
                            text = stringResource(R.string.common_reset),
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            },
                            size = MeloButtonSize.Medium,
                            style = MeloButtonStyle.Text,
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    },
                    dismissButton = {
                        MeloButton(
                            text = stringResource(R.string.common_cancel),
                            onClick = { showResetDialog = false },
                            size = MeloButtonSize.Medium,
                            style = MeloButtonStyle.Text,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Appearance Group
            Column(
                verticalArrangement = Arrangement.spacedBy(MeloLayout.Space1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = MeloLayout.Space2, bottom = MeloLayout.Space2),
                    text = stringResource(R.string.lyrics_appearance_section),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                 )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MeloLayout.PanelShape)
                        .background(itemBackgroundColor)
                        .padding(MeloLayout.Space4),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_appearance_alignment),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "left",
                            activeColor = accentColor,
                            inactiveColor = containerColor ?: MaterialTheme.colorScheme.controlFill,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = MeloAlpha.Secondary),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("left") },
                            imageVector = MeloIcons.FormatAlignLeft,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_left)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "center",
                            activeColor = accentColor,
                            inactiveColor = containerColor ?: MaterialTheme.colorScheme.controlFill,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = MeloAlpha.Secondary),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("center") },
                            imageVector = MeloIcons.FormatAlignCenter,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_center)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "right",
                            activeColor = accentColor,
                            inactiveColor = containerColor ?: MaterialTheme.colorScheme.controlFill,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = MeloAlpha.Secondary),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("right") },
                            imageVector = MeloIcons.FormatAlignRight,
                            contentDesc = stringResource(R.string.lyrics_appearance_align_right)
                        )
                    }
                }
            }

            // Control Settings Group
            val isSyncVisible = showSyncedLyrics
            val isRomanizationVisible = hasRomanizedLyrics
            val isTranslationVisible = hasTranslatedLyrics
            val isImmersiveVisible = showSyncedLyrics && immersiveLyricsEnabled
            val isKeepScreenOnVisible = true

            if (isSyncVisible || isRomanizationVisible || isTranslationVisible || isKeepScreenOnVisible) {
                // Determine first and last items for rounding
                val isRomanizationFirst = isRomanizationVisible && !isSyncVisible
                val isTranslationFirst = isTranslationVisible && !isSyncVisible && !isRomanizationVisible

                val isSyncLast = isSyncVisible && !isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isRomanizationLast = isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isTranslationLast = isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isImmersiveLast = isImmersiveVisible && !isKeepScreenOnVisible

                Column(
                    verticalArrangement = Arrangement.spacedBy(MeloLayout.Space1),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(start = MeloLayout.Space2, bottom = MeloLayout.Space2),
                        text = stringResource(R.string.lyrics_controls_section),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )

                    if (isSyncVisible) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (isSyncControlsVisible) {
                                        stringResource(R.string.lyrics_controls_hide_sync)
                                    } else {
                                        stringResource(R.string.lyrics_controls_adjust_sync)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = MeloIcons.Tune,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isSyncLast) 24.dp else 8.dp,
                                        bottomEnd = if (isSyncLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable {
                                    onDismissRequest()
                                    onToggleSyncControls()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isRomanizationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_romanization)) },
                            leadingContent = {
                                Icon(
                                    imageVector = MeloIcons.Abc,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                MeloSwitch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = MeloAlpha.Disabled)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isRomanizationFirst) 18.dp else 8.dp,
                                        topEnd = if (isRomanizationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isRomanizationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isRomanizationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowRomanizationChange(!showRomanization) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isTranslationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_translations)) },
                            leadingContent = {
                                Icon(
                                    imageVector = MeloIcons.Translate,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                MeloSwitch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = MeloAlpha.Disabled)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isTranslationFirst) 18.dp else 8.dp,
                                        topEnd = if (isTranslationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isTranslationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isTranslationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowTranslationChange(!showTranslation) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Immersive Mode Toggle
                    if (isImmersiveVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_disable_immersive_once)) },
                            leadingContent = {
                                Icon(
                                    imageVector = MeloIcons.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                MeloSwitch(
                                    modifier = Modifier,
                                    checked = isImmersiveTemporarilyDisabled,
                                    onCheckedChange = {
                                        onSetImmersiveTemporarilyDisabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = MeloAlpha.Disabled)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = if (isImmersiveLast) 24.dp else 8.dp,
                                        bottomEnd = if (isImmersiveLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor),
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Keep Screen On Toggle
                    if (isKeepScreenOnVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_keep_screen_on)) },
                            leadingContent = {
                                Icon(
                                    imageVector = MeloIcons.BrightnessHigh,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                MeloSwitch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = MeloAlpha.Disabled)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 24.dp,
                                        bottomEnd = 24.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onKeepScreenOnChange(!keepScreenOn) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MeloLayout.PanelShape)
                    //.background(contentColor.copy(alpha = 0.08f))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                 BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 20.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }
}
