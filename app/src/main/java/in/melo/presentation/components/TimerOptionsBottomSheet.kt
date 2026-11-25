package `in`.melo.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.melo.R
import `in`.melo.playback.SleepTimer
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.ui.glass.MeloSlider
import `in`.melo.ui.theme.MeloLayout

/**
 * The sleep timer: after this song, after a few songs, after a set time, or any length up to
 * twelve hours on its own page. A running timer is ticked, counts down under the title, and can
 * be turned off from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerOptionsBottomSheet(
    onPlayCounter: (count: Int) -> Unit,
    activeTimerValueDisplay: String?,
    activeTimerDurationMinutes: Int?,
    playCount: Float,
    isEndOfTrackTimerActive: Boolean,
    onDismiss: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingCustom by rememberSaveable { mutableStateOf(false) }
    val songsLeft = playCount.toInt()
    val custom = activeTimerDurationMinutes?.takeIf { it !in SleepTimer.PRESETS }
    val running = activeTimerDurationMinutes != null || songsLeft > 0

    MeloBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = MeloLayout.PageGutter)
                .padding(bottom = MeloLayout.Space4),
        ) {
            if (pickingCustom) {
                CustomTimerPage(
                    initialMinutes = custom ?: 90,
                    onBack = { pickingCustom = false },
                    onStart = { minutes ->
                        onSetPredefinedTimer(minutes)
                        onDismiss()
                    },
                )
                return@Column
            }
            TimerHeader(
                title = stringResource(R.string.sleep_timer_title),
                subtitle = when {
                    activeTimerDurationMinutes != null && activeTimerValueDisplay != null ->
                        stringResource(R.string.sleep_timer_pauses_in, activeTimerValueDisplay)
                    isEndOfTrackTimerActive -> stringResource(R.string.sleep_timer_pauses_after_song)
                    songsLeft > 1 -> stringResource(R.string.sleep_timer_pauses_after_songs, songsLeft)
                    else -> stringResource(R.string.sleep_timer_idle)
                },
            )
            MeloGroupedList {
                // Finishing the song is what people reach for at the end of a listen, so it leads.
                TimerRow(
                    label = stringResource(R.string.sleep_timer_after_song),
                    selected = isEndOfTrackTimerActive,
                ) {
                    onSetEndOfTrackTimer(true)
                    onDismiss()
                }
                SongCountRow(
                    activeCount = songsLeft.takeIf { it > 1 },
                    onStart = { count ->
                        onPlayCounter(count)
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(MeloLayout.Space4))
            MeloGroupedList {
                SleepTimer.PRESETS.forEach { minutes ->
                    TimerRow(
                        label = timerLength(minutes),
                        selected = minutes == activeTimerDurationMinutes,
                    ) {
                        onSetPredefinedTimer(minutes)
                        onDismiss()
                    }
                }
                TimerRow(
                    label = stringResource(R.string.sleep_timer_custom),
                    value = custom?.let { timerLength(it) },
                    selected = custom != null,
                    chevron = true,
                ) { pickingCustom = true }
            }
            if (running) {
                Spacer(Modifier.height(MeloLayout.Space4))
                MeloGroupedList {
                    TimerRow(
                        label = stringResource(R.string.sleep_timer_turn_off),
                        selected = false,
                        color = MaterialTheme.colorScheme.error,
                    ) {
                        onCancelTimer()
                        onCancelCountedPlay()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MeloLayout.Space1, bottom = MeloLayout.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = MeloLayout.Space1)) {
                Icon(MeloIcons.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        }
        Column(Modifier.weight(1f).padding(start = if (onBack == null) MeloLayout.Space1 else 0.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimerRow(
    label: String,
    selected: Boolean,
    value: String? = null,
    chevron: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = MeloLayout.PageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = MeloLayout.Space2),
            )
        }
        if (selected) {
            Icon(
                MeloIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = MeloLayout.Space3).size(18.dp),
            )
        }
        if (chevron) {
            Icon(
                MeloIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = MeloLayout.Space2).size(14.dp),
            )
        }
    }
}

/** "After 3 songs", with − and + beside it; the row itself starts it. */
@Composable
private fun SongCountRow(activeCount: Int?, onStart: (Int) -> Unit) {
    var count by remember(activeCount) { mutableIntStateOf(activeCount ?: 2) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = activeCount == count, role = Role.Button) { onStart(count) }
            .padding(start = MeloLayout.PageGutter, end = MeloLayout.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.sleep_timer_after_songs, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (activeCount == count) {
            Icon(
                MeloIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = MeloLayout.Space2).size(18.dp),
            )
        }
        StepButton("−", stringResource(R.string.sleep_timer_less), enabled = count > 2) { count-- }
        StepButton("+", stringResource(R.string.sleep_timer_more), enabled = count < SleepTimer.MAX_SONGS) { count++ }
    }
}

/** Any length up to twelve hours: a large readout over a slider, with − and + for one step. */
@Composable
private fun CustomTimerPage(initialMinutes: Int, onBack: () -> Unit, onStart: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    TimerHeader(
        title = stringResource(R.string.sleep_timer_custom),
        subtitle = stringResource(R.string.sleep_timer_idle),
        onBack = onBack,
    )
    Text(
        text = timerLength(minutes),
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MeloLayout.Space2, bottom = MeloLayout.Space3),
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeloLayout.Space1),
    ) {
        StepButton("−", stringResource(R.string.sleep_timer_less), enabled = minutes > SleepTimer.STEP_MINUTES) {
            minutes -= SleepTimer.STEP_MINUTES
        }
        MeloSlider(
            value = SleepTimer.fractionOf(minutes),
            onValueChange = { minutes = SleepTimer.minutesAt(it) },
            modifier = Modifier.weight(1f),
        )
        StepButton("+", stringResource(R.string.sleep_timer_more), enabled = minutes < SleepTimer.MAX_MINUTES) {
            minutes += SleepTimer.STEP_MINUTES
        }
    }
    Spacer(Modifier.height(MeloLayout.Space6))
    MeloButton(
        text = stringResource(R.string.sleep_timer_start),
        onClick = { onStart(minutes) },
        icon = MeloIcons.Timer,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepButton(label: String, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.25f),
        )
    }
}

/** "45 min", "1 h", or "1 h 25 min". */
@Composable
private fun timerLength(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.sleep_timer_minutes_short, m)
        m == 0 -> stringResource(R.string.sleep_timer_hours_short, h)
        else -> stringResource(R.string.sleep_timer_hours_minutes_short, h, m)
    }
}
