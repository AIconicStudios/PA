package com.aiconicstudios.pa.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiconicstudios.pa.alarm.AlarmScheduler
import com.aiconicstudios.pa.data.Persona
import com.aiconicstudios.pa.data.Repository
import com.aiconicstudios.pa.data.ScheduleMath
import com.aiconicstudios.pa.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    var hours by remember { mutableStateOf(settings.firstHour.toFloat()..settings.lastHour.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("🧑‍🏫 Coaches") {
                Text(
                    "Every activity has a built-in coach that plans your sessions. " +
                        "It works offline and is completely free — no account or internet needed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Coach personality", style = MaterialTheme.typography.labelLarge)
                Persona.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Repository.updateSettings { it.copy(persona = p) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.persona == p, onClick = { Repository.updateSettings { it.copy(persona = p) } })
                        Column {
                            Text("${p.emoji} ${p.label}", fontWeight = FontWeight.SemiBold)
                            Text(p.voice, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Section("🗓️ Timetable") {
                Text(
                    "Visible hours: ${ScheduleMath.formatTime(hours.start.toInt() * 60, settings.use24h)} – " +
                        ScheduleMath.formatTime(hours.endInclusive.toInt() * 60, settings.use24h),
                )
                RangeSlider(
                    value = hours,
                    onValueChange = { if (it.endInclusive - it.start >= 1f) hours = it },
                    onValueChangeFinished = {
                        Repository.updateSettings { s -> s.copy(firstHour = hours.start.toInt(), lastHour = hours.endInclusive.toInt()) }
                    },
                    valueRange = 0f..24f,
                    steps = 23,
                )
                ToggleRow("24-hour clock", settings.use24h) { v -> Repository.updateSettings { it.copy(use24h = v) } }
            }

            Section("⏰ Alarms") {
                ToggleRow("Keep ringing until I respond", settings.ringUntilDismissed) { v ->
                    Repository.updateSettings { it.copy(ringUntilDismissed = v) }
                }
                if (!AlarmScheduler.canRingExactly(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text("Exact alarms are off, so reminders may arrive a few minutes late.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Allow exact alarms") }
                }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, AlarmScheduler.CHANNEL_ID)
                    )
                }) { Text("Alarm sound & notification settings") }
                OutlinedButton(onClick = {
                    AlarmScheduler.snooze(context, AlarmScheduler.TEST_ID, minutes = 0)
                }) { Text("🔔 Ring a test alarm") }
            }

            Section("ℹ️ About") {
                Text("PA — your timetable, alarms and goal coaches in one place. Version 1.1 · works fully offline",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
