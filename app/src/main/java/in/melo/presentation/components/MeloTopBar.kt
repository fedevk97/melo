package `in`.melo.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.label
import `in`.melo.ui.theme.separator

/**
 * A page's top bar the way iOS draws it: a large bold title sitting on the page, which
 * shrinks into a small title on a see-through bar (with a hairline under it) once the
 * page scrolls. Pair it with [TopAppBarDefaults.exitUntilCollapsedScrollBehavior] and
 * hang that behaviour's nestedScrollConnection on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeloLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Transparent,
    scrolledContainerColor: Color = barMaterialColor(),
    showHairline: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val hairline = colors.separator
    LargeTopAppBar(
        title = {
            Text(
                title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        },
        modifier = modifier.drawWithContent {
            drawContent()
            val shown = scrollBehavior.state.collapsedFraction
            if (showHairline && shown > 0f) {
                val y = size.height - MeloLayout.Hairline.toPx() / 2f
                drawLine(hairline, Offset(0f, y), Offset(size.width, y), MeloLayout.Hairline.toPx(), alpha = shown)
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor,
            titleContentColor = colors.label,
            navigationIconContentColor = colors.label,
            actionIconContentColor = colors.label,
        ),
        scrollBehavior = scrollBehavior,
        expandedHeight = 112.dp,
    )
}
