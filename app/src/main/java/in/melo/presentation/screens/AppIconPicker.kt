package `in`.melo.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.data.AppIcon
import `in`.melo.ui.theme.MeloLayout

private val IconPreviewSize = 60.dp
private val IconPreviewShape = RoundedCornerShape(16.dp)
private val IconColumnWidth = 76.dp

/** The home-screen icons side by side; tapping one puts it on the launcher straight away. */
@Composable
internal fun AppIconPicker() {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(AppIcon.current(context)) }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = MeloLayout.PageGutter - 6.dp, vertical = 14.dp)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            AppIconChoice(icon, selected = icon == chosen) {
                if (icon != chosen) {
                    AppIcon.apply(context, icon)
                    chosen = icon
                }
            }
        }
    }
}

@Composable
private fun AppIconChoice(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 2.5.dp else 0.dp, label = "iconRing")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, label = "iconScale")
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .width(IconColumnWidth)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(IconPreviewSize + 8.dp)
                .border(ring, ringColor, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon.preview),
                contentDescription = null,
                modifier = Modifier
                    .size(IconPreviewSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(IconPreviewShape)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), IconPreviewShape),
            )
        }
        Text(
            text = stringResource(icon.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
