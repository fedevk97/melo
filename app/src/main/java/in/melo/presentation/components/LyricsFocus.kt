package `in`.melo.presentation.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.presentation.components.snapping.ExperimentalSnapperApi
import `in`.melo.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import `in`.melo.presentation.components.snapping.rememberSnapperFlingBehavior
import `in`.melo.data.model.SyncedLine
import `in`.melo.utils.BubblesLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/*
 * The synced lyrics as a focused stack: the line being sung is lit word by word by a soft-edged
 * sweep, the lines around it stay readable and the rest fall back and soften with distance, and
 * as the song moves on the rows arrive one behind another rather than sliding as a block.
 */

/** The one curve every handover runs on (dim, blur, scale and scroll): away quickly, in softly. */
internal val LyricEasing = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LyricSettleMs = 400

/** How the stack falls away either side of the focused line, by distance from it. */
private val FalloffAlpha = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private val FalloffBlur = floatArrayOf(0f, 1f, 1f, 1.7f, 2.4f)

/** What the words still to be sung read at, against the full ink of the ones sung. */
private const val UnsungAlpha = 0.45f
/** Every row at one brightness while the list is read by hand. */
private const val BrowsingAlpha = 0.8f

/** Lines not chosen while choosing lines for a share card. */
private const val PickingAlpha = 0.45f
private const val InactiveScale = 0.98f
private const val PressedScale = 0.96f

/** The sweep's leading edge fades over about a character and a half instead of ending on a cut. */
private val WipeFeather = 30.dp

/** The glow held notes give off on the focused line: its strength, its spread and the room it needs. */
private const val GlowAlpha = 0.62f
private val GlowRadius = 6.dp

/** The focused line's own bloom: wider and fainter than a held note's glow. */
private val LineBloomRadius = 12.dp
private const val LineBloomShare = 0.8f
private val GlowRoom = 10.dp
/** How far a sung word lifts off the line, and how much room above it a swelling letter may take. */
private val WordRise = 2.dp
private const val GrowHeadroom = 3f

/** The rows after the one scrolled to set off this much later each, up to this many rows. */
private const val StaggerSteps = 7
private const val StaggerFraction = 0.09f

/** How long the panel takes to move to a line, and how far ahead of it it sets off. */
private const val LeadMinMs = 350L
private const val LeadMaxMs = 500L

/** The last word of a line is swept over at most this long, so a gap after it isn't crawled. */
private const val LastWordMaxMs = 1_200L

/** A hand scroll holds the list where it was put for this long after the last touch. */
private const val BrowseIdleMs = 5_000L

/** The blur strength the falloff table is drawn at; the stored strength scales it. */
private const val BaseBlurStrength = 2.5f

/** A synced line laid out for the sweep: its text, and where and when each word is. */
@Immutable
internal class SweptLine(
    val text: String,
    val startMs: Long,
    /** The words, each with its own time and, where a source split it, its syllables. */
    val words: List<LyricWord>,
) {
    val hasWords: Boolean get() = words.isNotEmpty()
    /** When the last word finishes, or the line's start when it has no word timing. */
    val endMs: Long = words.lastOrNull()?.endMs ?: startMs
    /** When the sweep has finished with the line. */
    val sweepEndMs: Long get() = endMs

    /** Where each word sits in [text], walked so a repeated word lines up with its own occurrence. */
    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    /** Each separately timed piece: a word's syllables where it was split, the word itself where not. */
    val sweepSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        words.flatMapIndexed { index, word ->
            val span = wordSpans[index]
            if (word.syllables.isEmpty()) listOf(span)
            else word.syllables.map { (span.first + it.startChar) until (span.first + it.endChar) }
        }
    }

    /**
     * How many characters (fractionally) are lit at [positionMs]. Within a word it follows that
     * word's own pace; the space after a word fills over the pause before the next one.
     */
    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= startMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) return start + word.charsSungAt(positionMs)
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    /** Whether anything on this line is off the floor at [positionMs]. */
    fun isLifted(positionMs: Long): Boolean {
        val first = words.firstOrNull() ?: return false
        if (positionMs <= first.startMs) return false
        return positionMs < words.last().endMs + RISE_MS || isGrowing(positionMs)
    }

    /** From here until [animatesUntilMs] anything drawn for this line moves; outside it the line is still. */
    val animatesFromMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        minOf(startMs, words.firstOrNull()?.startMs ?: startMs)
    }

    /** See [animatesFromMs]. */
    val animatesUntilMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        val settled = words.lastOrNull()?.let { it.endMs + RISE_MS.toLong() } ?: endMs
        val grown = growingWords.maxOfOrNull { it.restsAtMs } ?: Long.MIN_VALUE
        maxOf(endMs, settled, grown)
    }

    /** How far word [index] has lifted, 0..1: it rises over its own length and settles after it. */
    fun wordLift(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val riseMs = (word.endMs - word.startMs).toFloat().coerceIn(RISE_MIN_MS, RISE_MS)
        val rising = ((positionMs - word.startMs) / riseMs).coerceIn(0f, 1f)
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return liftHeight(word) * smooth(minOf(rising, falling))
    }

    /** How much of word [index]'s lift is left: all of it while sung, easing to 0 once past. */
    fun wordFall(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return liftHeight(word) * smooth(falling)
    }

    /** The words held long enough to be animated letter by letter. */
    val growingWords: List<GrowingWord> by lazy(LazyThreadSafetyMode.NONE) {
        words.mapIndexedNotNull { index, word -> if (word.canGrow()) GrowingWord(index, word) else null }
    }

    fun growingAt(index: Int): GrowingWord? = growingWords.firstOrNull { it.index == index }

    fun isGrowing(positionMs: Long): Boolean = growingWords.any {
        positionMs >= it.startMs && positionMs <= it.restsAtMs
    }

    companion object {
        fun of(line: SyncedLine, nextLineStartMs: Int): SweptLine {
            val start = line.time.toLong()
            val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs)
            val pieces = line.words?.let(::sanitizeSyncedWords).orEmpty()
            if (pieces.isEmpty()) return SweptLine(sanitizeLyricLineText(line.line), start, emptyList())
            val pieceStart = LongArray(pieces.size) { pieces[it].time.toLong() }
            val pieceEnd = LongArray(pieces.size) { i ->
                if (i < pieces.lastIndex) normalizeWordEndTime(pieceStart[i], pieceStart[i + 1], lineEnd)
                else min(lineEnd, pieceStart[i] + LastWordMaxMs).coerceAtLeast(pieceStart[i] + 1)
            }
            // A piece that doesn't start a new word is a syllable of the word before it.
            val words = mutableListOf<LyricWord>()
            var i = 0
            while (i < pieces.size) {
                var j = i + 1
                while (j < pieces.size && !pieces[j].startsNewWord) j++
                val text = StringBuilder()
                val parts = mutableListOf<LyricSyllable>()
                for (k in i until j) {
                    val from = text.length
                    text.append(pieces[k].word)
                    parts += LyricSyllable(pieceStart[k], pieceEnd[k], from, text.length)
                }
                val word = text.toString()
                words += LyricWord(pieceStart[i], pieceEnd[j - 1], word, LyricSyllable.normalized(parts, word.length))
                i = j
            }
            return SweptLine(words.joinToString(" ") { it.text }, start, words)
        }
    }
}

/**
 * The playhead as the lyrics read it: the player's position, carried forward frame by frame
 * between its updates while playing, so the sweep moves smoothly rather than in quarter-second
 * steps. Small backward corrections are held rather than shown, so the sweep never twitches back.
 */
@Composable
internal fun rememberLyricClock(
    positionFlow: StateFlow<Long>,
    offsetMs: Int,
    overrideMs: Long?,
    isPlaying: Boolean,
): State<Long> {
    val clock = remember { mutableLongStateOf((positionFlow.value + offsetMs).coerceAtLeast(0L)) }
    LaunchedEffect(positionFlow, offsetMs, overrideMs, isPlaying) {
        if (overrideMs != null) {
            clock.longValue = overrideMs
            return@LaunchedEffect
        }
        var anchor = positionFlow.value
        var anchorNanos = System.nanoTime()
        launch {
            positionFlow.collect {
                anchor = it
                anchorNanos = System.nanoTime()
                if (!isPlaying) clock.longValue = (it + offsetMs).coerceAtLeast(0L)
            }
        }
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                val target = (anchor + (now - anchorNanos) / 1_000_000L + offsetMs).coerceAtLeast(0L)
                val shown = clock.longValue
                clock.longValue = if (target < shown && shown - target < 250L) shown else target
            }
        }
    }
    return clock
}

/** How far ahead of the next line the panel sets off: the run-up before it, within bounds. */
private fun scrollLead(lines: List<SyncedLine>, swept: List<SweptLine>, now: Long): Long {
    val current = resolveCurrentLineIndex(lines, now)
    if (current < 0) return LeadMinMs
    val next = lines.getOrNull(current + 1) ?: return LeadMinMs
    val gap = next.time.toLong() - swept[current].sweepEndMs
    return gap.coerceIn(LeadMinMs, LeadMaxMs)
}

/** One handover: how far the list is going and how long it takes. */
private class ScrollRun(val delta: Float, val durationMs: Int) {
    val spanMs: Float get() = durationMs * (1f + StaggerFraction * StaggerSteps)
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    isPlaying: Boolean,
    ink: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    highlightOffsetFraction: Float,
    blurEnabled: Boolean = true,
    blurStrength: Float = BaseBlurStrength,
    reducedMotion: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Lines chosen for a share card, by index. */
    picks: Set<Int> = emptySet(),
    /** Choosing lines: taps toggle them and the list stops following the song. */
    picking: Boolean = false,
    onLineLongPress: (Int) -> Unit = {},
    onLineToggle: (Int) -> Unit = {},
    footer: LazyListScope.() -> Unit = {}
) {
    val clock = rememberLyricClock(playbackPositionFlow, lyricsSyncOffset, positionOverrideMs, isPlaying)
    val isPreviewSeeking = positionOverrideMs != null
    val swept = remember(lines) {
        lines.mapIndexed { i, line -> SweptLine.of(line, lines.getOrNull(i + 1)?.time ?: Int.MAX_VALUE) }
    }
    val linesState = rememberUpdatedState(lines)
    // Where the list is heading: a beat ahead of the singing, so the line is under the eye by
    // the time it starts rather than arriving after it.
    val focusLine by remember(lines) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.value
            val ahead = resolveCurrentLineIndex(linesState.value, now + scrollLead(linesState.value, swept, now))
            if (ahead >= 0) ahead else resolveCurrentLineIndex(linesState.value, now)
        }
    }

    // A hand scroll suspends the following until the reader has let go for a while.
    var browsing by remember { mutableStateOf(false) }
    var lastTouchEnd by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> browsing = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> lastTouchEnd = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(browsing, lastTouchEnd) {
        if (!browsing || lastTouchEnd == 0L) return@LaunchedEffect
        delay(BrowseIdleMs)
        if (!listState.isScrollInProgress) browsing = false
    }

    val run = remember { mutableStateOf(ScrollRun(0f, 1)) }
    val since: MutableFloatState = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var aligned by remember(lines) { mutableStateOf(false) }
    var lastIndex by remember(lines) { mutableIntStateOf(-1) }

    Box(modifier = modifier) {
        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                // The focused line's top sits at the anchor, not its middle, so a line that
                // wraps grows downwards from where the eye already is.
                val viewport = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                (viewport * highlightOffsetFraction).toInt()
                    .coerceAtMost(viewport - item.size).coerceAtLeast(0)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        LaunchedEffect(focusLine, lines.size, isPreviewSeeking, browsing, picking) {
            val target = focusLine
            if (target !in lines.indices) return@LaunchedEffect
            if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect
            if (!aligned) {
                listState.scrollToItem(target)
                snapToSnapIndex(listState, snapperLayoutInfo, target)
                aligned = true
                lastIndex = target
                return@LaunchedEffect
            }
            if ((browsing || picking) && !isPreviewSeeking) return@LaunchedEffect
            val jump = if (lastIndex >= 0) abs(target - lastIndex) else 0
            lastIndex = target
            if (isPreviewSeeking || jump > 3) {
                if (jump > 3) {
                    listState.scrollToItem(target)
                    snapToSnapIndex(listState, snapperLayoutInfo, target)
                } else {
                    animateToSnapIndex(listState, snapperLayoutInfo, target, tween(110, easing = FastOutSlowInEasing))
                }
                return@LaunchedEffect
            }
            val duration = scrollLead(lines, swept, clock.value).toInt()
            val distance = snapperLayoutInfo.distanceToIndexSnap(target).toFloat()
            if (distance == 0f) return@LaunchedEffect
            run.value = ScrollRun(distance, duration)
            if (!reducedMotion) {
                launch {
                    val startNanos = withFrameNanos { it }
                    since.floatValue = 0f
                    val span = run.value.spanMs
                    while (since.floatValue < span) {
                        withFrameNanos { since.floatValue = (it - startNanos) / 1_000_000f }
                    }
                    since.floatValue = Float.MAX_VALUE
                }
            }
            val spec: AnimationSpec<Float> = tween(duration, easing = LyricEasing)
            animateToSnapIndex(listState, snapperLayoutInfo, target, spec)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = lines, key = { index, item -> "${item.time}_$index" }) { index, line ->
                val distance = if (focusLine >= 0) abs(focusLine - index) else 4
                val step = distance.coerceAtMost(FalloffAlpha.lastIndex)
                // Rows behind the one being scrolled to fan out; the ones it leaves go together.
                val stagger = Modifier.graphicsLayer {
                    val r = run.value
                    val elapsed = since.floatValue
                    if (elapsed == Float.MAX_VALUE) return@graphicsLayer
                    val behind = if (r.delta >= 0f) index - focusLine else focusLine - index
                    val delay = behind.coerceIn(0, StaggerSteps) * StaggerFraction * r.durationMs
                    if (delay <= 0f) return@graphicsLayer
                    translationY = r.delta * (
                        LyricEasing.transform((elapsed / r.durationMs).coerceIn(0f, 1f)) -
                            LyricEasing.transform(((elapsed - delay) / r.durationMs).coerceIn(0f, 1f))
                        )
                }
                if (line.line.isNotBlank() || swept[index].text.isNotBlank()) {
                    FocusedLyricRow(
                        line = line,
                        swept = swept[index],
                        clock = clock,
                        isFocused = distance == 0 && !picking,
                        lineAlpha = when {
                            picking -> if (index in picks) 1f else PickingAlpha
                            browsing -> BrowsingAlpha
                            else -> FalloffAlpha[step]
                        },
                        blur = if (blurEnabled && !browsing && !picking) (FalloffBlur[step] * blurStrength / BaseBlurStrength).dp else 0.dp,
                        picked = picking && index in picks,
                        picking = picking,
                        ink = ink,
                        style = textStyle,
                        lyricsAlignment = lyricsAlignment,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        modifier = stagger.fillMaxWidth().testTag("synced_line_${line.time}"),
                        onClick = { if (picking) onLineToggle(index) else onLineClick(line) },
                        onLongClick = { onLineLongPress(index) },
                    )
                } else {
                    BubblesLine(
                        positionFlow = playbackPositionFlow,
                        time = line.time,
                        color = ink.copy(alpha = 0.5f),
                        nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE,
                        modifier = stagger.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
            footer()
        }
    }
}

@Composable
private fun FocusedLyricRow(
    line: SyncedLine,
    swept: SweptLine,
    clock: State<Long>,
    isFocused: Boolean,
    lineAlpha: Float,
    blur: Dp,
    ink: Color,
    style: TextStyle,
    lyricsAlignment: String,
    showTranslation: Boolean,
    showRomanization: Boolean,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
    picking: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val settle = tween<Float>(LyricSettleMs, easing = LyricEasing)
    val pickFill by animateFloatAsState(if (picked) 1f else 0f, tween(180), label = "lyricPicked")
    val alpha by animateFloatAsState(lineAlpha, settle, label = "lyricAlpha")
    val blurPx by animateFloatAsState(blur.value, settle, label = "lyricBlur")
    val glow by animateFloatAsState(if (isFocused && !picking) GlowAlpha else 0f, tween(420), label = "lyricGlow")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> PressedScale
            isFocused -> 1f
            else -> InactiveScale
        },
        animationSpec = tween(if (pressed) 120 else LyricSettleMs, easing = LyricEasing),
        label = "lyricScale",
    )
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }
    val pivotX = when (lyricsAlignment) {
        "center" -> 0.5f
        "right" -> 1f
        else -> 0f
    }
    val horizontal = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }
    val subStyle = remember(style) {
        style.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(pivotX, 0.5f)
            }
            .then(if (blurPx > 0.05f) Modifier.blur(blurPx.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .then(if (pickFill > 0f) Modifier.background(ink.copy(alpha = 0.14f * pickFill)) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // The line's own glow room makes up the rest of the row's padding.
            .padding(vertical = 2.dp),
        horizontalAlignment = horizontal,
    ) {
        SweptText(
            swept, clock, style, ink, textAlign,
            lit = picking,
            glowAlpha = glow,
            feather = isFocused,
            room = GlowRoom,
        )
        val romanization = line.romanization
        if (showRomanization && !romanization.isNullOrBlank()) {
            Text(romanization, style = subStyle, color = ink.copy(alpha = 0.85f), textAlign = textAlign,
                modifier = Modifier.padding(start = GlowRoom, end = GlowRoom, bottom = GlowRoom))
        }
        val translation = line.translation
        if (showTranslation && !translation.isNullOrBlank()) {
            Text(translation, style = subStyle, color = ink.copy(alpha = 0.6f), textAlign = textAlign,
                modifier = Modifier.padding(start = GlowRoom, end = GlowRoom, bottom = GlowRoom))
        }
    }
}

private const val PhaseBefore = 0
private const val PhaseMoving = 1
private const val PhaseAfter = 2

/**
 * The line three times over: the whole of it at the unsung brightness, a blurred copy for the glow
 * of held notes on the focused line, and the full-ink copy revealed up to where the singing is.
 * Sung words lift off the line and settle back; a held word swells a letter at a time. Outside the
 * stretch the line moves in, it is drawn at rest and stops reading the clock.
 */
@Composable
private fun SweptText(
    line: SweptLine,
    clock: State<Long>,
    style: TextStyle,
    ink: Color,
    textAlign: TextAlign,
    lit: Boolean = false,
    glowAlpha: Float = 0f,
    feather: Boolean = false,
    room: Dp = 0.dp,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val growth = remember { CharGrowth() }
    val phase = remember(line, clock) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.value
            when {
                now < line.animatesFromMs -> PhaseBefore
                now > line.animatesUntilMs -> PhaseAfter
                else -> PhaseMoving
            }
        }
    }
    val drawnAt: () -> Long = {
        when (phase.value) {
            PhaseMoving -> clock.value
            PhaseBefore -> line.animatesFromMs - 1
            else -> line.animatesUntilMs + 1
        }
    }
    val roomed = if (room > 0.dp) Modifier.padding(room) else Modifier
    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (lit) inner else Modifier
            .drawWithContent {
                val measured = layout
                if (measured == null || !line.hasWords) drawContent()
                else riseWith(measured, line, drawnAt(), room.toPx(), WordRise.toPx(), growth)
            }
            .then(inner)
    }
    val sweep = Modifier.drawWithContent {
        // While picking, every line reads at full ink so only the pick dims it.
        if (lit) {
            drawContent()
            return@drawWithContent
        }
        val position = drawnAt()
        when {
            position >= line.endMs && position >= line.startMs -> drawContent()
            position <= line.startMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position), line.sweepSpans, feather) }
        }
    }
    Box(contentAlignment = Alignment.TopStart) {
        Text(
            text = line.text,
            style = style,
            color = ink.copy(alpha = ink.alpha * UnsungAlpha),
            textAlign = textAlign,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().then(riseAgainst(roomed)),
        )
        if (glowAlpha > 0.01f && !lit) {
            // A soft light under the sung part of the focused line, whether it is timed by the
            // word or only by the line; held notes add their own brighter glow on top.
            Text(
                text = line.text,
                style = style,
                color = ink,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = glowAlpha * LineBloomShare }
                    .blur(LineBloomRadius, BlurredEdgeTreatment.Unbounded)
                    .then(
                        riseAgainst(
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .then(roomed)
                                .then(sweep),
                        ),
                    ),
            )
            Text(
                text = line.text,
                style = style,
                color = ink,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(GlowRadius, BlurredEdgeTreatment.Unbounded)
                    .then(roomed)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val measured = layout ?: return@drawWithContent
                        glowGrown(measured, line, drawnAt(), room.toPx(), WordRise.toPx(), growth)
                    },
            )
        }
        Text(
            text = line.text,
            style = style,
            color = ink,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().then(
                riseAgainst(
                    Modifier
                        .graphicsLayer {
                            compositingStrategy = if (feather) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                        }
                        .then(roomed)
                        .then(sweep),
                ),
            ),
        )
    }
}

/**
 * The letters of the held words, each at its own brightness, for the glow layer to blur. A line of
 * ordinary syllables draws nothing here: only a carried note gives off light.
 */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: SweptLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    val em = layout.layoutInput.style.fontSize.toPx()
    val length = layout.layoutInput.text.length
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = line.wordFall(word.index, positionMs)
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(left = from - overhang + dx, top = rowTop - peak * GrowHeadroom, right = to + overhang + dx, bottom = bottom) {
                translate(left = dx, top = dy) {
                    scale(growth.scale, growth.scale, Offset((from + to) / 2f, (rowTop + bottom) / 2f)) {
                        this@glowGrown.drawContent()
                    }
                }
                drawRect(color = Color.White.copy(alpha = growth.bloom), blendMode = BlendMode.DstIn)
            }
        }
    }
}

/**
 * Redraws the row with the word being sung lifted off the line and the ones behind it settling back.
 * The line is cut at word boundaries, so no glyph is sliced; a line with nothing lifted draws once.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: SweptLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val start = maxOf(span.first, lineStart)
            val end = minOf(span.last + 1, lineEnd)
            if (start >= end) continue
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val lift = line.wordLift(index, positionMs)
            if (held == null && lift <= 0.01f) continue
            val from = layout.xOn(start, visualLine, inset)
            val to = layout.xOn(end, visualLine, inset)
            if (to <= from) continue
            if (start > at) sliceRisen(edge, top, from, bottom, 0f)
            if (held != null) {
                growEach(layout, held, line, positionMs, visualLine, start, end, top, bottom, inset, peak, em, growth)
            } else {
                sliceRisen(from, top - peak, to, bottom, -lift * peak)
            }
            at = end
            edge = to
        }
        if (at < lineEnd) sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
    }
}

/**
 * One held word redrawn a letter at a time, each scaled about its own centre and clipped to where
 * it is going, so a swelling letter is not shaved by the box it was laid out in.
 */
@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: GrowingWord,
    line: SweptLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: CharGrowth,
) {
    val fall = line.wordFall(word.index, positionMs)
    val first = line.wordSpans[word.index].first
    val ceiling = top - peak * GrowHeadroom
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipShiftedDown(from - overhang + dx, ceiling, to + overhang + dx, bottom, dy) {
            translate(left = dx) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/** Clipped to the band, then drawn [dy] lower (or higher, for a negative [dy]). */
private inline fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(from: Float, top: Float, to: Float, bottom: Float, dy: Float) {
    if (to <= from) return
    clipShiftedDown(from, top, to, bottom, dy) { this@sliceRisen.drawContent() }
}

/**
 * Where an offset sits across the visual line it was cut from. The offset just past a wrapped row
 * belongs to the next row, so the row's ends are answered from its own edges, honouring direction.
 */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val lineStart = getLineStart(visualLine)
    val lineEndVisible = getLineEnd(visualLine, visibleEnd = true)
    val lineLeft = getLineLeft(visualLine) + inset
    val lineRight = getLineRight(visualLine) + inset
    val rtl = getParagraphDirection(lineStart.coerceIn(0, (layoutInput.text.length - 1).coerceAtLeast(0))) ==
        ResolvedTextDirection.Rtl
    return when {
        offset <= lineStart -> if (rtl) lineRight else lineLeft
        offset >= lineEndVisible -> if (rtl) lineLeft else lineRight
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset).coerceIn(lineLeft, lineRight)
    }
}

/**
 * Where a fractional character index sits across a visual line. Inside a timed piece that sits
 * whole on the row, the edge crosses the piece at a steady rate rather than letter by letter.
 */
private fun horizontalAt(layout: TextLayoutResult, chars: Float, visualLine: Int, spans: List<IntRange>): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEndVisible = layout.getLineEnd(visualLine, visibleEnd = true)
    val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)
    val piece = spans.firstOrNull { chars >= it.first && chars < it.last + 1 }
    if (piece != null && piece.first >= lineStart && piece.last + 1 <= lineEndVisible) {
        val from = layout.xOn(piece.first, visualLine, 0f)
        val to = layout.xOn(piece.last + 1, visualLine, 0f)
        return from + (to - from) * (chars - piece.first) / (piece.last + 1 - piece.first)
    }
    val index = chars.toInt().coerceIn(lineStart, lineEndAll)
    val here = layout.xOn(index, visualLine, 0f)
    val rawNext = index + 1
    val textLen = layout.layoutInput.text.length
    val spills = layout.getLineForOffset(rawNext.coerceIn(0, textLen)) != visualLine
    val next = if (spills && rawNext > lineEndVisible) {
        val rtl = layout.getParagraphDirection(lineStart.coerceIn(0, (textLen - 1).coerceAtLeast(0))) ==
            ResolvedTextDirection.Rtl
        if (rtl) layout.getLineLeft(visualLine) else layout.getLineRight(visualLine)
    } else {
        layout.xOn(rawNext.coerceAtMost(lineEndVisible), visualLine, 0f)
    }
    return here + (next - here) * (chars - index)
}

/**
 * Draws this text clipped to its first [revealed] characters, a visual line at a time. With
 * [feather], the edge fades out over the [WipeFeather] ahead of it rather than ending on a cut,
 * which needs the caller's offscreen layer to erase into.
 */
internal fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealed: Float, spans: List<IntRange>, feather: Boolean) {
    if (revealed <= 0f) return
    val textLen = layout.layoutInput.text.length
    if (revealed >= textLen) {
        drawContent()
        return
    }
    val rtl = layout.getParagraphDirection(layout.getLineStart(0).coerceIn(0, (textLen - 1).coerceAtLeast(0))) ==
        ResolvedTextDirection.Rtl
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)
        if (revealed <= lineStart) return
        val cut = revealed < lineEndAll
        val charPos = if (cut) horizontalAt(layout, revealed, visualLine, spans)
        else if (rtl) layout.getLineLeft(visualLine) else layout.getLineRight(visualLine)
        val lineLeft = layout.getLineLeft(visualLine)
        val lineRight = layout.getLineRight(visualLine)
        val edge = charPos.coerceIn(lineLeft, lineRight)
        val featherPx = if (feather && cut) {
            minOf(WipeFeather.toPx(), if (rtl) lineRight - edge else edge - lineLeft)
        } else 0f
        val lead = if (rtl) (edge - featherPx).coerceAtLeast(lineLeft) else (edge + featherPx).coerceAtMost(lineRight)
        val (clipLeft, clipRight) = if (rtl) lead to lineRight else lineLeft to lead
        if (clipLeft >= clipRight) continue
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(left = clipLeft, top = top, right = clipRight, bottom = bottom) {
            this@sweepTo.drawContent()
        }
        if (featherPx < 0.5f) continue
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = edge,
                    endX = if (rtl) edge - featherPx else edge + featherPx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/**
 * Fades the top and bottom edges of the lyrics to nothing, whatever is behind them, so lines
 * dissolve into the header and the controls rather than meeting a painted band.
 */
internal fun Modifier.lyricsEdgeFade(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.5f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.5f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                t to Color.Black,
                1f - b to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
