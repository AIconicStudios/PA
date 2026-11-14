package com.aiconicstudios.pa.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconicstudios.pa.ai.Coach
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.ChatMsg
import com.aiconicstudios.pa.data.Goal
import com.aiconicstudios.pa.data.PlanTask
import com.aiconicstudios.pa.data.Repository
import com.aiconicstudios.pa.data.ScheduleMath
import com.aiconicstudios.pa.data.Settings
import com.aiconicstudios.pa.data.Workspace
import kotlinx.coroutines.launch
import java.time.LocalDate

private val levelTitles = listOf("Rookie", "Regular", "Committed", "Pro", "Expert", "Master", "Legend")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    activity: Activity,
    workspace: Workspace,
    settings: Settings,
    allActivities: List<Activity>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val (bg, accent) = blockColor(activity.color)
    val coachName = activity.category.coachName

    fun ask(message: String) {
        if (settings.apiKey.isBlank()) {
            error = "Add your Claude API key in Settings to wake up $coachName."
            return
        }
        val userMsg = ChatMsg(fromUser = true, text = message)
        Repository.updateWorkspace(activity.id) { it.copy(chat = it.chat + userMsg) }
        thinking = true
        error = null
        scope.launch {
            val latest = Repository.state.value.workspace(activity.id)
            val result = Coach(settings.apiKey).reply(
                activity = activity,
                workspace = latest,
                persona = settings.persona,
                use24h = settings.use24h,
                conversation = latest.chat.takeLast(30),
            )
            thinking = false
            when (result) {
                is Coach.Result.Failure -> error = result.message
                is Coach.Result.Reply -> Repository.updateWorkspace(activity.id) { ws ->
                    val newTasks = ScheduleMath.parseChecklist(result.text)
                    val doneTexts = ws.tasks.filter { it.done }.map { it.text }.toSet()
                    ws.copy(
                        chat = ws.chat + ChatMsg(fromUser = false, text = result.text),
                        plan = if (newTasks.isNotEmpty() || ws.plan == null) result.text else ws.plan,
                        tasks = if (newTasks.isNotEmpty())
                            newTasks.map { it.copy(done = it.done || it.text in doneTexts) }
                        else ws.tasks,
                    )
                }
            }
        }
    }

    LaunchedEffect(workspace.chat.size, thinking) {
        if (workspace.chat.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("${activity.category.emoji} ${activity.title}", fontWeight = FontWeight.Bold)
                        Text(
                            "${ScheduleMath.daysLabel(activity.days)} · ${ScheduleMath.formatRange(activity, settings.use24h)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit activity") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (workspace.goal != null) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Ask $coachName… (e.g. I missed Monday)") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { ask(input.trim()); input = "" },
                            enabled = input.isNotBlank() && !thinking,
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CoachHeader(activity, workspace, today, accent) }

            if (settings.apiKey.isBlank()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🔑 Connect your coach", fontWeight = FontWeight.Bold)
                        Text("$coachName runs on Claude. Add your Claude API key once in Settings and every activity's coach comes alive.")
                        TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }

            val goal = workspace.goal
            if (goal == null) {
                item { GoalForm(activity, enabled = !thinking) { text, days ->
                    Repository.updateWorkspace(activity.id) {
                        it.copy(goal = Goal(text, days, today.toString()), plan = null, tasks = emptyList(), chat = emptyList())
                    }
                    ask(Coach.planRequest(text, days))
                } }
            } else {
                item {
                    GoalCard(goal, workspace, today, accent,
                        onCheckIn = {
                            val key = today.toString()
                            val already = key in workspace.checkIns
                            Repository.updateWorkspace(activity.id) {
                                it.copy(checkIns = if (already) it.checkIns - key else it.checkIns + key)
                            }
                            if (!already) scope.launch {
                                val s = ScheduleMath.streak(activity.days, workspace.checkIns + key, today)
                                snackbar.showSnackbar(cheer(s))
                            }
                        },
                        onChangeGoal = { confirmReset = true },
                    )
                }
                if (workspace.tasks.isNotEmpty()) item { ChecklistCard(activity.id, workspace.tasks, accent) }
            }

            items(workspace.chat) { msg -> ChatBubble(msg, coachEmoji = activity.category.emoji) }

            if (thinking) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("$coachName is thinking…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let { e ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e, Modifier.weight(1f))
                            val lastUser = workspace.chat.lastOrNull()?.takeIf { it.fromUser }
                            if (lastUser != null && settings.apiKey.isNotBlank()) TextButton(onClick = {
                                Repository.updateWorkspace(activity.id) { it.copy(chat = it.chat.dropLast(1)) }
                                ask(lastUser.text)
                            }) { Text("Retry") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (editing) {
        ActivityEditor(
            initial = activity, defaultDay = activity.days.min(), defaultStart = activity.startMinute,
            allActivities = allActivities, use24h = settings.use24h,
            onDismiss = { editing = false },
            onSave = { Repository.upsertActivity(it); editing = false },
            onDelete = { editing = false; onBack(); Repository.deleteActivity(it.id) },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Set a new goal?") },
            text = { Text("Your current plan, checklist and chat will be cleared. Your check-ins and streak stay.") },
            confirmButton = {
                TextButton(onClick = {
                    Repository.updateWorkspace(activity.id) { it.copy(goal = null, plan = null, tasks = emptyList(), chat = emptyList()) }
                    confirmReset = false
                }) { Text("New goal") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun cheer(streak: Int) = when {
    streak >= 30 -> "🏆 $streak in a row. Legendary."
    streak >= 10 -> "🚀 $streak-session streak! Unstoppable."
    streak >= 5 -> "🔥 $streak in a row — you're on fire!"
    streak >= 2 -> "💪 $streak-session streak. Keep it going!"
    else -> "✅ Session logged. Great start!"
}

@Composable
private fun CoachHeader(activity: Activity, ws: Workspace, today: LocalDate, accent: androidx.compose.ui.graphics.Color) {
    val streak = ScheduleMath.streak(activity.days, ws.checkIns, today)
    val xp = ws.checkIns.size * 10 + ws.tasks.count { it.done } * 5
    val level = xp / 50
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text(activity.category.emoji, fontSize = 28.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.category.coachName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Your ${activity.category.coachRole}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill("🔥", "$streak", "streak")
        StatPill("✅", "${ws.checkIns.size}", "sessions")
        StatPill("⭐", "Lv ${level + 1}", levelTitles[level.coerceAtMost(levelTitles.lastIndex)])
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { (xp % 50) / 50f },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = accent,
    )
    Text("${50 - xp % 50} XP to next level · check-ins +10, tasks +5",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatPill(emoji: String, value: String, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$emoji $value", fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalForm(activity: Activity, enabled: Boolean, onSubmit: (String, Int) -> Unit) {
    var goal by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(30) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎯 What do you want to achieve?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${activity.category.coachName} will turn your ${ScheduleMath.formatDuration(activity.durationMinutes)} " +
                    "${ScheduleMath.daysLabel(activity.days).lowercase()} slot into a step-by-step plan.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = goal, onValueChange = { goal = it },
                label = { Text("Your goal") },
                placeholder = { Text(activity.category.goalIdeas.first()) },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                activity.category.goalIdeas.forEach { idea ->
                    AssistChip(onClick = { goal = idea }, label = { Text(idea) })
                }
            }
            Text("Achieve it in", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 14, 30, 60, 90).forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(Coach.durationLabel(d)) })
                }
            }
            Button(
                onClick = { onSubmit(goal.trim(), days) },
                enabled = enabled && goal.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("✨ Build my plan") }
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    ws: Workspace,
    today: LocalDate,
    accent: androidx.compose.ui.graphics.Color,
    onCheckIn: () -> Unit,
    onChangeGoal: () -> Unit,
) {
    val start = LocalDate.parse(goal.startDate)
    val day = (today.toEpochDay() - start.toEpochDay() + 1).toInt()
    val finished = day > goal.durationDays
    val checkedToday = today.toString() in ws.checkIns
    val tasksDone = ws.tasks.count { it.done }
    Card(Modifier.animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎯 ${goal.text}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (finished) "Time frame complete 🎉 — set a new goal or ask your coach to extend."
                else "Day ${day.coerceAtLeast(1)} of ${goal.durationDays} · ${goal.durationDays - day} days to go",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { (day.toFloat() / goal.durationDays).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = accent,
            )
            if (ws.tasks.isNotEmpty()) Text("Checklist: $tasksDone/${ws.tasks.size} done", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCheckIn) {
                    Text(if (checkedToday) "✅ Done today (undo)" else "I did today's session")
                }
                TextButton(onClick = onChangeGoal) { Text("New goal") }
            }
        }
    }
}

@Composable
private fun ChecklistCard(activityId: String, tasks: List<PlanTask>, accent: androidx.compose.ui.graphics.Color) {
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("This week's checklist", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            tasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Repository.updateWorkspace(activityId) { ws ->
                            ws.copy(tasks = ws.tasks.map { if (it.id == task.id) it.copy(done = !it.done) else it })
                        }
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.done, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(
                        task.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg, coachEmoji: String) {
    val mine = msg.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (!mine) {
            Text(coachEmoji, fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp, top = 4.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (mine) Text(msg.text, color = MaterialTheme.colorScheme.onPrimary)
                else MarkdownLite(msg.text)
            }
        }
    }
}

/** Just enough markdown for a coach's plan: headings, bullets, checkboxes and **bold**. */
@Composable
private fun MarkdownLite(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                trimmed.startsWith("#") -> Text(
                    inlineBold(trimmed.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Regex("""^[-*•]\s*\[[ xX]]\s*""").containsMatchIn(trimmed) -> {
                    val done = trimmed.contains("[x]", ignoreCase = true)
                    Text(inlineBold((if (done) "☑ " else "☐ ") + trimmed.replace(Regex("""^[-*•]\s*\[[ xX]]\s*"""), "")),
                        style = MaterialTheme.typography.bodyMedium)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> Text(
                    inlineBold("•  " + trimmed.drop(2)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = if (line.length - trimmed.length >= 2) 14.dp else 0.dp),
                )
                else -> Text(inlineBold(trimmed), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun inlineBold(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1 && i != parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        else append(part)
    }
}
