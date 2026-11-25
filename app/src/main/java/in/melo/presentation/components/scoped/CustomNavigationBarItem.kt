package `in`.melo.presentation.components.scoped

import `in`.melo.ui.glass.pressBounce
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.melo.presentation.components.meloPress
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.MeloMotion

/**
 * One tab of the floating tab bar: glyph over a small label, with tab semantics and a
 * full-height touch target. The selection pill is not drawn here; the bar draws one
 * shared pill behind every item and slides it to the selected one.
 */
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compactMode: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.primary,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurface,
    selectedTextColor: Color = selectedIconColor,
    unselectedTextColor: Color = unselectedIconColor,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val showLabel = !compactMode && label != null && (alwaysShowLabel || selected)
    val iconColor by animateColorAsState(
        if (selected) selectedIconColor else unselectedIconColor, MeloMotion.fast(), label = "tabIcon",
    )
    val textColor by animateColorAsState(
        if (selected) selectedTextColor else unselectedTextColor, MeloMotion.fast(), label = "tabText",
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1.08f else 1f, MeloMotion.bounce(), label = "tabIconScale",
    )
    Column(
        modifier.fillMaxHeight().heightIn(min = MeloLayout.ControlSize)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (!showLabel && contentDescription != null) this.contentDescription = contentDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TabLabelGap, Alignment.CenterVertically),
    ) {
        CompositionLocalProvider(LocalContentColor provides iconColor) {
            // The glyph alone sinks under the finger and springs back past its size.
            Box(Modifier.size(MeloLayout.IconDefault).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                .pressBounce(interactionSource).clearAndSetSemantics {}) {
                if (selected) selectedIcon() else icon()
            }
        }
        if (showLabel) ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )) { label?.invoke() }
    }
}

private val TabLabelGap = 2.dp
