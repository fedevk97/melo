package `in`.melo.presentation.components

import `in`.melo.ui.glass.MeloAlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.GoogleSansRounded
import androidx.compose.ui.res.stringResource
import `in`.melo.R
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    MeloAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.equalizer_presets_save_custom_title), fontFamily = GoogleSansRounded)
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.equalizer_presets_save_custom_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.equalizer_preset_name_placeholder)) },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = stringResource(R.string.equalizer_error_name_empty),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            MeloButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        onSave(name)
                        onDismiss()
                    }
                },
                size = MeloButtonSize.Medium,
            )
        },
        dismissButton = {
            MeloButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                size = MeloButtonSize.Medium,
                style = MeloButtonStyle.Text,
            )
        }
    )
}

@Composable
fun RenamePresetDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }

    MeloAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.equalizer_presets_rename_title), fontFamily = GoogleSansRounded)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.equalizer_preset_name_placeholder)) },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = stringResource(R.string.equalizer_error_name_empty),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            MeloButton(
                text = stringResource(R.string.equalizer_action_rename),
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        onRename(name.trim())
                        onDismiss()
                    }
                },
                size = MeloButtonSize.Medium,
            )
        },
        dismissButton = {
            MeloButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                size = MeloButtonSize.Medium,
                style = MeloButtonStyle.Text,
            )
        }
    )
}
