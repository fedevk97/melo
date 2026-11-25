package `in`.melo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Named colour jobs on top of the Material roles, after the iOS semantic colours.
 *
 * Material's names say where a colour sits ("onSurfaceVariant"); these say what it
 * is for, so a second line of text, a hairline or a pressed fill is the same grey on
 * every screen without each one picking an alpha of its own.
 */

/** Primary text: titles and anything the eye should land on first. */
val ColorScheme.label: Color get() = onSurface

/** A row's second line, metadata, captions. */
val ColorScheme.secondaryLabel: Color get() = onSurfaceVariant

/** Text that should barely register: placeholders, disabled rows, timestamps. */
val ColorScheme.tertiaryLabel: Color get() = onSurface.copy(alpha = 0.32f)

/** Hairline dividers between rows and around grouped lists. */
val ColorScheme.separator: Color get() = outlineVariant

/** The fill behind a small control (chips, search field, round icon buttons). */
val ColorScheme.controlFill: Color get() = onSurface.copy(alpha = 0.10f)

/** The chosen option among several (a sort order, a primary artist): a step up from [controlFill]. */
val ColorScheme.selectedFill: Color get() = onSurface.copy(alpha = 0.18f)

/** A pressed row or tile. */
val ColorScheme.pressedFill: Color get() = onSurface.copy(alpha = 0.08f)

/** The page behind grouped lists (Settings), so the groups stand off it. */
val ColorScheme.groupedBackground: Color get() = surfaceContainerLowest

/** The groups themselves on [groupedBackground]. */
val ColorScheme.groupedSurface: Color get() = surfaceContainer

/**
 * The hairline drawn round every piece of artwork, so a black cover on a black page
 * (or a white one on white) still reads as a square.
 */
val ColorScheme.artworkOutline: Color get() = onSurface.copy(alpha = 0.12f)
