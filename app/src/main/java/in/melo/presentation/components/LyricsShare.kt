package `in`.melo.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.melo.R
import `in`.melo.data.model.Song
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.presentation.components.player.playerDockGlass

/**
 * A line as the lyrics screen shows it, ready to be picked for a share card:
 * the words, the smaller line under them, and for synced lyrics when they are sung.
 */
@Immutable
internal class PickableLine(
    val text: String,
    /** The romanization or translation shown under the line, or null. */
    val subText: String?,
    /** The line's sweep, for synced lyrics; null for plain ones. */
    val timing: SweptLine?,
)

/**
 * One picked line as it goes on the card.
 *
 * @property isGap Stands for lines skipped between two picked ones, drawn as "(...)".
 * @property startMs Where the line is sung in the track, for the share video; null when unsynced.
 */
internal data class LyricsShareLine(
    val text: String,
    val subText: String?,
    val isGap: Boolean = false,
    val startMs: Long? = null,
    val endMs: Long? = null,
    /** The line's sweep, so the video lights the words as the lyrics screen does. */
    val timing: SweptLine? = null,
)

/** Everything the card needs, gathered once when Share is tapped. */
internal data class LyricsShareRequest(
    val song: Song,
    val artworkUrl: String?,
    val lines: List<LyricsShareLine>,
    /** The user's lyrics offset: line times are the track position plus this. */
    val syncOffsetMs: Long = 0L,
)

/**
 * How much text one card holds, in characters of the picked words: about five
 * short lines, where the type is still big enough to read in a chat. Past it a
 * line is refused at the pick; nothing already picked is dropped.
 */
internal const val SHARE_CARD_CHAR_BUDGET = 250

/**
 * Whether a line of [text] can still join a card already holding [taken]
 * characters. Only the words count: the smaller line under each rides along.
 */
internal fun fitsOnCard(taken: Int, text: String?): Boolean =
    taken + (text?.length ?: 0) <= SHARE_CARD_CHAR_BUDGET

/** The pick mode: which lines are chosen and what can be done with them. */
internal class LyricsPicker(
    val picking: Boolean,
    val picks: Set<Int>,
    /** Long-press on a line: start picking with it, or toggle it once picking. */
    val pick: (Int) -> Unit,
    /** Tap while picking: add or drop that line. */
    val toggle: (Int) -> Unit,
    val cancel: () -> Unit,
    /** Hands the chosen lines over as a card request. */
    val share: () -> Unit,
    /** The header's share button: picking, with nothing chosen yet. */
    val beginPicking: () -> Unit,
)

/**
 * The pick mode's state. Adding is held to [SHARE_CARD_CHAR_BUDGET] across
 * everything chosen; a refused line says so and stays unlit.
 */
@Composable
internal fun rememberLyricsPicker(
    song: Song?,
    lines: List<PickableLine>,
    syncOffsetMs: Long = 0L,
    onCard: (LyricsShareRequest) -> Unit,
): LyricsPicker {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val cardFull = stringResource(R.string.lyrics_share_pick_limit)
    val currentLines by rememberUpdatedState(lines)
    val currentSong by rememberUpdatedState(song)
    val currentOnCard by rememberUpdatedState(onCard)
    val currentOffset by rememberUpdatedState(syncOffsetMs)

    var picking by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // The picks index into the lines: another track or other lyrics clear them.
    LaunchedEffect(song?.id, lines) {
        picking = false
        picks = emptySet()
    }

    val toggle: (Int) -> Unit = { index ->
        val text = currentLines.getOrNull(index)?.text
        when {
            index in picks -> picks = picks - index
            text.isNullOrBlank() -> Unit
            fitsOnCard(picks.sumOf { currentLines.getOrNull(it)?.text?.length ?: 0 }, text) -> picks = picks + index
            else -> Toast.makeText(context, cardFull, Toast.LENGTH_SHORT).show()
        }
    }
    val pick: (Int) -> Unit = { index ->
        if (picking) {
            toggle(index)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            picking = true
            picks = emptySet()
            toggle(index)
        }
    }
    val cancel: () -> Unit = {
        picking = false
        picks = emptySet()
    }
    val beginPicking: () -> Unit = {
        if (!picking) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            picking = true
            picks = emptySet()
        }
    }
    val share: () -> Unit = share@{
        val target = currentSong ?: return@share cancel()
        val picked = buildShareLines(currentLines, picks)
        if (picked.isNotEmpty()) {
            currentOnCard(LyricsShareRequest(target, target.albumArtUriString, picked, currentOffset))
        }
        cancel()
    }
    return LyricsPicker(picking, picks, pick, toggle, cancel, share, beginPicking)
}

/**
 * The chosen lines in order, with a gap marker wherever unchosen lines were
 * skipped, and for synced lyrics the stretch of the track each is sung over.
 */
internal fun buildShareLines(lines: List<PickableLine>, picks: Set<Int>): List<LyricsShareLine> {
    val chosen = picks.filter { it in lines.indices }.sorted()
    val synced = lines.any { it.timing != null }
    return buildList {
        chosen.forEachIndexed { position, index ->
            if (position > 0 && index > chosen[position - 1] + 1) {
                add(LyricsShareLine(text = "", subText = null, isGap = true))
            }
            val line = lines[index]
            val window = if (synced) singWindow(lines, index, chosen.getOrNull(position + 1)) else null
            add(
                LyricsShareLine(
                    text = line.text,
                    subText = line.subText?.takeIf { it.isNotBlank() && it != line.text },
                    startMs = window?.first,
                    endMs = window?.second,
                    timing = line.timing,
                ),
            )
        }
    }.filter { it.isGap || it.text.isNotBlank() }
}

/**
 * The stretch of the track line [index] is sung over: up to the next line's
 * start when the next line is picked too, so a run plays as one take; otherwise
 * until the sweep ends plus a beat, never past the next line nor longer than a cap.
 */
internal fun singWindow(lines: List<PickableLine>, index: Int, nextPicked: Int?): Pair<Long, Long>? {
    val timing = lines.getOrNull(index)?.timing ?: return null
    val start = timing.startMs.coerceAtLeast(0L)
    val nextStamp = lines.getOrNull(index + 1)?.timing?.startMs?.takeIf { it > start }
    val end = if (nextPicked == index + 1 && nextStamp != null) {
        nextStamp
    } else {
        val tail = timing.sweepEndMs.takeIf { it > start }?.plus(SHARE_TAIL_MS)
            ?: nextStamp
            ?: (start + SHARE_LINE_FALLBACK_MS)
        minOf(tail, nextStamp ?: Long.MAX_VALUE, start + SHARE_LINE_MAX_MS)
    }
    return start to end
}

/** A beat after a line's last word, so it isn't clipped. */
private const val SHARE_TAIL_MS = 400L

/** A last line with nothing after it to time it by. */
private const val SHARE_LINE_FALLBACK_MS = 5_000L

/** The longest one line is held when it ends a run. */
private const val SHARE_LINE_MAX_MS = 9_000L

/** The floating bar while lines are picked: Cancel and Share. */
@Composable
internal fun LyricsPickBar(
    shareEnabled: Boolean,
    dark: Boolean,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .playerDockGlass(RoundedCornerShape(22.dp), dark)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShareButton(
            label = stringResource(R.string.common_cancel),
            icon = MeloIcons.Close,
            accent = false,
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCancel()
            },
        )
        ShareButton(
            label = stringResource(R.string.detail_share),
            icon = MeloIcons.Share,
            accent = true,
            enabled = shareEnabled,
            modifier = Modifier.weight(1f),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onShare()
            },
        )
    }
}

/** One button of the pick bar or the share sheet: white, or a white film. */
@Composable
internal fun ShareButton(
    label: String,
    icon: ImageVector,
    accent: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (accent) Color.White.copy(alpha = if (enabled) 1f else 0.3f)
    else Color.White.copy(alpha = if (enabled) 0.12f else 0.06f)
    val foreground = if (accent) Color(0xFF121212).copy(alpha = if (enabled) 1f else 0.4f)
    else Color.White.copy(alpha = if (enabled) 0.9f else 0.4f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
