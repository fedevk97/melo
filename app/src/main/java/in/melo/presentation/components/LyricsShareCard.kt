package `in`.melo.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import `in`.melo.R
import `in`.melo.presentation.components.player.artworkColourField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The picked lines as one picture you can send.
 *
 * Drawn on a software canvas rather than captured from the screen: a screenshot carries the
 * scrubber, the blur falloff and whatever lines happened to be in view, while this is the verse,
 * the cover and the mark, the same on every phone. It also draws the same from API 26 up, which
 * offscreen Compose does not.
 *
 * The type size is chosen down a fixed ladder until the rows fit, and the card is then grown to
 * exactly the room they take, inside 1080×1920. The bottom rung is a floor: the pick's character
 * budget is what keeps a verse from needing smaller type.
 */

/** The card laid out once, ready to draw at any moment of the song. */
internal suspend fun lyricsCardPainter(
    context: Context,
    card: LyricsShareRequest,
    story: Boolean,
    showLogo: Boolean,
    transparent: Boolean = false,
): LyricsCardPainter {
    val cover = card.artworkUrl?.let { loadArtwork(context, it) }
    return withContext(Dispatchers.Default) {
        val type = CardFonts(context)
        val layout = if (story) layoutStory(context, type, card, cover, showLogo, transparent)
        else layoutCard(context, type, card, cover, showLogo, transparent)
        LyricsCardPainter(layout, card.lines)
    }
}

/** The cover as a software bitmap, small enough to draw quickly, or null. */
internal suspend fun loadArtwork(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(ARTWORK_PX).build()
    (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.let { drawable ->
        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: ARTWORK_PX
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: ARTWORK_PX
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, w, h)
                drawable.draw(Canvas(it))
            }
        }
    }
}.getOrNull()

/** The faces the lyrics screen draws with: SF Pro Display. */
private class CardFonts(context: Context) {
    private val heavy = font(context, R.font.sf_pro_display_heavy, Typeface.DEFAULT_BOLD)
    private val bold = font(context, R.font.sf_pro_display_bold, Typeface.DEFAULT_BOLD)
    private val regular = font(context, R.font.sf_pro_display_medium, Typeface.DEFAULT)

    fun heading(size: Float, color: Int) = paint(heavy, size, color)
    fun body(size: Float, color: Int, bold: Boolean = false) = paint(if (bold) this.bold else regular, size, color)

    private fun paint(face: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
}

internal class PlacedRow(val row: CardRow, val x: Float, val baseline: Float)

internal class CardLayout(val width: Int, val height: Int, val base: Bitmap, val rows: List<PlacedRow>)

private fun place(plan: Plan, x: Float, top: Float): List<PlacedRow> {
    var y = top
    return plan.rows.map { row ->
        val baseline = y + row.gapBefore - row.paint.ascent() +
            if (row.isGap) row.paint.textSize * GAP_MARKER_OFFSET else 0f
        y += row.gapBefore + row.height
        PlacedRow(row, x, baseline)
    }
}

/** The card: the mark at the top right, the passage, and the song along the foot. */
private fun layoutCard(
    context: Context,
    type: CardFonts,
    card: LyricsShareRequest,
    cover: Bitmap?,
    showLogo: Boolean,
    transparent: Boolean,
): CardLayout {
    val headTop = if (showLogo) HEADER_BOTTOM + GAP_ABOVE_BODY else FOOTER_PAD
    val frameRoom = MAX_CARD_H - FOOTER_THUMB - FOOTER_PAD - GAP_BELOW_BODY - headTop
    val plan = fittestPlan(type, card.lines, frameRoom)
    val cardH = headTop + plan.content + GAP_BELOW_BODY + FOOTER_THUMB + FOOTER_PAD

    val bitmap = Bitmap.createBitmap(CARD_W, cardH.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent) drawBackdrop(canvas, cover, card.artworkUrl.hashCode(), CARD_W.toFloat(), cardH)

    if (showLogo) {
        val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(BRAND, CARD_W - CARD_MARGIN, HEADER_BASELINE, brand)
        drawMark(context, canvas, CARD_W - CARD_MARGIN - brand.measureText(BRAND) - LOGO_GAP - MARK_SIZE,
            HEADER_BASELINE - MARK_SIZE + 6f, MARK_SIZE, 0xE6FFFFFF.toInt())
    }

    val thumbY = cardH - FOOTER_PAD - FOOTER_THUMB
    drawArtwork(canvas, cover, CARD_MARGIN, thumbY, FOOTER_THUMB)
    val textX = CARD_MARGIN + FOOTER_THUMB + 34f
    val room = CARD_W - CARD_MARGIN - textX
    val title = type.body(50f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(card.song.title, title, room), textX, thumbY + 74f, title)
    val artist = type.body(42f, 0x99FFFFFF.toInt())
    canvas.drawText(ellipsised(card.song.displayArtist, artist, room), textX, thumbY + 132f, artist)

    return CardLayout(bitmap.width, bitmap.height, bitmap, place(plan, CARD_MARGIN, headTop))
}

/**
 * The story: the whole 9:16 frame is the song's blurred colours, with the passage in a frosted
 * panel in the middle and the song along its foot, clear of a story app's own header and reply bar.
 */
private fun layoutStory(
    context: Context,
    type: CardFonts,
    card: LyricsShareRequest,
    cover: Bitmap?,
    showLogo: Boolean,
    transparent: Boolean,
): CardLayout {
    val height = MAX_CARD_H
    val bitmap = Bitmap.createBitmap(CARD_W, height.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent) drawBackdrop(canvas, cover, card.artworkUrl.hashCode(), CARD_W.toFloat(), height)

    val left = (CARD_W - STORY_PANEL_W) / 2f
    val contentW = STORY_PANEL_W - STORY_PAD * 2
    val bandH = STORY_BAND_PAD * 2 + STORY_THUMB
    val lyricRoom = STORY_PANEL_MAX_H - STORY_PAD * 2 - bandH
    val plan = fittestPlan(type, card.lines, lyricRoom, contentW, STORY_LADDER)
    val panelH = STORY_PAD + plan.content + STORY_PAD + bandH
    val top = (height - panelH) / 2f
    val panel = RectF(left, top, left + STORY_PANEL_W, top + panelH)

    // Frosted glass on a software canvas: the backdrop is already a blur, so a light veil and a
    // hairline rim read as the same material.
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = 0x12000000
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, fill)
    fill.color = 0x1FFFFFFF
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, fill)
    val bandTop = panel.bottom - bandH
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(panel, STORY_RADIUS, STORY_RADIUS, Path.Direction.CW) })
    fill.color = 0x1AFFFFFF
    canvas.drawRect(panel.left, bandTop, panel.right, panel.bottom, fill)
    canvas.restore()
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x1FFFFFFF
    }
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, rim)

    val x = left + STORY_PAD
    val thumbY = bandTop + STORY_BAND_PAD
    drawArtwork(canvas, cover, x, thumbY, STORY_THUMB)
    val textX = x + STORY_THUMB + 28f
    val room = left + STORY_PANEL_W - STORY_PAD - textX
    val title = type.body(36f, Color.WHITE, bold = true)
    val artist = type.body(36f, 0xE6FFFFFF.toInt())
    val rows = if (showLogo) 3 else 2
    val lineH = 44f
    var baseline = thumbY + (STORY_THUMB - lineH * rows) / 2f + lineH - 10f
    canvas.drawText(ellipsised(card.song.title, title, room), textX, baseline, title)
    baseline += lineH
    canvas.drawText(ellipsised(card.song.displayArtist, artist, room), textX, baseline, artist)
    if (showLogo) {
        baseline += lineH
        val brand = type.body(30f, 0x99FFFFFF.toInt(), bold = true)
        val markH = 26f
        drawMark(context, canvas, textX, baseline - markH + 3f, markH, 0x99FFFFFF.toInt())
        canvas.drawText(BRAND, textX + markH + 8f, baseline, brand)
    }
    return CardLayout(bitmap.width, bitmap.height, bitmap, place(plan, x, top + STORY_PAD))
}

/**
 * Draws the card at a moment of the song the way the lyrics screen draws the same lines: the
 * line being sung lit by the soft-edged sweep, its words lifting and its held notes swelling, the words still to come at the screen's unsung
 * brightness, and lines further from it falling off the same way. With no position, every line
 * is lit: the still card.
 *
 * Not thread-safe: it reuses its paints. One instance per drawing thread.
 */
internal class LyricsCardPainter(private val layout: CardLayout, private val lines: List<LyricsShareLine>) {
    val width: Int get() = layout.width
    val height: Int get() = layout.height

    private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scratch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val growth = CharGrowth()

    /** Each line's place among the sung lines (gaps don't count), for the falloff. */
    private val rank = IntArray(lines.size).also { ranks ->
        var next = 0
        lines.forEachIndexed { index, line -> ranks[index] = if (line.isGap) -1 else next++ }
    }

    /** The card at [positionMs] (track time; null for the still card), into [into] when given. */
    fun render(positionMs: Long?, into: Bitmap? = null): Bitmap {
        val bitmap = into ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        drawOn(Canvas(bitmap), positionMs)
        return bitmap
    }

    fun drawOn(canvas: Canvas, positionMs: Long?) {
        canvas.drawBitmap(layout.base, 0f, 0f, basePaint)
        val active = positionMs?.let { activeShareLine(lines, it) } ?: -1
        for (placed in layout.rows) drawRow(canvas, placed, positionMs, active)
    }

    private fun drawRow(canvas: Canvas, placed: PlacedRow, positionMs: Long?, active: Int) {
        val row = placed.row
        val line = lines.getOrNull(row.line)
        if (positionMs == null || row.isGap || line == null || active < 0) {
            canvas.drawText(row.text, placed.x, placed.baseline, row.paint)
            return
        }
        val distance = abs(rank[row.line] - rank[active]).coerceAtMost(LINE_FALLOFF.lastIndex)
        val alpha = row.paint.alpha * LINE_FALLOFF[distance]
        val timing = line.timing
        val start = line.startMs ?: timing?.startMs ?: 0L
        val swept = timing != null && timing.hasWords && !row.rtl
        if (!swept) {
            // No word timings: the line lights whole when it starts, as on the lyrics screen.
            scratch.set(row.paint)
            scratch.alpha = (alpha * if (positionMs >= start) 1f else UNSUNG_ALPHA).roundToInt()
            canvas.drawText(row.text, placed.x, placed.baseline, scratch)
            return
        }
        timing!!
        // How far the sweep has got, in this row's own characters.
        val revealedInLine = if (row.isSub) {
            val lead = timing.text.length.coerceAtLeast(1)
            (timing.revealedChars(positionMs) / lead).coerceIn(0f, 1f) * (line.subText?.length ?: 0)
        } else {
            timing.revealedChars(positionMs)
        }
        val revealed = (revealedInLine - row.start).coerceIn(0f, row.text.length.toFloat())
        val sung = positionMs >= timing.endMs
        val dim = if (sung) 1f else UNSUNG_ALPHA
        val lifts = !row.isSub

        // The unsung copy underneath, then the lit one over it up to the sweep.
        scratch.set(row.paint)
        scratch.alpha = (alpha * dim).roundToInt()
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = false)
        if (revealed <= 0f || (sung && revealed >= row.text.length)) return

        scratch.alpha = alpha.roundToInt()
        if (revealed >= row.text.length) {
            drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
            return
        }
        val size = row.paint.textSize
        val edge = placed.x + sweepX(row, revealed, if (row.isSub) emptyList() else timing.sweepSpans)
        // The feather runs ahead of the sweep, into what is still to come, and grows in over the
        // first stretch of each row, as on the lyrics screen.
        val feather = minOf(size * FEATHER_RATIO, edge - placed.x)
        val top = placed.baseline + row.paint.ascent() - size * HEADROOM_RATIO
        val bottom = placed.baseline + row.paint.descent() + size * 0.1f
        val save = canvas.saveLayer(0f, top, layout.width.toFloat(), bottom, null)
        canvas.clipRect(0f, top, edge + feather, bottom)
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
        if (feather > 0.5f) {
            fade.shader = LinearGradient(edge, 0f, edge + feather, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, top, layout.width.toFloat(), bottom, fade)
        }
        canvas.restoreToCount(save)
    }

    /**
     * The row's text with each word at its lift, and a held word letter by letter at its swell,
     * with, when [bloom], the glow its letters give off.
     */
    @Suppress("LongParameterList")
    private fun drawPieces(
        canvas: Canvas,
        placed: PlacedRow,
        paint: Paint,
        positionMs: Long,
        timing: SweptLine,
        lifts: Boolean,
        bloom: Boolean,
    ) {
        val row = placed.row
        val text = row.text
        if (!lifts) {
            canvas.drawText(text, placed.x, placed.baseline, paint)
            return
        }
        val size = paint.textSize
        val rise = size * RISE_RATIO
        val rowStart = row.start
        val rowEnd = rowStart + text.length
        fun run(from: Int, to: Int, dy: Float) {
            if (to <= from) return
            canvas.drawText(text, from, to, placed.x + paint.measureText(text, 0, from), placed.baseline + dy, paint)
        }
        var cursor = 0
        timing.wordSpans.forEachIndexed { index, span ->
            val from = maxOf(span.first, rowStart) - rowStart
            val to = minOf(span.last + 1, rowEnd) - rowStart
            if (to <= from) return@forEachIndexed
            run(cursor, from, 0f)
            val grow = timing.growingAt(index)
            if (grow != null && positionMs >= grow.startMs && positionMs <= grow.restsAtMs) {
                for (k in from until to) {
                    val letter = rowStart + k - span.first
                    grow.sampleInto(letter, positionMs, growth)
                    val left = placed.x + paint.measureText(text, 0, k)
                    val w = paint.measureText(text, k, k + 1)
                    val cx = left + w / 2f + growth.shift * size
                    val y = placed.baseline - growth.rise * rise
                    canvas.save()
                    canvas.scale(growth.scale, growth.scale, cx, y)
                    if (bloom && growth.bloom > 0.01f) {
                        glow.set(paint)
                        glow.alpha = (paint.alpha * growth.bloom.coerceAtMost(1f)).roundToInt()
                        glow.maskFilter = BlurMaskFilter(size * GLOW_RATIO, BlurMaskFilter.Blur.NORMAL)
                        canvas.drawText(text, k, k + 1, cx - w / 2f, y, glow)
                    }
                    canvas.drawText(text, k, k + 1, cx - w / 2f, y, paint)
                    canvas.restore()
                }
            } else {
                run(from, to, -timing.wordLift(index, positionMs) * rise)
            }
            cursor = to
        }
        run(cursor, text.length, 0f)
    }

    /** Where [chars] characters into [row] falls, from the row's left edge. */
    private fun sweepX(row: CardRow, chars: Float, spans: List<IntRange>): Float {
        val text = row.text
        val paint = row.paint
        val at = row.start + chars
        // Within a timed piece the edge crosses its width at a steady pace.
        val piece = spans.firstOrNull { at >= it.first && at < it.last + 1 }
        if (piece != null && piece.first >= row.start && piece.last + 1 <= row.start + text.length) {
            val from = paint.measureText(text, 0, piece.first - row.start)
            val to = paint.measureText(text, 0, piece.last + 1 - row.start)
            return from + (to - from) * (at - piece.first) / (piece.last + 1 - piece.first)
        }
        // A space between words, or a piece broken over a wrap: letter by letter.
        val k = chars.toInt().coerceIn(0, text.length)
        val here = paint.measureText(text, 0, k)
        val next = paint.measureText(text, 0, (k + 1).coerceAtMost(text.length))
        return here + (next - here) * (chars - k)
    }
}

/** The line being sung at [positionMs]: the last to have started, or the first. */
internal fun activeShareLine(lines: List<LyricsShareLine>, positionMs: Long): Int {
    var first = -1
    var last = -1
    lines.forEachIndexed { index, line ->
        if (line.isGap) return@forEachIndexed
        val start = line.startMs ?: line.timing?.startMs ?: return@forEachIndexed
        if (first < 0) first = index
        if (positionMs >= start) last = index
    }
    return if (last >= 0) last else first
}

/** The song's colours alone, [height] tall at the card's width: under the video when nothing else is. */
internal fun renderLyricsShareBackdrop(cover: Bitmap?, artworkUrl: String?, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_W, height, Bitmap.Config.ARGB_8888)
    drawBackdrop(Canvas(bitmap), cover, artworkUrl.hashCode(), CARD_W.toFloat(), height.toFloat())
    return bitmap
}

/**
 * The player's own backdrop: the cover's colour field, already blurred smooth, stretched over the
 * frame, under the same scrim, which follows how bright the cover is so white type always reads.
 * [seed] arranges the field as the player does for the same cover. Near black without a cover.
 */
private fun drawBackdrop(canvas: Canvas, cover: Bitmap?, seed: Int, width: Float, height: Float) {
    canvas.drawColor(BACKDROP_BASE)
    val field = cover?.let { artworkColourField(it, seed) }
    // Dithered, so the long gentle gradients don't band once compressed.
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    field?.let { canvas.drawBitmap(it.first, null, RectF(0f, 0f, width, height), paint) }
    val lum = field?.second ?: 0f
    fun black(alpha: Float) = Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
    paint.shader = LinearGradient(
        0f, 0f, 0f, height,
        intArrayOf(black(0.22f + 0.38f * lum), black(0.10f + 0.30f * lum), black(0.22f + 0.38f * lum), black(0.34f + 0.40f * lum)),
        floatArrayOf(0f, 0.35f, 0.60f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, paint)
}

/** The cover as a rounded square, or a plain tile when there is none. */
private fun drawArtwork(canvas: Canvas, cover: Bitmap?, x: Float, y: Float, size: Float) {
    val rect = RectF(x, y, x + size, y + size)
    val radius = size * 0.12f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (cover == null) {
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(rect, radius, radius, paint)
        return
    }
    val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val scale = maxOf(size / cover.width, size / cover.height)
    shader.setLocalMatrix(Matrix().apply {
        setScale(scale, scale)
        postTranslate(x + (size - cover.width * scale) / 2f, y + (size - cover.height * scale) / 2f)
    })
    paint.shader = shader
    canvas.drawRoundRect(rect, radius, radius, paint)
}

/** Melo's mark, tinted, in a [size] square at [x], [y]. */
private fun drawMark(context: Context, canvas: Canvas, x: Float, y: Float, size: Float, tint: Int) {
    val mark = runCatching { ResourcesCompat.getDrawable(context.resources, R.drawable.ic_melo_mark, null) }.getOrNull() ?: return
    mark.mutate().setTint(tint)
    mark.setBounds(x.roundToInt(), y.roundToInt(), (x + size).roundToInt(), (y + size).roundToInt())
    mark.draw(canvas)
}

private fun ellipsised(text: String, paint: Paint, room: Float): String {
    if (paint.measureText(text) <= room) return text
    val ellipsis = "…"
    val fit = paint.breakText(text, true, room - paint.measureText(ellipsis), null)
    return text.take(fit.coerceAtLeast(0)).trimEnd() + ellipsis
}

// ── Fitting ────────────────────────────────────────────────────────────────

internal class CardRow(
    val text: String,
    val paint: Paint,
    val height: Float,
    val gapBefore: Float,
    val isGap: Boolean = false,
    /** Which of the request's lines this row belongs to. */
    val line: Int = -1,
    /** Where the row starts in its line's text (or its sub-line's). */
    val start: Int = 0,
    val isSub: Boolean = false,
    /** Right-to-left text lights whole rather than sweeping. */
    val rtl: Boolean = false,
)

private class Plan(val rows: List<CardRow>, val ascent: Float) {
    val content: Float get() = ascent + rows.fold(0f) { total, row -> total + row.gapBefore + row.height }
}

/** The largest size on [ladder] whose rows fit in [room], or the bottom rung. */
private fun fittestPlan(
    type: CardFonts,
    lines: List<LyricsShareLine>,
    room: Float,
    width: Float = CARD_CONTENT_W,
    ladder: FloatArray = FIT_LADDER,
): Plan {
    for (size in ladder) {
        val plan = planAt(type, lines, size, width)
        if (plan.content <= room) return plan
    }
    return planAt(type, lines, ladder.last(), width)
}

/** The rows at one [size], with the lyrics screen's tracking and line advance as ratios of it. */
private fun planAt(type: CardFonts, lines: List<LyricsShareLine>, size: Float, width: Float): Plan {
    val primary = type.heading(size, Color.WHITE).apply { letterSpacing = TRACKING }
    val subSize = (size * SUB_RATIO).coerceAtLeast(SUB_FLOOR)
    val secondary = type.heading(subSize, SUB_COLOR).apply { letterSpacing = SUB_TRACKING }
    val rows = mutableListOf<CardRow>()
    lines.forEachIndexed { index, line ->
        if (line.isGap) {
            val marker = type.body(size * 0.65f, 0x66FFFFFF)
            rows += CardRow("(...)", marker, marker.descent() - marker.ascent(), 0f, isGap = true, line = index)
            return@forEachIndexed
        }
        var firstOfLine = true
        fun rowsOf(source: String, paint: Paint, height: Float, isSub: Boolean) {
            val rtl = isRtl(source)
            var cursor = 0
            wrapText(source, paint, width).forEach { piece ->
                val at = source.indexOf(piece, cursor).takeIf { it >= 0 } ?: cursor
                cursor = at + piece.length
                // One verse line is one paragraph: the space goes above its first row only.
                val gap = if (rows.isNotEmpty() && firstOfLine && index > 0) size * 0.55f else 0f
                rows += CardRow(piece, paint, height, gap, line = index, start = at, isSub = isSub, rtl = rtl)
                firstOfLine = false
            }
        }
        rowsOf(line.text, primary, size * LEAD, isSub = false)
        val sub = line.subText
        if (!sub.isNullOrBlank() && sub != line.text) rowsOf(sub, secondary, subSize * SUB_LEAD, isSub = true)
    }
    return Plan(rows, -primary.ascent())
}

/**
 * Greedy wrap at [width]. Breaks where the text has to, then walks back to a space when there is
 * one, so lyrics without spaces (Japanese, Chinese) still wrap instead of running off the card.
 */
internal fun wrapText(text: String, paint: Paint, width: Float): List<String> {
    val source = text.trim()
    if (source.isEmpty()) return listOf("")
    val out = mutableListOf<String>()
    var rest = source
    while (rest.isNotEmpty()) {
        val fit = paint.breakText(rest, true, width, null)
        if (fit <= 0) {
            out += rest
            break
        }
        val space = rest.lastIndexOf(' ', fit - 1)
        val take = if (space > 0 && fit < rest.length) space else fit
        out += rest.substring(0, take).trimEnd()
        rest = rest.substring(take).trimStart()
    }
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
}

private fun isRtl(text: String): Boolean =
    !java.text.Bidi(text, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()

// ── Geometry ───────────────────────────────────────────────────────────────

private const val ARTWORK_PX = 600
private const val CARD_W = 1080

/** 9:16: a ceiling on the card's height, which otherwise follows its passage. */
private const val MAX_CARD_H = 1920f
private const val CARD_MARGIN = 72f
private const val CARD_CONTENT_W = CARD_W - CARD_MARGIN * 2
private const val BRAND = "Melo"
private const val MARK_SIZE = 52f
private const val LOGO_GAP = 16f
private const val HEADER_BASELINE = 132f
private const val HEADER_BOTTOM = 170f
private const val GAP_ABOVE_BODY = 20f
private const val GAP_BELOW_BODY = 20f
private const val FOOTER_THUMB = 160f
private const val FOOTER_PAD = 96f
private const val SUB_FLOOR = 24f
private const val GAP_MARKER_OFFSET = 0.18f
private const val BACKDROP_BASE = 0xFF121212.toInt()

/** The sizes tried, largest first; the last is the floor. */
private val FIT_LADDER = floatArrayOf(96f, 88f, 80f, 72f, 64f, 56f, 50f, 44f, 38f, 32f)

private const val STORY_PANEL_W = 760f
private const val STORY_PANEL_MAX_H = 1280f
private const val STORY_PAD = 52f
private const val STORY_RADIUS = 44f
private const val STORY_THUMB = 128f
private const val STORY_BAND_PAD = 40f
private val STORY_LADDER = floatArrayOf(60f, 56f, 52f, 48f, 44f, 40f, 36f, 32f)

// ── The lyrics screen's look, as ratios of its 34sp line ───────────────────

/** Words still to be sung, against the full ink of those sung. */
private const val UNSUNG_ALPHA = 0.45f

/** Lines further from the one sung fall off as on the lyrics screen. */
private val LINE_FALLOFF = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)

/** The sweep's soft edge: 30dp against the 34sp line. */
private const val FEATHER_RATIO = 30f / 34f

/** A sung word's lift: 2dp against the 34sp line. */
private const val RISE_RATIO = 2f / 34f

/** Room above a row for a lifted, swelling letter. */
private const val HEADROOM_RATIO = 0.35f

/** The bloom behind a held letter: 6dp against the 34sp line. */
private const val GLOW_RATIO = 6f / 34f
private const val TRACKING = -0.7f / 34f
private const val SUB_TRACKING = -0.7f / 20f
private const val LEAD = 41f / 34f
private const val SUB_LEAD = 25f / 20f
private const val SUB_RATIO = 20f / 34f
private const val SUB_COLOR = 0xD9FFFFFF.toInt()
