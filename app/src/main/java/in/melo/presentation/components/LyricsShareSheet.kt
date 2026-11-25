package `in`.melo.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import `in`.melo.R
import `in`.melo.data.canvas.CanvasRepository
import `in`.melo.data.canvas.CanvasSource
import `in`.melo.data.preferences.dataStore
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.ui.glass.MeloSegmentedControl
import `in`.melo.ui.glass.MeloSwitch
import `in`.melo.ui.theme.LocalMeloDarkTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOGO_KEY = booleanPreferencesKey("lyrics_card_logo")

/**
 * The share sheet for picked lyrics: a video of the song with the lines lit as they are sung, or
 * the still card; laid out as a 9:16 story or a card cut to its lines; on the song's blurred
 * colours or one of its moving covers; with or without the mark. The preview is the thing that
 * will be sent, playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsShareSheet(request: LyricsShareRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Remembered across shares: someone who takes the mark off wants it off.
    val logoFlow = remember(context) { context.dataStore.data.map { it[LOGO_KEY] ?: true } }
    val showLogo by logoFlow.collectAsState(initial = true)
    // The 9:16 story is what these are mostly made for, so it is where the sheet opens; the card
    // cut to its lines is one tap away.
    var story by remember(request) { mutableStateOf(true) }
    var saved by remember(request) { mutableStateOf(false) }
    var failed by remember(request) { mutableStateOf(false) }

    // The song's moving covers, each offered as a background once its search comes back. The
    // blurred colours are the default, and all there is until then (or when none is found).
    var clips by remember(request) {
        mutableStateOf(CanvasRepository.cached(request.song).orEmpty())
    }
    LaunchedEffect(request) {
        clips = clips + runCatching { CanvasRepository.shareClipsFor(request.song) }.getOrDefault(emptyMap())
    }
    var background by remember(request) { mutableStateOf(ShareBackground.GRADIENT) }
    // Video: the song, the words sweeping with it, on the colours or a moving cover. Image: the
    // still card, every line lit. Image has no background choice; the clip only exists in motion.
    var asVideo by remember(request) { mutableStateOf(true) }
    val clip = background.source?.takeIf { asVideo }?.let(clips::get)?.let { found ->
        // A story is 9:16: the 3:4 rendition crops to it with less thrown away.
        if (story) found.copy(url = found.portraitUrl ?: found.url) else found
    }
    // Synced lines carry where they are sung: the share becomes a video of that stretch of the
    // song, each line lighting up as the vocal reaches it.
    val timeline = remember(request) { shareTimeline(request.lines) }
    // Nothing would move in a video with no vocal to follow and no clip.
    val videoPossible = timeline != null || clips.isNotEmpty()
    val isVideo = asVideo && (clip != null || timeline != null)
    // Saving runs from the tap until the file is in the gallery: the export, then the copy.
    var saving by remember(request) { mutableStateOf(false) }

    // The card laid out once and drawable at any moment of the song: the preview runs it on the
    // song's own clock, so what is shared is what was seen. [image] is the still card, for the
    // frame's shape and for a PNG.
    var image by remember(request) { mutableStateOf<Bitmap?>(null) }
    var painter by remember(request) { mutableStateOf<LyricsCardPainter?>(null) }
    LaunchedEffect(request, story, showLogo, background, asVideo) {
        image = null
        painter = null
        failed = false
        saved = false
        runCatching {
            val paint = lyricsCardPainter(context, request, story, showLogo, transparent = clip != null)
            image = withContext(Dispatchers.Default) { paint.render(null) }
            painter = paint
        }.onFailure { failed = true }
    }

    // One export per look, reused by Save and Share alike.
    var exporting by remember(request) { mutableStateOf(false) }
    var exported by remember(request) { mutableStateOf<Pair<Any, File>?>(null) }
    suspend fun videoFile(): File? {
        val look = listOf(clip, story, showLogo)
        exported?.takeIf { it.first == look && it.second.exists() }?.let { return it.second }
        exporting = true
        return try {
            // The export lays its own background, the clip or the colours, so the card it is
            // handed is always the bare one.
            val overlay = runCatching {
                lyricsCardPainter(context, request, story, showLogo, transparent = true)
            }.getOrNull() ?: return null
            exportLyricsVideo(context, request, clip, overlay, portrait = story)
                ?.also { exported = look to it }
        } finally {
            exporting = false
        }
    }

    /** The file for the current look: the video, or the still card as a picture. */
    suspend fun produce(): File? {
        if (isVideo) return videoFile()
        val drawn = image ?: return null
        return writeLyricsImage(context, drawn)
    }

    fun save() {
        if (saving) return
        saved = false
        saving = true
        failed = false
        scope.launch {
            val done = try {
                val file = produce()
                file != null && saveToGallery(context, file, request.song.title, isVideo)
            } finally {
                saving = false
            }
            if (!done) failed = true
            saved = done
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else failed = true
    }
    val shareTitle = stringResource(R.string.lyrics_share_chooser)
    val busy = exporting || saving

    CompositionLocalProvider(LocalMeloDarkTheme provides true) {
        MaterialTheme(colorScheme = darkColorScheme(primary = MaterialTheme.colorScheme.primary)) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.5f),
                tonalElevation = 0.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * SHEET_SCREEN_SHARE).dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.lyrics_share_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.lyrics_share_description),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    MeloSegmentedControl(
                        options = listOf(stringResource(R.string.lyrics_share_video), stringResource(R.string.lyrics_share_image)),
                        selectedIndex = if (asVideo && videoPossible) 0 else 1,
                        onSelect = { asVideo = it == 0 },
                        enabled = !busy,
                        optionEnabled = { it != 0 || videoPossible },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (asVideo && videoPossible) {
                        // Apple and Spotify are always shown, dimmed when the song has none; the
                        // other providers' clip only when there is one.
                        val choices = ShareBackground.entries.filter {
                            it != ShareBackground.OTHER || CanvasSource.OTHER in clips
                        }
                        MeloSegmentedControl(
                            options = choices.map { stringResource(it.label) },
                            selectedIndex = choices.indexOf(background).coerceAtLeast(0),
                            onSelect = { background = choices[it] },
                            enabled = !busy,
                            optionEnabled = { index -> choices[index].source.let { it == null || it in clips } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val bitmap = image
                    // The card is only as tall as its lines, so the frame follows it: a strip
                    // comes out wide, a full verse narrow. Sized off the screen's height so the
                    // whole sheet, actions and all, fits.
                    val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: (9f / 16f)
                    val previewH = (LocalConfiguration.current.screenHeightDp * PREVIEW_SCREEN_SHARE).dp
                    val cardDescription = stringResource(R.string.lyrics_share_card_description)
                    val preview = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentWidth()
                        .height(previewH)
                        .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(18.dp))
                        .semantics { contentDescription = cardDescription }
                    if (bitmap == null) {
                        Box(preview.background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    } else {
                        Box(preview, contentAlignment = Alignment.Center) {
                            if (clip != null) {
                                // The clip live under the card, blurred and dimmed the way the
                                // export draws it.
                                CanvasClipPlayer(clip, Modifier.matchParentSize().blur(PREVIEW_BLUR))
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = CLIP_SCRIM)))
                            }
                            CardPreview(painter, bitmap, timeline.takeIf { isVideo }, Modifier.matchParentSize())
                            if (exporting) ExportSpinner()
                        }
                    }

                    // Opens as a story; this switches between the two shapes.
                    ShareButton(
                        label = stringResource(if (story) R.string.lyrics_share_story_revert else R.string.lyrics_share_story),
                        icon = MeloIcons.Image,
                        accent = false,
                        enabled = image != null && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { story = !story }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PlainFill)
                            .toggleable(
                                value = showLogo,
                                enabled = !busy,
                                role = Role.Switch,
                                onValueChange = { on -> scope.launch { context.dataStore.edit { it[LOGO_KEY] = on } } },
                            )
                            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lyrics_share_show_logo),
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        // The row is the toggle; the switch only shows its state.
                        MeloSwitch(checked = showLogo, onCheckedChange = null, enabled = !busy)
                    }

                    if (failed) {
                        Text(
                            stringResource(R.string.lyrics_share_failed),
                            fontSize = 13.sp,
                            color = Color(0xFFFF6961),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShareButton(
                            label = stringResource(
                                when {
                                    saving -> R.string.lyrics_share_saving
                                    saved -> R.string.lyrics_share_saved
                                    else -> R.string.common_save
                                },
                            ),
                            icon = if (saved) MeloIcons.Check else MeloIcons.Download,
                            accent = false,
                            enabled = image != null && !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                PackageManager.PERMISSION_GRANTED
                            if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                        }
                        ShareButton(
                            label = stringResource(R.string.detail_share),
                            icon = MeloIcons.Share,
                            accent = true,
                            enabled = image != null && !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            failed = false
                            scope.launch {
                                val video = isVideo
                                val file = produce()
                                val intent = file?.let { shareFileIntent(context, it, if (video) VIDEO_MIME else IMAGE_MIME) }
                                if (intent == null) {
                                    failed = true
                                    saved = false
                                } else {
                                    runCatching { context.startActivity(Intent.createChooser(intent, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        .onFailure { failed = true }
                                }
                            }
                        }
                    }
                    if (exporting) {
                        Text(
                            stringResource(R.string.lyrics_share_rendering),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/** What the card sits on: the song's blurred colours, or one of its moving covers. */
private enum class ShareBackground(@param:StringRes val label: Int, val source: CanvasSource?) {
    GRADIENT(R.string.lyrics_share_gradient, null),
    APPLE(R.string.lyrics_share_apple_canvas, CanvasSource.APPLE),
    SPOTIFY(R.string.lyrics_share_spotify_canvas, CanvasSource.SPOTIFY),

    /** A clip from Tidal or the community index: offered only when there is one. */
    OTHER(R.string.lyrics_share_canvas, CanvasSource.OTHER),
}

/** Over the preview while the video is being made. */
@Composable
private fun ExportSpinner() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * The card as the video will play it: [painter] drawn every frame at the moment of the song the
 * loop has reached, the sweep, the lifts and the held words as on the lyrics screen. The still
 * [card] when there's no vocal to follow, or until the painter is ready.
 */
@Composable
private fun CardPreview(
    painter: LyricsCardPainter?,
    card: Bitmap,
    timeline: ShareTimeline?,
    modifier: Modifier,
) {
    if (painter == null || timeline == null) {
        Image(bitmap = card.asImageBitmap(), contentDescription = null, modifier = modifier)
        return
    }
    var elapsedMs by remember(painter, timeline) { mutableLongStateOf(0L) }
    LaunchedEffect(painter, timeline) {
        val start = withFrameMillis { it }
        val length = (timeline.durationMs + PREVIEW_REST_MS).coerceAtLeast(1L)
        while (true) withFrameMillis { elapsedMs = (it - start) % length }
    }
    Canvas(modifier) {
        val at = timeline.trackMsAt(elapsedMs * 1000)
        val scale = size.width / painter.width
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(scale, scale)
            painter.drawOn(native, at)
            native.restore()
        }
    }
}

/** How much of the screen the sheet may take: enough for every control. */
private const val SHEET_SCREEN_SHARE = 0.92f

/** The preview's height, as a share of the screen's. */
private const val PREVIEW_SCREEN_SHARE = 0.3f

/** A beat on the finished verse before the preview loops back to the start. */
private const val PREVIEW_REST_MS = 1_200L

/** The black over the moving cover in the preview: the export's 60% dim. */
private const val CLIP_SCRIM = 0.4f

/** About the blur the export draws, at the preview's size. */
private val PREVIEW_BLUR = 10.dp

/** One fill for the sheet's plain controls: the story button, Save and the logo row. */
private val PlainFill = Color.White.copy(alpha = 0.10f)
