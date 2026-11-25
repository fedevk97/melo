package `in`.melo.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloAlpha
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.controlFill

enum class MeloButtonSize { Large, Medium }
enum class MeloButtonStyle { Primary, Tonal, Outlined, Text }

@Composable
fun MeloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    size: MeloButtonSize = MeloButtonSize.Large,
    style: MeloButtonStyle = MeloButtonStyle.Primary,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val activeContainer = when (style) {
        MeloButtonStyle.Primary -> containerColor ?: scheme.primary
        // iOS's grey see-through fill, so a secondary button reads the same on a page, a sheet or glass.
        MeloButtonStyle.Tonal, MeloButtonStyle.Outlined -> containerColor ?: scheme.controlFill
        MeloButtonStyle.Text -> containerColor ?: Color.Transparent
    }
    val activeContent = when (style) {
        MeloButtonStyle.Primary -> contentColor ?: scheme.onPrimary
        MeloButtonStyle.Tonal -> contentColor ?: scheme.onSurface
        MeloButtonStyle.Outlined, MeloButtonStyle.Text -> contentColor ?: scheme.primary
    }
    val buttonColors = when (style) {
        MeloButtonStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = activeContainer,
            contentColor = activeContent,
            disabledContainerColor = activeContainer,
            disabledContentColor = activeContent,
        )
        MeloButtonStyle.Tonal -> ButtonDefaults.filledTonalButtonColors(
            containerColor = activeContainer,
            contentColor = activeContent,
            disabledContainerColor = activeContainer,
            disabledContentColor = activeContent,
        )
        MeloButtonStyle.Outlined -> ButtonDefaults.outlinedButtonColors(
            containerColor = activeContainer,
            contentColor = activeContent,
            disabledContainerColor = activeContainer,
            disabledContentColor = activeContent,
        )
        MeloButtonStyle.Text -> ButtonDefaults.textButtonColors(
            containerColor = activeContainer,
            contentColor = activeContent,
            disabledContainerColor = activeContainer,
            disabledContentColor = activeContent,
        )
    }
    val minHeight = when (size) {
        MeloButtonSize.Large -> MeloLayout.ButtonLarge
        MeloButtonSize.Medium -> MeloLayout.ButtonMedium
    }
    val pressModifier = modifier
        .heightIn(min = minHeight)
        .alpha(if (enabled) 1f else MeloAlpha.Disabled)
        .meloPress(interactionSource, enabled)
    val content: @Composable RowScope.() -> Unit = {
        if (leadingContent != null || icon != null) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(MeloLayout.IconMedium)) }
            }
            Spacer(Modifier.size(MeloLayout.Space2))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    when (style) {
        MeloButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = pressModifier,
            enabled = enabled,
            shape = MeloLayout.PillShape,
            colors = buttonColors,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            border = null,
            contentPadding = PaddingValues(horizontal = MeloLayout.Space4),
            interactionSource = interactionSource,
            content = content,
        )
        MeloButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = pressModifier,
            enabled = enabled,
            shape = MeloLayout.PillShape,
            colors = buttonColors,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = MeloLayout.Space4),
            interactionSource = interactionSource,
            content = content,
        )
        MeloButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = pressModifier,
            enabled = enabled,
            shape = MeloLayout.PillShape,
            colors = buttonColors,
            border = null,
            contentPadding = PaddingValues(horizontal = MeloLayout.Space4),
            interactionSource = interactionSource,
            content = content,
        )
        MeloButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = pressModifier,
            enabled = enabled,
            shape = MeloLayout.PillShape,
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = MeloLayout.Space4),
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun MeloIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Null for the page's glass, as iOS's round bar buttons; a colour for a filled button. */
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MeloLayout.ControlSize)
            .alpha(if (enabled) 1f else MeloAlpha.Disabled)
            .meloPress(interactionSource, enabled),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor,
        ),
        interactionSource = interactionSource,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(MeloLayout.ControlVisual)
                .then(if (containerColor == null) Modifier.meloPageGlass(MeloLayout.PillShape)
                else Modifier.clip(MeloLayout.PillShape).background(containerColor)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(MeloLayout.IconDefault),
            )
        }
    }
}

/** A round or rounded icon button's secondary look: [controlFill] under the page's ink, as iOS's grey buttons. */
@Composable
fun meloTonalIconColors(): androidx.compose.material3.IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.controlFill,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** The same for a text button. */
@Composable
fun meloTonalButtonColors(): androidx.compose.material3.ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.controlFill,
    contentColor = MaterialTheme.colorScheme.onSurface,
)
