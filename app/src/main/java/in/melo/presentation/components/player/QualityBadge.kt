package `in`.melo.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.melo.R
import `in`.melo.presentation.icons.MeloIcons
import kotlinx.coroutines.delay

/** What the badge between the timestamps can say; a state rather than a string, so a swap can tell one from the next. */
internal enum class QualityBadge { UPGRADING, HIGH_UPGRADING, HI_RES_LOSSLESS, LOSSLESS, HIGH }

private val LosslessCodecs = setOf("FLAC", "ALAC", "WAV")
private val KhzPart = Regex("""([\d.]+)\s*kHz""")
private val KbpsPart = Regex("""(\d+)\s*kbps""")

/** Lossy at or above this is the top of what lossy gets, a 320 kbps AAC rather than a 160 kbps Opus. */
private const val HighQualityKbps = 256

/** Above CD's rate: where a lossless stream counts as hi-res. */
private const val HiResKhz = 48f

/**
 * The badge for a stream described as "FLAC · 44.1 kHz · 943 kbps", read off what is decoding;
 * a figure the description leaves out is taken as unknown, never guessed.
 */
internal fun qualityBadgeOf(format: String?, upgrading: Boolean): QualityBadge? {
    val parts = format.orEmpty().split('·').map { it.trim() }
    val codec = parts.firstOrNull()?.uppercase().orEmpty()
    val lossless = codec in LosslessCodecs
    val khz = parts.firstNotNullOfOrNull { KhzPart.find(it)?.groupValues?.get(1)?.toFloatOrNull() }
    val kbps = parts.firstNotNullOfOrNull { KbpsPart.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    val high = !lossless && (kbps ?: 0) >= HighQualityKbps
    return when {
        upgrading && !lossless -> if (high) QualityBadge.HIGH_UPGRADING else QualityBadge.UPGRADING
        lossless -> if ((khz ?: 0f) > HiResKhz) QualityBadge.HI_RES_LOSSLESS else QualityBadge.LOSSLESS
        high -> QualityBadge.HIGH
        else -> null
    }
}

/** How long the badge waits on an empty state before taking itself down. */
private const val BadgeGoneGraceMs = 600L

/** One label leaving and the next arriving. */
private const val BadgeSwapMs = 350

/** How far "Upgrading quality" dips, and how long each half of a breath takes. */
private const val UpgradingPulseLow = 0.4f
private const val UpgradingPulseMs = 900

/**
 * The quality badge centred between the timestamps under the scrubber: "Lossless" and
 * "Hi-Res Lossless" with a shine sweeping across them, "Upgrading quality" breathing while a
 * better copy is looked for. A new label arrives from the right as the old one leaves left.
 */
@Composable
internal fun PlaybackQualityBadge(
    format: String?,
    upgrading: Boolean,
    ink: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val badge = qualityBadgeOf(format, upgrading)
    // Put up at once, taken down only once it has stayed gone a moment, so the step between an
    // upgrade ending and the new copy's format landing reads as one label replacing another.
    var shown by remember { mutableStateOf(badge) }
    LaunchedEffect(badge) {
        if (badge == null) delay(BadgeGoneGraceMs)
        shown = badge
    }
    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (slideInHorizontally(tween(BadgeSwapMs)) { it / 2 } + fadeIn(tween(BadgeSwapMs))) togetherWith
                (slideOutHorizontally(tween(BadgeSwapMs)) { -it / 2 } + fadeOut(tween(BadgeSwapMs))) using
                SizeTransform(clip = false)
        },
        label = "qualityBadge",
    ) { target ->
        if (target != null) {
            val tap = if (onClick == null) Modifier else Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            QualityBadgeLabel(target, ink, tap.padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun QualityBadgeLabel(badge: QualityBadge, ink: Color, modifier: Modifier) {
    when (badge) {
        QualityBadge.UPGRADING, QualityBadge.HIGH_UPGRADING -> BadgeLabel(
            text = stringResource(
                if (badge == QualityBadge.HIGH_UPGRADING) R.string.quality_high_upgrading else R.string.quality_upgrading,
            ),
            ink = ink,
            wave = true,
            shine = false,
            pulsing = true,
            modifier = modifier,
        )
        QualityBadge.HI_RES_LOSSLESS, QualityBadge.LOSSLESS -> BadgeLabel(
            text = stringResource(
                if (badge == QualityBadge.HI_RES_LOSSLESS) R.string.quality_hi_res_lossless else R.string.quality_lossless,
            ),
            ink = ink,
            wave = true,
            shine = true,
            modifier = modifier,
        )
        QualityBadge.HIGH -> BadgeLabel(stringResource(R.string.quality_high), ink, wave = false, shine = false, modifier = modifier)
    }
}

@Composable
private fun badgeStyle(): TextStyle = MaterialTheme.typography.labelMedium.let {
    it.copy(fontSize = (it.fontSize.value + 1).sp)
}

@Composable
private fun BadgeLabel(
    text: String,
    ink: Color,
    wave: Boolean,
    shine: Boolean,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "upgradingPulse").animateFloat(
            initialValue = 1f,
            targetValue = UpgradingPulseLow,
            animationSpec = infiniteRepeatable(tween(UpgradingPulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "upgradingPulseAlpha",
        )
    } else null
    Row(
        // Read in the layer, so a frame of the pulse is a redraw of this label alone.
        modifier = modifier.graphicsLayer {
            alpha = pulse?.value ?: 1f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = ink.copy(alpha = if (shine) 0.7f else 0.45f)
        if (wave) {
            Icon(MeloIcons.GraphicEq, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        } else {
            Icon(MeloIcons.Headphones, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(4.dp))
        if (shine) {
            ShineText(text, ink)
        } else {
            Text(text = text, style = badgeStyle(), color = ink.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Text with a highlight band sweeping left to right across it every three seconds. The glyphs are
 * drawn once in white and the moving band painted over them with SrcIn in the draw pass, so a
 * frame of the sweep is a redraw, never a recomposition.
 */
@Composable
internal fun ShineText(
    text: String,
    ink: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = badgeStyle().copy(fontWeight = FontWeight.SemiBold),
    baseAlpha: Float = 0.55f,
    textAlign: TextAlign? = null,
) {
    val progress = rememberInfiniteTransition(label = "qualityShine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart),
        label = "qualityShineProgress",
    )
    val base = ink.copy(alpha = baseAlpha)
    Text(
        text = text,
        style = style,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val width = size.width
                val band = width * 0.6f
                val center = -band + progress.value * (width + 2 * band)
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(0f to base, 0.5f to ink, 1f to base),
                        start = Offset(center - band, 0f),
                        end = Offset(center + band, 0f),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    )
}

/**
 * The full stream line under the transport, "FLAC · 44.1 kHz · 943 kbps", small and quiet;
 * tapping it opens the audio pipeline, as the badge does.
 */
@Composable
internal fun AudioStatsCaption(format: String?, ink: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (format.isNullOrBlank()) return
    val tap = if (onClick == null) Modifier else Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
    Text(
        text = format,
        style = MaterialTheme.typography.labelSmall,
        color = ink.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(tap).padding(vertical = 4.dp),
    )
}
