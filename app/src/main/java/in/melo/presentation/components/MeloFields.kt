package `in`.melo.presentation.components

import `in`.melo.presentation.icons.MeloIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.MeloMotion
import `in`.melo.ui.theme.controlFill
import `in`.melo.ui.theme.label
import `in`.melo.ui.theme.secondaryLabel

/**
 * The search field: a grey pill with a magnifier, the text, and a clear button once
 * something is typed, as iOS draws it. No outline, no label floating above it.
 */
@Composable
fun MeloSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    clearDescription: String? = null,
    searchIconDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val text = MaterialTheme.typography.bodyLarge.copy(color = colors.label)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        textStyle = text,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(SearchFieldHeight).clip(MeloLayout.PillShape)
                    .background(colors.controlFill).padding(start = MeloLayout.Space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space2),
            ) {
                Icon(MeloIcons.Search, searchIconDescription, Modifier.size(MeloLayout.IconMedium), tint = colors.secondaryLabel)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, style = text, color = colors.secondaryLabel, maxLines = 1)
                    field()
                }
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(MeloMotion.fast()), exit = fadeOut(MeloMotion.fast())) {
                    IconButton(onClick = { onQueryChange("") }, Modifier.size(SearchFieldHeight)) {
                        Icon(MeloIcons.Cancel, clearDescription, Modifier.size(MeloLayout.IconMedium), tint = colors.secondaryLabel)
                    }
                }
            }
        },
    )
}

/** How tall the [MeloSearchField] pill is. Its row is padded out to a full touch target by its caller. */
val SearchFieldHeight = 40.dp

/**
 * What a page shows in place of its content when there is none to show: nothing found,
 * nothing saved yet, offline, a failed load. A quiet glyph, a short title, a line saying
 * why, and at most one way out ([action]), centred on the page.
 */
@Composable
fun MeloMessageState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = MeloLayout.Space8, vertical = MeloLayout.Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MeloLayout.Space2),
    ) {
        if (icon != null) Icon(
            icon, null, Modifier.padding(bottom = MeloLayout.Space2).size(MessageIconSize), tint = colors.secondaryLabel,
        )
        Text(
            title, Modifier.widthIn(max = MessageMaxWidth), style = MaterialTheme.typography.titleLarge,
            color = colors.label, textAlign = TextAlign.Center,
        )
        if (message != null) Text(
            message, Modifier.widthIn(max = MessageMaxWidth), style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryLabel, textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
    }
}

private val MessageIconSize = 48.dp
private val MessageMaxWidth = 320.dp
