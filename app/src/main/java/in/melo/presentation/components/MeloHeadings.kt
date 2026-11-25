package `in`.melo.presentation.components

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import `in`.melo.ui.glass.glassAccentFill
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.MeloMotion
import `in`.melo.ui.theme.controlFill
import `in`.melo.ui.theme.label
import `in`.melo.ui.theme.secondaryLabel

/*
 * The three pieces of type that frame every page: the large title at its top, the
 * header over each section, and the chips that filter what is under them. Every screen
 * takes these, so a heading looks and lines up the same wherever it is.
 */

/**
 * A page's large title ("Home", "Library", "Search"), 34sp bold, Apple Music's way of
 * saying where you are. [trailing] holds the page's own buttons (profile, settings),
 * centred on the title's line.
 */
@Composable
fun MeloLargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = MeloLayout.PageGutter),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(contentPadding).padding(top = MeloLayout.LargeTitleTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/**
 * The header over a section or shelf. When [onOpen] is given the whole header opens the
 * full list, shown by a quiet chevron after the title (as Apple Music does) instead of a
 * button of its own; the header stays [MeloLayout.ControlSize] tall so it is easy to hit.
 *
 * [contentPadding] is the page gutter by default; a list that already insets its items
 * passes [PaddingValues] of zero.
 */
@Composable
fun MeloSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = MeloLayout.PageGutter),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val tappable = if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onOpen) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MeloLayout.ControlSize)
            .then(tappable)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onOpen != null) Icon(
                imageVector = MeloIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondaryLabel,
                modifier = Modifier.size(MeloLayout.IconDefault),
            )
        }
        trailing()
    }
}

/**
 * A filter or choice chip: a grey pill that turns solid when chosen. The pill is
 * [ChipHeight] tall to read as small, but takes a full [MeloLayout.ControlSize] of touch.
 */
@Composable
fun MeloChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    /**
     * The chip lies on a main page over the colour background: unselected it is glass with a
     * rim in the song's colour, and selected it takes that colour.
     */
    onWash: Boolean = false,
) {
    if (onWash) {
        WashChip(label, selected, onClick, modifier, enabled)
        return
    }
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val washAccent: Color? = null
    val selectedFill = washAccent ?: colors.primary
    val selectedInk = if (washAccent == null) colors.onPrimary
        else if (washAccent.luminance() > 0.5f) Color.Black else Color.White
    val background by animateColorAsState(
        if (selected) selectedFill else colors.controlFill, MeloMotion.fast(), label = "chipBackground",
    )
    val foreground by animateColorAsState(
        if (selected) selectedInk else colors.label, MeloMotion.fast(), label = "chipForeground",
    )
    val glassy = washAccent != null && !selected
    Box(
        modifier = modifier
            .heightIn(min = MeloLayout.ControlSize)
            .meloPress(interactions, enabled)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, interactionSource = interactions, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(ChipHeight)
                .then(
                    if (glassy) Modifier.meloPageGlass(MeloLayout.PillShape)
                    else Modifier.clip(MeloLayout.PillShape).background(background)
                )
                .padding(horizontal = MeloLayout.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space1 + 2.dp),
        ) {
            if (leading != null) androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides foreground, content = leading,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (enabled) foreground else foreground.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
    }
}

/** How tall a [MeloChip]'s pill is drawn. */
val ChipHeight = 34.dp

/**
 * A chip on a main page over the colour background: the
 * page's glass, and chosen, the page's accent laid on it as more glass at 86% (solid in the
 * Solid style), its ink black or white by the accent's lightness. Chosen, it dips and springs
 * back; let go, it only loses its fill.
 */
@Composable
private fun WashChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val haptics = LocalHapticFeedback.current
    val accent = pageAccent()
    val solid = `in`.melo.ui.glass.LocalMeloSurface.current.style == `in`.melo.ui.glass.MeloSurfaceStyle.SOLID
    val fill by animateColorAsState(
        accent.copy(alpha = if (selected) (if (solid) 1f else WashChipFill) else 0f),
        androidx.compose.animation.core.tween(WashChipFillMs), label = "washChipFill",
    )
    val ink by animateColorAsState(
        when {
            !selected -> MaterialTheme.colorScheme.onBackground
            accent.luminance() > 0.5f -> Color.Black
            else -> Color.White
        },
        androidx.compose.animation.core.tween(WashChipFillMs), label = "washChipInk",
    )
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var seen by remember { androidx.compose.runtime.mutableStateOf(selected) }
    androidx.compose.runtime.LaunchedEffect(selected) {
        if (selected && !seen) {
            scale.snapTo(WashChipPressScale)
            scale.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                ),
            )
        }
        seen = selected
    }
    Box(
        modifier = modifier
            .heightIn(min = MeloLayout.ControlSize)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .meloPageGlass(WashChipShape)
                .glassAccentFill(WashChipShape, fill)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) ink else ink.copy(alpha = 0.38f),
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
            )
        }
    }
}

private val WashChipShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
private const val WashChipFill = 0.86f
private const val WashChipFillMs = 240
private const val WashChipPressScale = 0.88f
