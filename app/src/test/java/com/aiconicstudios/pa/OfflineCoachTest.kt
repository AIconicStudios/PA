package com.aiconicstudios.pa

import com.aiconicstudios.pa.coach.OfflineCoach
import com.aiconicstudios.pa.coach.Programs
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.Category
import com.aiconicstudios.pa.data.Goal
import com.aiconicstudios.pa.data.Persona
import com.aiconicstudios.pa.data.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OfflineCoachTest {

    private val monday = LocalDate.of(2026, 11, 16)
    private val exercise = Activity(id = "ex", title = "Exercise", category = Category.EXERCISE,
        days = setOf(1, 3, 5), startMinute = 18 * 60, durationMinutes = 60)

    @Test fun picksProgramFromGoal() {
        assertEquals("mobility", Programs.forGoal(Category.EXERCISE, "Flexible waist").id)
        assertEquals("running", Programs.forGoal(Category.EXERCISE, "Run 5 km without stopping").id)
        assertEquals("strength", Programs.forGoal(Category.EXERCISE, "Do 20 clean push-ups").id)
        assertEquals("fitness", Programs.forGoal(Category.EXERCISE, "Lose 5 kg").id)
        assertEquals("fitness", Programs.forGoal(Category.EXERCISE, "Feel better").id)
        assertEquals("studies", Programs.forGoal(Category.STUDIES, "Score an A in maths").id)
        // "score" must not trigger the "core" strength keyword
        assertEquals("studies", Programs.forGoal(Category.CUSTOM, "Score an A in my exam").id)
        assertEquals("habit", Programs.forGoal(Category.CUSTOM, "Drink more water").id)
    }

    @Test fun oneTaskPerScheduledSessionInFirstWeek() {
        val goal = Goal("Flexible waist", 30, monday.toString())
        val r = OfflineCoach.startGoal(exercise, Workspace(), goal, Persona.MENTOR, monday)
        val tasks = r.workspace.tasks
        assertEquals(3, tasks.size) // Mon, Wed, Fri
        assertEquals(listOf("2026-11-16", "2026-11-18", "2026-11-20"), tasks.map { it.date })
        assertTrue(tasks[0].text, tasks[0].text.contains("warm-up"))
        assertTrue(r.text.contains("checklist") && r.text.contains("## Roadmap"))
        assertEquals(0, r.workspace.planWeek)
    }

    @Test fun lastSessionIsTestDay() {
        val goal = Goal("Flexible waist", 28, monday.toString())
        val lastWeek = OfflineCoach.weekTasks(exercise, goal, 0, 3)
        assertTrue(lastWeek.last().text.contains("Test day"))
    }

    @Test fun tooEasyMakesItHarder() {
        val goal = Goal("Do 20 push-ups", 30, monday.toString())
        val start = OfflineCoach.startGoal(exercise, Workspace(), goal, Persona.SERGEANT, monday).workspace
        val harder = OfflineCoach.respond(exercise, start, Persona.SERGEANT, "Too easy", monday).workspace
        assertEquals(1, harder.intensity)
        val easier = OfflineCoach.respond(exercise, start, Persona.SERGEANT, "Too hard", monday).workspace
        assertEquals(-1, easier.intensity)
        // Same moves, bigger numbers
        val firstNumbers = Regex("""\d+×(\d+)""").findAll(start.tasks[0].text).map { it.groupValues[1].toInt() }.toList()
        val harderNumbers = Regex("""\d+×(\d+)""").findAll(harder.tasks[0].text).map { it.groupValues[1].toInt() }.toList()
        assertTrue(harderNumbers.sum() > firstNumbers.sum())
    }

    @Test fun newWeekRefreshesChecklist() {
        val goal = Goal("Run 5 km", 30, monday.toString())
        val ws = OfflineCoach.startGoal(exercise, Workspace(), goal, Persona.HYPE, monday).workspace
        assertEquals(null, OfflineCoach.refreshWeek(exercise, ws, Persona.HYPE, monday.plusDays(3)))
        val next = OfflineCoach.refreshWeek(exercise, ws, Persona.HYPE, monday.plusDays(7))!!
        assertEquals(1, next.workspace.planWeek)
        assertEquals("2026-11-23", next.workspace.tasks.first().date)
    }

    @Test fun everyCategoryProducesSessions() {
        Category.entries.forEach { c ->
            val a = exercise.copy(category = c, durationMinutes = 45)
            val goal = Goal(c.goalIdeas.first(), 60, monday.toString())
            for (week in 0 until 9) {
                OfflineCoach.weekTasks(a, goal, 0, week).forEach {
                    assertTrue("$c: ${it.text}", it.text.length > 15 && "{" !in it.text)
                }
            }
        }
    }
}
