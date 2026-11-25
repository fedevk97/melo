package `in`.melo.update

import `in`.melo.presentation.components.MeloButton
import `in`.melo.presentation.components.MeloButtonSize
import `in`.melo.presentation.components.MeloButtonStyle
import `in`.melo.ui.glass.MeloAlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.BuildConfig
import `in`.melo.R
import java.util.Locale

@Composable
internal fun UpdatePrompt() {
    val context = LocalContext.current
    val updater = remember { AppUpdater.get(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    if (state == UpdateState.Idle) return
    if (state == UpdateState.Unconfigured) {
        MeloAlertDialog(onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_settings_title)) },
            text = { Text(stringResource(R.string.melo_updates_unavailable)) },
            confirmButton = { MeloButton(text = stringResource(R.string.update_close), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium) })
        return
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun launchInstaller(current: UpdateState.Ready) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", current.file)
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.clipData = ClipData.newRawUri("Melo update", uri)
            installer.launch(intent)
        } catch (_: Exception) { updater.installerFailed() }
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            (updater.state.value as? UpdateState.Ready)?.let(::launchInstaller)
        }
    }
    val release = when (val current = state) {
        is UpdateState.Available -> current.release
        is UpdateState.Downloading -> current.release
        is UpdateState.Ready -> current.release
        is UpdateState.Failed -> current.release
        is UpdateState.Notes -> current.release
        else -> null
    }
    fun openRelease() {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.page ?: GitHubRelease.LATEST_PAGE))) }
        catch (_: android.content.ActivityNotFoundException) { }
    }
    fun joinDiscord() {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/h3Hzk4seXj"))) }
        catch (_: android.content.ActivityNotFoundException) { }
        updater.dismiss()
    }
    val title = when (state) {
        UpdateState.Checking -> R.string.update_checking
        UpdateState.Current -> R.string.update_current_title
        is UpdateState.Downloading -> R.string.update_downloading
        is UpdateState.Ready -> R.string.update_ready
        is UpdateState.Failed -> R.string.update_failed_title
        is UpdateState.Notes -> R.string.update_notes_title
        UpdateState.CommunityInvite -> R.string.community_invite_title
        else -> R.string.update_available
    }
    MeloAlertDialog(
        onDismissRequest = updater::dismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (val current = state) {
                    is UpdateState.Available -> {
                        ReleaseSummary(current.release.tag, BuildConfig.VERSION_NAME)
                        Text(stringResource(R.string.update_notes_title), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (current.release.notes.isNotBlank()) ReleaseNotes(current.release.notes)
                        if (current.release.apk == null) Text(stringResource(R.string.update_no_apk),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is UpdateState.Notes -> {
                        release?.let { ReleaseSummary(it.tag, null) }
                        if (current.release.notes.isNotBlank()) ReleaseNotes(current.release.notes)
                    }
                    UpdateState.CommunityInvite -> Text(
                        stringResource(R.string.community_invite_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is UpdateState.Downloading -> {
                        if (current.total > 0) {
                            LinearProgressIndicator(progress = { (current.done.toFloat() / current.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.update_progress, megabytes(current.done), megabytes(current.total), (current.done * 100 / current.total).toInt()))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.update_downloaded, megabytes(current.done)))
                        }
                    }
                    is UpdateState.Ready -> Text(stringResource(R.string.update_install_description))
                    is UpdateState.Failed -> Text(stringResource(if (current.release == null) R.string.update_check_failed else R.string.update_download_failed))
                    UpdateState.Current -> Text(stringResource(R.string.update_current_description, BuildConfig.VERSION_NAME))
                    UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateState.Available -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        MeloButton(
                            text = stringResource(if (current.release.apk != null) R.string.update_download_now else R.string.update_release_page),
                            onClick = { if (current.release.apk != null) updater.download(current.release) else openRelease() },
                            modifier = Modifier.fillMaxWidth(),
                            size = MeloButtonSize.Medium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MeloButton(text = stringResource(R.string.update_later), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                            MeloButton(text = stringResource(R.string.update_dont_remind_today), onClick = updater::dontRemindToday, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                        }
                    }
                }
                UpdateState.CommunityInvite -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MeloButton(
                        text = stringResource(R.string.community_invite_join),
                        onClick = ::joinDiscord,
                        modifier = Modifier.fillMaxWidth(),
                        size = MeloButtonSize.Medium,
                    )
                    MeloButton(text = stringResource(R.string.community_invite_later), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                }
                is UpdateState.Ready -> MeloButton(text = stringResource(R.string.update_install), size = MeloButtonSize.Medium, onClick = {
                    try {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        } else {
                            launchInstaller(current)
                        }
                    } catch (_: Exception) { updater.installerFailed() }
                })
                is UpdateState.Failed -> MeloButton(
                    text = stringResource(R.string.update_retry),
                    onClick = { current.release?.let(updater::download) ?: updater.check(manual = true) },
                    size = MeloButtonSize.Medium,
                )
                UpdateState.Current, is UpdateState.Notes -> MeloButton(text = stringResource(R.string.update_close), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Downloading, UpdateState.Checking -> MeloButton(text = stringResource(R.string.update_cancel), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                is UpdateState.Failed -> Row {
                    MeloButton(text = stringResource(R.string.update_close), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                    MeloButton(text = stringResource(R.string.update_release_page), onClick = ::openRelease, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                }
                else -> Unit
            }
        },
    )
}

@Composable
private fun ReleaseSummary(version: String, installedVersion: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Melo $version", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (installedVersion != null) Text(stringResource(R.string.update_installed, installedVersion),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReleaseNotes(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        markdown.lineSequence().take(160).map(String::trim).filter { it.isNotBlank() && it != "---" }.forEach { raw ->
            val heading = raw.startsWith('#')
            val bullet = raw.matches(Regex("^[-*+]\\s+.*"))
            val text = raw.trimStart('#').trim().removePrefix("- ").removePrefix("* ").removePrefix("+ ")
                .replace("**", "").replace("`", "")
                .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            if (heading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (bullet) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
