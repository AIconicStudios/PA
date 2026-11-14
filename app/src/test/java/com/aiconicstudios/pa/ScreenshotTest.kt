package com.aiconicstudios.pa

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.AppState
import com.aiconicstudios.pa.data.Category
import com.aiconicstudios.pa.data.ChatMsg
import com.aiconicstudios.pa.data.Goal
import com.aiconicstudios.pa.data.ScheduleMath
import com.aiconicstudios.pa.data.Settings
import com.aiconicstudios.pa.data.Workspace
import com.aiconicstudios.pa.ui.PaTheme
import com.aiconicstudios.pa.ui.TimetableScreen
import com.aiconicstudios.pa.ui.WorkspaceScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Renders real screens to PNG files under docs/screenshots (sample data, no network calls).
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val exercise = Activity(id = "ex", title = "Exercise", category = Category.EXERCISE,
        days = setOf(1, 3, 5), startMinute = 18 * 60, durationMinutes = 60, leadMinutes = 10)
    private val activities = listOf(
        Activity(id = "st", title = "Studies", category = Category.STUDIES, days = weekdays,
            startMinute = 5 * 60 + 30, durationMinutes = 120),
        Activity(id = "wk", title = "Work", category = Category.WORK, days = weekdays,
            startMinute = 9 * 60, durationMinutes = 8 * 60, reminder = false),
        exercise,
        Activity(id = "rd", title = "Reading", category = Category.READING, days = (1..7).toSet(),
            startMinute = 21 * 60, durationMinutes = 30),
        Activity(id = "lr", title = "Long run", category = Category.EXERCISE, days = setOf(6),
            startMinute = 7 * 60, durationMinutes = 90),
        Activity(id = "mp", title = "Meal prep", category = Category.MEALS, days = setOf(7),
            startMinute = 16 * 60, durationMinutes = 90),
        Activity(id = "md", title = "Meditate", category = Category.MINDFUL, days = setOf(6, 7),
            startMinute = 10 * 60, durationMinutes = 30),
    )

    private val today = LocalDate.now()
    private val sampleReply = """
        A flexible waist in a month is realistic with 3 focused sessions a week — we'll build range gradually.

        ## This week
        - [ ] Mon: 10 min warm-up, 3×12 standing side bends, 2×30 s cat-cow
        - [ ] Wed: hip circles 2×10 each way, seated twists 3×8, child's pose 1 min
        - [ ] Fri: repeat Monday, add 2×20 s side planks

        ## Week 2–3: build range
        - Hold stretches **45 s** instead of 30 s
        - Add standing windmills and thread-the-needle

        ## Week 4: test it
        - Compare your side-bend reach to day 1

        Tip: lay your mat out before 6 pm so starting takes zero effort.
    """.trimIndent()

    private val workspace = Workspace(
        goal = Goal("Flexible waist", 30, today.minusDays(5).toString()),
        plan = sampleReply,
        tasks = ScheduleMath.parseChecklist(sampleReply).mapIndexed { i, t -> t.copy(done = i == 0) },
        chat = listOf(
            ChatMsg(true, "My goal: Flexible waist. Time frame: 1 month. Please build my plan."),
            ChatMsg(false, sampleReply),
        ),
        // The last three Mon/Wed/Fri sessions before today
        checkIns = generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .filter { it.dayOfWeek.value in exercise.days }.take(3).map { it.toString() }.toList(),
    )

    @Test fun timetable() = shoot("timetable") {
        TimetableScreen(
            state = AppState(activities = activities, workspaces = mapOf("ex" to workspace), settings = Settings(firstHour = 5, lastHour = 23, hourHeightDp = 56)),
            onOpenWorkspace = {}, onOpenSettings = {},
        )
    }

    @Test fun timetableEmpty() = shoot("timetable_empty") {
        TimetableScreen(state = AppState(), onOpenWorkspace = {}, onOpenSettings = {})
    }

    @Test fun workspace() = shoot("workspace") {
        WorkspaceScreen(
            activity = exercise, workspace = workspace, settings = Settings(apiKey = "x"),
            allActivities = activities, onBack = {}, onOpenSettings = {},
        )
    }

    @Test fun workspaceNewGoal() = shoot("workspace_new_goal") {
        WorkspaceScreen(
            activity = exercise, workspace = Workspace(), settings = Settings(apiKey = "x"),
            allActivities = activities, onBack = {}, onOpenSettings = {},
        )
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        // The timetable has a ticking clock, so drive time manually instead of waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.setContent { PaTheme { content() } }
        compose.mainClock.advanceTimeBy(3_000)
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
