package `in`.melo.update

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.BuildConfig
import `in`.melo.R
import `in`.melo.data.AppIcon
import `in`.melo.presentation.components.MeloButton
import `in`.melo.presentation.components.MeloButtonSize
import `in`.melo.presentation.components.MeloButtonStyle
import `in`.melo.ui.glass.MeloAlertDialog
import `in`.melo.ui.glass.MeloDialogFrame
import `in`.melo.ui.glass.MeloMenuColors
import `in`.melo.ui.theme.LocalMeloDarkTheme
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
    fun install(current: UpdateState.Ready) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } else {
                launchInstaller(current)
            }
        } catch (_: Exception) { updater.installerFailed() }
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

    // A release on screen gets the full card; it stays up from the offer through the download
    // to the install, with only its lower half changing.
    if (release != null) {
        ReleaseCard(
            state = state,
            release = release,
            onDismiss = updater::dismiss,
            onDownload = { if (release.apk != null) updater.download(release) else openRelease() },
            onInstall = ::install,
            onRetry = { updater.download(release) },
            onNotToday = updater::dontRemindToday,
            onReleasePage = ::openRelease,
        )
        return
    }
    if (state == UpdateState.CommunityInvite) {
        CommunityCard(onJoin = ::joinDiscord, onDismiss = updater::dismiss)
        return
    }

    val title = when (state) {
        UpdateState.Checking -> R.string.update_checking
        UpdateState.Current -> R.string.update_current_title
        is UpdateState.Failed -> R.string.update_failed_title
        else -> R.string.update_available
    }
    MeloAlertDialog(
        onDismissRequest = updater::dismiss,
        title = { Text(stringResource(title)) },
        text = {
            when (state) {
                is UpdateState.Failed -> Text(stringResource(R.string.update_check_failed), textAlign = TextAlign.Center)
                UpdateState.Current -> Text(stringResource(R.string.update_current_description, BuildConfig.VERSION_NAME), textAlign = TextAlign.Center)
                UpdateState.Checking -> ProgressTrack(null, Modifier.padding(top = 8.dp))
                else -> Unit
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Failed -> MeloButton(text = stringResource(R.string.update_retry), onClick = { updater.check(manual = true) }, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                UpdateState.Current -> MeloButton(text = stringResource(R.string.update_close), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                UpdateState.Checking -> MeloButton(text = stringResource(R.string.update_cancel), onClick = updater::dismiss, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium)
                else -> Unit
            }
        },
        dismissButton = when (state) {
            is UpdateState.Failed -> { { MeloButton(text = stringResource(R.string.update_release_page), onClick = ::openRelease, style = MeloButtonStyle.Text, size = MeloButtonSize.Medium) } }
            else -> null
        },
    )
}

/** Which of the card's lower halves is showing. */
private enum class CardPhase { Offer, Downloading, Ready, Failed, Notes }

@Composable
private fun ReleaseCard(
    state: UpdateState,
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (UpdateState.Ready) -> Unit,
    onRetry: () -> Unit,
    onNotToday: () -> Unit,
    onReleasePage: () -> Unit,
) {
    val context = LocalContext.current
    val icon by remember(context) { AppIcon.live(context) }.collectAsState()
    val appIcon = icon ?: AppIcon.Default
    val dark = LocalMeloDarkTheme.current
    val accent = if (dark) appIcon.markDark.second else appIcon.markLight.second
    val phase = when (state) {
        is UpdateState.Downloading -> CardPhase.Downloading
        is UpdateState.Ready -> CardPhase.Ready
        is UpdateState.Failed -> CardPhase.Failed
        is UpdateState.Notes -> CardPhase.Notes
        else -> CardPhase.Offer
    }
    val version = Version.parse(release.tag)?.toString() ?: release.tag
    val notes = remember(release.notes) { ReleaseNotes.forApp(ReleaseNotes.parse(release.notes)) }

    MeloDialogFrame(
        onDismissRequest = onDismiss,
        // A download in flight is not thrown away by a stray tap beside the card.
        properties = DialogProperties(dismissOnClickOutside = phase != CardPhase.Downloading),
        maxWidth = 360.dp,
    ) { colors ->
        Column(
            Modifier.fillMaxWidth().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlowBadge(accent) {
                Image(
                    painter = painterResource(appIcon.preview),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(BadgeShape).border(0.5.dp, accent.copy(alpha = 0.35f), BadgeShape),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(
                    when (phase) {
                        CardPhase.Offer -> R.string.update_eyebrow_new
                        CardPhase.Downloading -> R.string.update_downloading
                        CardPhase.Ready -> R.string.update_ready
                        CardPhase.Failed -> R.string.update_failed_title
                        CardPhase.Notes -> R.string.update_eyebrow_updated
                    },
                ).uppercase(Locale.getDefault()),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Melo $version",
                color = colors.label,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(4.dp))
            val size = release.apk?.bytes?.takeIf { it > 0 }
            Text(
                text = when {
                    phase == CardPhase.Notes -> stringResource(R.string.update_notes_subtitle)
                    size != null -> stringResource(R.string.update_you_have_size, BuildConfig.VERSION_NAME, megabytes(size))
                    else -> stringResource(R.string.update_you_have, BuildConfig.VERSION_NAME)
                },
                color = colors.secondaryLabel,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(if (notes.isNotEmpty()) 20.dp else 8.dp))
        }
        if (notes.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            NotesList(notes, accent, colors, Modifier.weight(1f, fill = false))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
            label = "update actions",
        ) { shown ->
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (shown) {
                    CardPhase.Offer -> {
                        MeloButton(
                            text = stringResource(if (release.apk != null) R.string.update_now else R.string.update_release_page),
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (release.apk == null) Caption(stringResource(R.string.update_no_apk), colors)
                        SecondaryRow(
                            colors,
                            stringResource(R.string.update_later) to onDismiss,
                            stringResource(R.string.update_not_today) to onNotToday,
                        )
                    }
                    CardPhase.Downloading -> {
                        val current = state as? UpdateState.Downloading
                        val fraction = current?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
                        ProgressTrack(fraction, Modifier.padding(top = 6.dp), accent)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (current != null && current.total > 0) stringResource(R.string.update_progress_of, megabytes(current.done), megabytes(current.total))
                                    else stringResource(R.string.update_downloaded, megabytes(current?.done ?: 0)),
                                color = colors.secondaryLabel,
                                fontSize = 13.sp,
                            )
                            if (fraction != null) Text("${(fraction * 100).toInt()}%", color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        SecondaryRow(colors, stringResource(R.string.update_cancel) to onDismiss)
                    }
                    CardPhase.Ready -> {
                        MeloButton(
                            text = stringResource(R.string.update_install),
                            onClick = { (state as? UpdateState.Ready)?.let(onInstall) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Caption(stringResource(R.string.update_install_description), colors)
                        SecondaryRow(colors, stringResource(R.string.update_later) to onDismiss)
                    }
                    CardPhase.Failed -> {
                        Caption(stringResource(R.string.update_download_failed), colors, Modifier.padding(bottom = 12.dp))
                        MeloButton(text = stringResource(R.string.update_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
                        SecondaryRow(
                            colors,
                            stringResource(R.string.update_close) to onDismiss,
                            stringResource(R.string.update_release_page) to onReleasePage,
                        )
                    }
                    CardPhase.Notes -> {
                        MeloButton(text = stringResource(R.string.update_continue), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val BadgeShape = RoundedCornerShape(20.dp)
private val DiscordBlurple = Color(0xFF5865F2)

/** A card's badge, on a soft glow of [glow] that reaches past it without taking room. */
@Composable
private fun GlowBadge(glow: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.drawBehind {
            val radius = size.minDimension * 1.25f
            drawCircle(
                Brush.radialGradient(
                    0f to glow.copy(alpha = 0.5f),
                    0.55f to glow.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The invitation to the Discord server, shown once after an update. */
@Composable
private fun CommunityCard(onJoin: () -> Unit, onDismiss: () -> Unit) {
    MeloDialogFrame(onDismissRequest = onDismiss, maxWidth = 360.dp) { colors ->
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlowBadge(DiscordBlurple) {
                Box(
                    Modifier.size(72.dp).clip(BadgeShape).background(DiscordBlurple),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_discord),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.community_invite_eyebrow).uppercase(Locale.getDefault()),
                color = if (LocalMeloDarkTheme.current) Color(0xFF9AA2FF) else DiscordBlurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.community_invite_title),
                color = colors.label,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.community_invite_message),
                color = colors.secondaryLabel,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp)) {
            MeloButton(
                text = stringResource(R.string.community_invite_join),
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                containerColor = DiscordBlurple,
                contentColor = Color.White,
            )
            SecondaryRow(colors, stringResource(R.string.community_invite_later) to onDismiss)
        }
    }
}

/** The notes, scrolling under soft edges once they outgrow the card. */
@Composable
private fun NotesList(blocks: List<NoteBlock>, accent: Color, colors: MeloMenuColors, modifier: Modifier) {
    val scroll = rememberScrollState()
    val topFade by animateFloatAsState(if (scroll.value > 0) 1f else 0f, label = "notes top fade")
    val bottomFade by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "notes bottom fade")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edge = 28.dp.toPx()
                if (topFade > 0f) drawRect(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - topFade), 1f to Color.Black, startY = 0f, endY = edge),
                    size = size.copy(height = edge),
                    blendMode = BlendMode.DstIn,
                )
                if (bottomFade > 0f) drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - bottomFade), startY = size.height - edge, endY = size.height),
                    topLeft = Offset(0f, size.height - edge),
                    size = size.copy(height = edge),
                    blendMode = BlendMode.DstIn,
                )
            }
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is NoteBlock.Heading -> Text(
                    text = block.text.uppercase(Locale.getDefault()),
                    color = colors.secondaryLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                )
                is NoteBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(accent))
                    Text(styled(block.spans, colors), color = colors.secondaryLabel, fontSize = 14.5.sp, lineHeight = 21.sp)
                }
                is NoteBlock.Paragraph -> Text(styled(block.spans, colors), color = colors.secondaryLabel, fontSize = 13.5.sp, lineHeight = 19.sp)
                is NoteBlock.Table -> NotesTable(block, colors)
            }
        }
    }
}

/**
 * A table as a stack of rows, its first column on top of the rest: a phone card is too narrow
 * for columns side by side. The header row only names the columns, so it is left out.
 */
@Composable
private fun NotesTable(table: NoteBlock.Table, colors: MeloMenuColors) {
    val rows = if (table.rows.size > 1) table.rows.drop(1) else table.rows
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.pressed.copy(alpha = colors.pressed.alpha * 0.6f))
            .border(0.5.dp, colors.separator, shape),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                row.firstOrNull()?.let {
                    Text(styled(it, colors, plain = colors.label), color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                row.drop(1).forEach {
                    Text(styled(it, colors), color = colors.secondaryLabel, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

private fun styled(spans: List<NoteSpan>, colors: MeloMenuColors, plain: Color? = null): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = when {
            span.code -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = plain ?: colors.label)
            span.bold -> SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.label)
            else -> null
        }
        if (style != null) withStyle(style) { append(span.text) } else append(span.text)
    }
}

/** A rounded track; null [fraction] runs it as a sweep while the size is unknown. */
@Composable
private fun ProgressTrack(fraction: Float?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val shown by animateFloatAsState(fraction ?: 0.3f, spring(stiffness = 120f), label = "update progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceIn(0.02f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
private fun Caption(text: String, colors: MeloMenuColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = colors.secondaryLabel,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

/** Quiet actions under the main one, sharing the width evenly. */
@Composable
private fun ColumnScope.SecondaryRow(colors: MeloMenuColors, vararg actions: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (label, onClick) ->
            MeloButton(
                text = label,
                onClick = onClick,
                modifier = Modifier.weight(1f),
                style = MeloButtonStyle.Text,
                size = MeloButtonSize.Medium,
                contentColor = colors.secondaryLabel,
            )
        }
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
