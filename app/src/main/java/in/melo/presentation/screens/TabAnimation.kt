package `in`.melo.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout

/** Shared category control: a quiet outline and a clear filled selection. */
@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurface,
    /** Outline an unselected tab; off where the tabs already sit inside a shared track. */
    unselectedBorder: Boolean = true,
    shape: Shape = MeloLayout.CardShape,
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit,
) {
    val selected = index == selectedIndex
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(if (selected) selectedColor else unselectedColor, tween(200), label = "tabFill")
    val ink by animateColorAsState(if (selected) onSelectedColor else onUnselectedColor, tween(200), label = "tabInk")

    Tab(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp).heightIn(min = 48.dp)
            .clip(shape).background(background)
            .border(0.75.dp, if (selected || !unselectedBorder) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape),
        selected = selected, text = content,
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        selectedContentColor = ink, unselectedContentColor = ink,
    )
}
