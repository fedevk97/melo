package `in`.melo.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import `in`.melo.R
import `in`.melo.data.model.SyncedLine
import `in`.melo.presentation.icons.MeloIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private const val StripDimAlpha = 0.55f
private const val StripMs = 340
private const val BreakMorphMs = 420
private const val BreakSlide = 0.6f
private val BreakEasing = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private val BreakBarWidth = 96.dp
private val BreakBarHeight = 3.dp
private const val UnavailableHoldMs = 5_000L
private const val UnavailableFadeMs = 900

/**
 * The line being sung, one line high, above the player's scrubber. Tapping it opens the lyrics.
 * Breaks and the intro count down as a thin bar; a line too long for the width scrolls with the
 * singing rather than ending in an ellipsis.
 */
@Composable
internal fun CurrentLyricStrip(
    synced: List<SyncedLine>?,
    hasPlainLyrics: Boolean,
    loading: Boolean,
    trackKey: Any,
    positionFlow: StateFlow<Long>,
    offsetMs: Int,
    isPlaying: Boolean,
    durationMs: Long,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The scrubber's touch target reaches above the drawn bar, so the strip sits in that dead space.
    Box(modifier = modifier.fillMaxWidth().offset(y = 6.dp)) {
        when {
            !synced.isNullOrEmpty() -> SyncedStripLine(
                lines = synced,
                positionFlow = positionFlow,
                offsetMs = offsetMs,
                isPlaying = isPlaying,
                durationMs = durationMs,
                ink = ink,
                onClick = onClick,
            )
            hasPlainLyrics -> StripRow(onClick) {
                NoteMark(ink)
                Text(
                    text = stringResource(R.string.lyrics_strip_open),
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Chevron(ink)
            }
            loading -> Text(
                text = stringResource(R.string.lyrics_loading),
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = StripDimAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            else -> UnavailableLine(trackKey, ink)
        }
    }
}

@Composable
private fun StripRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun NoteMark(ink: Color) {
    Icon(MeloIcons.MusicNote, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun Chevron(ink: Color) {
    Spacer(Modifier.width(6.dp))
    Icon(
        MeloIcons.KeyboardArrowRight,
        contentDescription = null,
        tint = ink.copy(alpha = 0.5f),
        modifier = Modifier.size(14.dp),
    )
}

private val SyncedLine.isGap: Boolean get() = line.isBlank()

@Composable
private fun SyncedStripLine(
    lines: List<SyncedLine>,
    positionFlow: StateFlow<Long>,
    offsetMs: Int,
    isPlaying: Boolean,
    durationMs: Long,
    ink: Color,
    onClick: () -> Unit,
) {
    val clock = rememberLyricClock(positionFlow, offsetMs, null, isPlaying)
    val index by remember(lines) { derivedStateOf { lines.indexOfLast { it.time <= clock.value } } }
    val current = lines.getOrNull(index)
    val instrumental = current == null || current.isGap
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    val instrumentalText = stringResource(R.string.lyrics_instrumental)
    val text = if (instrumental) instrumentalText else current!!.line

    // Where the break a gap line belongs to began: a break can be several bare stamps in a row.
    val breakStart: (Int) -> Int = { at ->
        var start = at
        while (start - 1 >= firstSung.coerceAtLeast(0) && lines[start - 1].isGap) start--
        start
    }
    val isIntro: (Int) -> Boolean = { at -> firstSung >= 0 && at < firstSung }
    val isBreakBar: (Int, SyncedLine?) -> Boolean = { at, item ->
        isIntro(at) || (item != null && item.isGap && firstSung >= 0)
    }

    StripRow(onClick) {
        AnimatedContent(
            targetState = Triple(index, current, text),
            // One bar for the whole of a break, however many stamps it is made of.
            contentKey = { state ->
                when {
                    isIntro(state.first) -> "intro"
                    isBreakBar(state.first, state.second) -> "break@${breakStart(state.first)}"
                    else -> state
                }
            },
            transitionSpec = {
                val barInvolved = isBreakBar(initialState.first, initialState.second) !=
                    isBreakBar(targetState.first, targetState.second)
                if (barInvolved) {
                    val move = tween<IntOffset>(BreakMorphMs, easing = BreakEasing)
                    (fadeIn(tween(BreakMorphMs - 80, delayMillis = 80, easing = LinearOutSlowInEasing)) +
                        slideInVertically(move) { (it * BreakSlide).toInt() })
                        .togetherWith(
                            fadeOut(tween(BreakMorphMs - 160, easing = FastOutLinearInEasing)) +
                                slideOutVertically(move) { -(it * BreakSlide).toInt() },
                        )
                        .using(SizeTransform(clip = false) { _, _ -> tween(BreakMorphMs, easing = BreakEasing) })
                } else {
                    val spec = tween<Float>(StripMs, easing = FastOutSlowInEasing)
                    val slide = tween<IntOffset>(StripMs, easing = FastOutSlowInEasing)
                    (fadeIn(spec) + slideInVertically(slide) { (it * 0.35f).toInt() })
                        .togetherWith(fadeOut(spec) + slideOutVertically(slide) { -(it * 0.35f).toInt() })
                        .using(SizeTransform(clip = false) { _, _ -> tween(StripMs, easing = FastOutSlowInEasing) })
                }
            },
            label = "currentLyric",
            modifier = Modifier.weight(1f, fill = false),
        ) { (lineIndex, lineItem, lineText) ->
            val itemInstrumental = lineItem == null || lineItem.isGap
            val breakSpan = when {
                isIntro(lineIndex) -> 0L to lines[firstSung].time.toLong()
                isBreakBar(lineIndex, lineItem) && lineItem != null -> {
                    val began = lines[breakStart(lineIndex)].time.toLong()
                    val until = lines.subList(lineIndex + 1, lines.size).firstOrNull { !it.isGap }?.time?.toLong()
                    until?.let { began to it }
                }
                else -> null
            }?.takeIf { (from, until) -> until > from }
            val swept = remember(lineItem, lineIndex) {
                lineItem?.takeIf { !itemInstrumental && !it.words.isNullOrEmpty() }?.let {
                    SweptLine.of(it, lines.getOrNull(lineIndex + 1)?.time ?: durationMs.toInt())
                }?.takeIf { it.hasWords }
            }
            when {
                breakSpan != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    NoteMark(ink)
                    BreakBar(lineText, breakSpan, clock, ink)
                }
                swept != null -> SweptStripText(swept, clock, ink)
                itemInstrumental -> Row(verticalAlignment = Alignment.CenterVertically) {
                    // A break with no sung line after it: nothing to count down to.
                    NoteMark(ink)
                    Text(
                        text = lineText,
                        style = MaterialTheme.typography.titleMedium,
                        color = ink.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    // No word timing: the line crosses its overflow over its own time.
                    val from = lineItem!!.time.toLong()
                    val until = lines.getOrNull(lineIndex + 1)?.time?.toLong() ?: durationMs
                    var rtl by remember(lineText) { mutableStateOf(false) }
                    Text(
                        text = lineText,
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        onTextLayout = {
                            rtl = it.lineCount > 0 && it.getParagraphDirection(0) == ResolvedTextDirection.Rtl
                        },
                        modifier = Modifier.lyricMarquee { viewport, width ->
                            val across = lineMarqueeOffset(from, until, clock.value, viewport, width)
                            if (rtl) (width - viewport) - across else across
                        },
                    )
                }
            }
        }
        Chevron(ink)
    }
}

/** A break counts itself down: a thin bar from the break's own stamp to the next sung line. */
@Composable
private fun BreakBar(label: String, span: Pair<Long, Long>, clock: State<Long>, ink: Color) {
    val (from, until) = span
    // Sized by an invisible line of the strip's own type, so nothing under the strip moves.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.width(BreakBarWidth).semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BreakBarHeight)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(ink.copy(alpha = 0.22f), cornerRadius = radius)
                    val through = ((clock.value - from).toFloat() / (until - from)).coerceIn(0f, 1f)
                    if (through > 0f) {
                        drawRoundRect(
                            ink.copy(alpha = 0.85f),
                            size = Size(size.width * through, size.height),
                            cornerRadius = radius,
                        )
                    }
                },
        )
    }
}

/** A word-timed line on one line: dim, with the sung part lit, kept in view as it scrolls. */
@Composable
private fun SweptStripText(line: SweptLine, clock: State<Long>, ink: Color) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val wordStarts = remember(line) { LongArray(line.words.size) { line.words[it].startMs } }
    val wordRights = remember(line, layout) {
        val measured = layout
        val last = line.text.lastIndex
        if (measured == null || last < 0 || measured.layoutInput.text.length != line.text.length) null
        else line.wordSpans.map { measured.getBoundingBox(it.last.coerceIn(0, last)).right }.toFloatArray()
    }
    val rtl = layout?.let { it.lineCount > 0 && it.getParagraphDirection(0) == ResolvedTextDirection.Rtl } == true
    val style = MaterialTheme.typography.titleMedium
    Box(
        modifier = Modifier.lyricMarquee { viewport, width ->
            val rights = wordRights
            when {
                rtl -> (width - viewport) - lineMarqueeOffset(line.startMs, line.endMs, clock.value, viewport, width)
                rights == null -> 0f
                else -> wordMarqueeOffset(wordStarts, line.endMs, rights, clock.value, viewport, width)
            }
        },
    ) {
        Text(
            text = line.text,
            style = style,
            color = ink.copy(alpha = StripDimAlpha),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout = it },
        )
        Text(
            text = line.text,
            style = style,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val measured = layout ?: return@drawWithContent
                    val at = clock.value
                    when {
                        at >= line.endMs -> drawContent()
                        at <= line.startMs -> Unit
                        else -> sweepTo(measured, line.revealedChars(at), line.sweepSpans, feather = true)
                    }
                },
        )
    }
}

/** Shown for a few seconds once a lookup came back empty, then left to fade. */
@Composable
private fun UnavailableLine(trackKey: Any, ink: Color) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(UnavailableHoldMs)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) StripDimAlpha else 0f,
        animationSpec = tween(UnavailableFadeMs),
        label = "lyricsUnavailable",
    )
    Text(
        text = stringResource(R.string.lyrics_not_found),
        style = MaterialTheme.typography.titleMedium,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { this.alpha = alpha },
    )
}
