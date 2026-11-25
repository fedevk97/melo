package `in`.melo.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.melo.ui.glass.MeloMenuPopover
import `in`.melo.ui.glass.meloMenuColors
import `in`.melo.ui.theme.MeloLayout

/**
 * The pop-up menu behind every "more" button, drawn like an iOS menu: one rounded
 * panel frosted over the blurred app, grown out of its button, rows with the label
 * first and the icon at the end, and a thicker band between groups ([MeloMenuDivider]).
 *
 * Compose it inside the button's Box, as a DropdownMenu would be: it lines up
 * with that box.
 */
@Composable
fun MeloMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    MeloMenuPopover(expanded = expanded, onDismiss = onDismiss, content = content)
}

/** One row: the label, then its icon at the far end. Pressing washes the row instead of a ripple. */
@Composable
fun MeloMenuItem(label: String, icon: @Composable () -> Unit, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = meloMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = (if (destructive) MaterialTheme.colorScheme.error else colors.label)
        .let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RowShape)
            .background(if (pressed) colors.pressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = MenuRowHeight)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = ink,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(MeloLayout.Space3))
        CompositionLocalProvider(LocalContentColor provides ink) {
            Box(Modifier.size(MeloLayout.IconMedium), contentAlignment = Alignment.Center) { icon() }
        }
    }
}

/** The band between two groups of a [MeloMenu]. */
@Composable
fun MeloMenuDivider() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = MeloLayout.Space1).height(MeloLayout.Space2)
            .background(meloMenuColors().separator),
    )
}

private val RowShape = RoundedCornerShape(14.dp)
private val MenuRowHeight = 44.dp
