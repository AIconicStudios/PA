package com.aiconicstudios.pa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.AppState
import com.aiconicstudios.pa.data.Repository
import com.aiconicstudios.pa.data.ScheduleMath
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HourColumnWidth = 52.dp
private val HeaderHeight = 48.dp

/** What the editor dialog is working on. */
private data class EditorRequest(val activity: Activity?, val day: Int, val startMinute: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    state: AppState,
    onOpenWorkspace: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings = state.settings
    var editor by remember { mutableStateOf<EditorRequest?>(null) }

    // A clock that ticks every 30 s drives the "now" line and the Now/Next card.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Timetable", fontWeight = FontWeight.Bold)
                        Text(
                            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Repository.updateSettings { it.copy(hourHeightDp = (it.hourHeightDp - 16).coerceAtLeast(40)) }
                    }) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = {
                        Repository.updateSettings { it.copy(hourHeightDp = (it.hourHeightDp + 16).coerceAtMost(144)) }
                    }) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val start = ((ScheduleMath.minuteOf(now.toLocalTime()) / 30) + 1) * 30
                    editor = EditorRequest(null, now.dayOfWeek.value, start.coerceAtMost(23 * 60))
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Activity") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NowNextCard(state, now, onOpenWorkspace)
            if (state.activities.isEmpty()) EmptyHint()
            TimetableGrid(
                state = state,
                now = now,
                onEmptySlot = { day, minute -> editor = EditorRequest(null, day, minute) },
                onOpen = onOpenWorkspace,
                onEdit = { editor = EditorRequest(it, it.days.min(), it.startMinute) },
                hourHeight = settings.hourHeightDp.dp,
            )
        }
    }

    editor?.let { req ->
        ActivityEditor(
            initial = req.activity,
            defaultDay = req.day,
            defaultStart = req.startMinute,
            allActivities = state.activities,
            use24h = settings.use24h,
            onDismiss = { editor = null },
            onSave = { Repository.upsertActivity(it); editor = null },
            onDelete = { Repository.deleteActivity(it.id); editor = null },
        )
    }
}

@Composable
private fun NowNextCard(state: AppState, now: LocalDateTime, onOpen: (String) -> Unit) {
    val current = ScheduleMath.current(state.activities, now)
    val next = ScheduleMath.upcoming(state.activities.filter { it.id != current?.id }, now)
    if (current == null && next == null) return
    val (activity, line) = if (current != null) {
        val left = current.endMinute - ScheduleMath.minuteOf(now.toLocalTime())
        current to "Now · ${ScheduleMath.formatDuration(left)} left"
    } else {
        val (a, at) = next!!
        val mins = Duration.between(now, at).toMinutes().toInt().coerceAtLeast(1)
        a to "Up next · in ${if (mins >= 24 * 60) "${mins / (24 * 60)}d" else ScheduleMath.formatDuration(mins)}"
    }
    val (bg, accent) = blockColor(activity.color)
    val ws = state.workspace(activity.id)
    val streak = ScheduleMath.streak(activity.days, ws.checkIns, now.toLocalDate())
    Card(
        onClick = { onOpen(activity.id) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(activity.category.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(line, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(activity.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val task = (ws.tasks.firstOrNull { it.date == now.toLocalDate().toString() && !it.done }
                    ?: ws.tasks.firstOrNull { !it.done })?.text
                Text(
                    task ?: "${ScheduleMath.formatRange(activity, state.settings.use24h)} · tap for your coach",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (streak > 0) Text("🔥$streak", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyHint() {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Welcome! 👋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap any empty slot to add an activity. Every block repeats weekly, rings when it starts, " +
                    "and has its own AI coach for your goals.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Repository.loadSampleWeek() }) { Text("Load a sample week") }
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    state: AppState,
    now: LocalDateTime,
    hourHeight: Dp,
    onEmptySlot: (day: Int, minute: Int) -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (Activity) -> Unit,
) {
    val settings = state.settings
    // Make sure every activity is visible even if it sits outside the chosen hour range.
    val firstHour = minOf(settings.firstHour, state.activities.minOfOrNull { it.startMinute / 60 } ?: 24)
    val lastHour = maxOf(settings.lastHour, state.activities.maxOfOrNull { (it.endMinute + 59) / 60 } ?: 0)
        .coerceIn(firstHour + 1, 24)
    val hours = firstHour until lastHour
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val today = now.dayOfWeek.value
    val density = LocalDensity.current

    // Open on the current hour, the way a paper timetable is read.
    LaunchedEffect(hourHeight) {
        val target = (now.hour - firstHour - 1).coerceAtLeast(0)
        vScroll.scrollTo(with(density) { (hourHeight * target).roundToPx() })
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
        // Like a paper timetable, the whole week fits on screen; very narrow screens scroll sideways.
        val dayWidth = maxOf(46.dp, (maxWidth - HourColumnWidth) / 7)
        val compact = dayWidth < 84.dp
        val gridHeight = hourHeight * hours.count()
        val line = MaterialTheme.colorScheme.outlineVariant

        Column(Modifier.fillMaxSize()) {
            // ---- Header row: days of the week across the top ------------------------------
            Row(Modifier.height(HeaderHeight)) {
                Box(Modifier.width(HourColumnWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("🕒", fontSize = 18.sp)
                }
                Row(Modifier.horizontalScroll(hScroll)) {
                    for (d in 1..7) {
                        val isToday = d == today
                        Box(
                            Modifier.width(dayWidth).fillMaxHeight().padding(3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ScheduleMath.dayShort[d - 1].uppercase().let { if (compact) it.take(2) else it },
                                fontWeight = FontWeight.Bold,
                                letterSpacing = if (compact) 0.sp else 1.sp,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(Modifier.weight(1f)) {
                // ---- Hours down the side ----------------------------------------------------
                Column(Modifier.width(HourColumnWidth).verticalScroll(vScroll)) {
                    for (h in hours) {
                        Box(Modifier.height(hourHeight).fillMaxWidth()) {
                            Text(
                                ScheduleMath.formatTime(h * 60, settings.use24h).replace(":00 ", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (h == now.hour) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (h == now.hour) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(96.dp))
                }

                // ---- The grid itself ----------------------------------------------------------
                Box(Modifier.weight(1f).verticalScroll(vScroll).horizontalScroll(hScroll)) {
                    Box(
                        Modifier.width(dayWidth * 7).height(gridHeight + 96.dp)
                            .pointerInput(dayWidth, hourHeight, firstHour) {
                                detectTapGestures { pos ->
                                    val day = (pos.x / dayWidth.toPx()).toInt() + 1
                                    val hourIndex = (pos.y / hourHeight.toPx())
                                    val minute = firstHour * 60 + (hourIndex * 2).toInt() * 30
                                    if (day in 1..7 && minute < lastHour * 60) onEmptySlot(day, minute)
                                }
                            }
                    ) {
                        // Today's column tint
                        Box(
                            Modifier.offset(x = dayWidth * (today - 1)).width(dayWidth).height(gridHeight)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        )
                        // Hour lines and day separators
                        for (i in 0..hours.count()) {
                            Box(Modifier.offset(y = hourHeight * i).fillMaxWidth().height(1.dp).background(line))
                        }
                        for (d in 0..7) {
                            Box(Modifier.offset(x = dayWidth * d).width(1.dp).height(gridHeight).background(line))
                        }
                        // Activity blocks
                        for (a in state.activities) for (d in a.days) {
                            ActivityBlock(
                                activity = a,
                                streak = ScheduleMath.streak(a.days, state.workspace(a.id).checkIns, now.toLocalDate()),
                                hasGoal = state.workspace(a.id).goal != null,
                                use24h = settings.use24h,
                                compact = compact,
                                modifier = Modifier
                                    .offset(
                                        x = dayWidth * (d - 1) + if (compact) 1.dp else 2.dp,
                                        y = hourHeight * ((a.startMinute - firstHour * 60) / 60f) + 1.dp,
                                    )
                                    .width(dayWidth - if (compact) 2.dp else 4.dp)
                                    .height((hourHeight * (a.durationMinutes / 60f) - 2.dp).coerceAtLeast(18.dp)),
                                onClick = { onOpen(a.id) },
                                onLongClick = { onEdit(a) },
                            )
                        }
                        // The red "now" line across today's column
                        val nowMinute = ScheduleMath.minuteOf(now.toLocalTime())
                        if (nowMinute in firstHour * 60 until lastHour * 60) {
                            val y = hourHeight * ((nowMinute - firstHour * 60) / 60f)
                            Box(
                                Modifier.offset(x = dayWidth * (today - 1), y = y - 1.dp).width(dayWidth).height(2.dp)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            Box(
                                Modifier.offset(x = dayWidth * (today - 1) - 4.dp, y = y - 5.dp).size(10.dp)
                                    .clip(CircleShape).background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityBlock(
    activity: Activity,
    streak: Int,
    hasGoal: Boolean,
    use24h: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (bg, accent) = blockColor(activity.color)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Accent stripe on the left edge, like a highlighter mark on paper
        Box(Modifier.width(if (compact) 3.dp else 4.dp).fillMaxHeight().background(accent))
        if (compact) {
            // Narrow column: emoji, then the name, then the start time — like handwriting in a small box.
            Column(Modifier.padding(start = 5.dp, end = 2.dp, top = 2.dp)) {
                if (activity.durationMinutes >= 45) Text(activity.category.emoji, fontSize = 13.sp)
                Text(
                    activity.title,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = when {
                        activity.durationMinutes >= 120 -> 3
                        activity.durationMinutes >= 60 -> 2
                        else -> 1
                    },
                    overflow = TextOverflow.Ellipsis,
                )
                if (activity.durationMinutes >= 90) Text(
                    ScheduleMath.formatTime(activity.startMinute, use24h),
                    fontSize = 9.sp, maxLines = 1,
                )
                if (activity.durationMinutes >= 120 && streak > 0) Text("🔥$streak", fontSize = 9.sp)
            }
            return@Box
        }
        Column(Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 2.dp)) {
            Text(
                "${activity.category.emoji} ${activity.title}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = if (activity.durationMinutes >= 60) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (activity.durationMinutes >= 45) {
                Text(
                    "${ScheduleMath.formatRange(activity, use24h)} · ${ScheduleMath.formatDuration(activity.durationMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(activity.durationMinutes >= 75 && (streak > 0 || hasGoal || activity.reminder)) {
                Text(
                    buildString {
                        if (activity.reminder) append("⏰ ")
                        if (hasGoal) append("🎯 ")
                        if (streak > 0) append("🔥$streak")
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
}
