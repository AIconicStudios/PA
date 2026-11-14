package com.aiconicstudios.pa.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.Category
import com.aiconicstudios.pa.data.ScheduleMath

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityEditor(
    initial: Activity?,
    defaultDay: Int,
    defaultStart: Int,
    allActivities: List<Activity>,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onSave: (Activity) -> Unit,
    onDelete: (Activity) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: Category.STUDIES) }
    var days by remember { mutableStateOf(initial?.days ?: setOf(defaultDay)) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: defaultStart) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: (defaultStart + 60).coerceAtMost(24 * 60)) }
    var color by remember { mutableIntStateOf(initial?.color ?: category.defaultColor) }
    var colorTouched by remember { mutableStateOf(initial != null) }
    var reminder by remember { mutableStateOf(initial?.reminder ?: true) }
    var lead by remember { mutableIntStateOf(initial?.leadMinutes ?: 0) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val newId = remember { java.util.UUID.randomUUID().toString() }

    val duration = end - start
    val draft = Activity(
        id = initial?.id ?: newId,
        title = title.trim().ifEmpty { category.label },
        category = category,
        days = days,
        startMinute = start,
        durationMinutes = duration,
        color = color,
        reminder = reminder,
        leadMinutes = lead,
        notes = notes.trim(),
    )
    val conflicts = if (duration > 0) ScheduleMath.conflictsFor(draft, allActivities) else emptyList()
    val valid = days.isNotEmpty() && duration > 0

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60 % 24, current % 60, use24h).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New activity" else "Edit activity") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is it?") },
                    placeholder = { Text(category.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Label("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = {
                                category = c
                                if (!colorTouched) color = c.defaultColor
                            },
                            label = { Text("${c.emoji} ${c.label}") },
                        )
                    }
                }

                Label("Repeats every week on")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ScheduleMath.dayShort[d - 1].take(2),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickDays("Weekdays", setOf(1, 2, 3, 4, 5)) { days = it }
                    QuickDays("Weekend", setOf(6, 7)) { days = it }
                    QuickDays("Daily", (1..7).toSet()) { days = it }
                }

                Label("Time")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickTime(start) { s -> val d = end - start; start = s; end = (s + d.coerceAtLeast(15)).coerceAtMost(24 * 60) } }) {
                        Text(ScheduleMath.formatTime(start, use24h))
                    }
                    Text("  –  ")
                    OutlinedButton(onClick = { pickTime(end) { e -> end = if (e == 0) 24 * 60 else e } }) {
                        Text(ScheduleMath.formatTime(end, use24h))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (duration > 0) ScheduleMath.formatDuration(duration) else "⚠️",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90, 120, 180).forEach { m ->
                        FilterChip(
                            selected = duration == m,
                            onClick = { end = (start + m).coerceAtMost(24 * 60) },
                            label = { Text(ScheduleMath.formatDuration(m)) },
                        )
                    }
                }
                if (duration <= 0) ErrorText("The end time must be after the start time.")

                Label("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockPalette.indices.forEach { i ->
                        val (bg, accent) = blockColor(i)
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(bg)
                                .border(if (i == color) 3.dp else 1.dp, accent, CircleShape)
                                .clickable { color = i; colorTouched = true }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("⏰ Alarm", fontWeight = FontWeight.SemiBold)
                        Text("Rings at the start, every week", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = reminder, onCheckedChange = { reminder = it })
                }
                if (reminder) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10, 15, 30).forEach { m ->
                            FilterChip(
                                selected = lead == m,
                                onClick = { lead = m },
                                label = { Text(if (m == 0) "On time" else "$m min early") },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes for your coach (optional)") },
                    placeholder = { Text("e.g. beginner, bad knee, exams on 12 Dec") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                if (conflicts.isNotEmpty()) {
                    ErrorText("Overlaps with " + conflicts.joinToString { "${it.title} (${ScheduleMath.daysLabel(it.days.intersect(days))})" })
                }

                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete activity", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.title}?") },
            text = { Text("This removes it from every day, cancels its alarm and clears its coach workspace.") },
            confirmButton = {
                TextButton(onClick = { onDelete(initial) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ErrorText(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

@Composable
private fun QuickDays(label: String, value: Set<Int>, onPick: (Set<Int>) -> Unit) {
    TextButton(onClick = { onPick(value) }, modifier = Modifier.padding(0.dp)) { Text(label) }
}
