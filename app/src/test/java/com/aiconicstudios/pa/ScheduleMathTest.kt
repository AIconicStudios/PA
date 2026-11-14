package com.aiconicstudios.pa

import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.ScheduleMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleMathTest {

    // 2026-11-16 is a Monday.
    private val monday = LocalDate.of(2026, 11, 16)
    private val studies = Activity(title = "Studies", days = setOf(1), startMinute = 5 * 60 + 30, durationMinutes = 120)

    @Test fun nextTriggerLaterSameDay() {
        val t = ScheduleMath.nextTrigger(studies, monday.atTime(4, 0))
        assertEquals(monday.atTime(5, 30), t)
    }

    @Test fun nextTriggerRollsToNextWeekAfterStart() {
        val t = ScheduleMath.nextTrigger(studies, monday.atTime(5, 30))
        assertEquals(monday.plusWeeks(1).atTime(5, 30), t)
    }

    @Test fun nextTriggerPicksEarliestOfSeveralDays() {
        val a = studies.copy(days = setOf(1, 3, 5))
        assertEquals(monday.plusDays(2).atTime(5, 30), ScheduleMath.nextTrigger(a, monday.atTime(9, 0)))
        assertEquals(monday.plusDays(7).atTime(5, 30), ScheduleMath.nextTrigger(a, monday.plusDays(4).atTime(9, 0)))
    }

    @Test fun leadTimeCanCrossMidnight() {
        val midnight = Activity(title = "x", days = setOf(2), startMinute = 0, durationMinutes = 30, leadMinutes = 10)
        // Tuesday 00:00 start, 10 min early → Monday 23:50
        assertEquals(monday.atTime(23, 50), ScheduleMath.nextTrigger(midnight, monday.atTime(12, 0)))
    }

    @Test fun noDaysMeansNoTrigger() {
        assertNull(ScheduleMath.nextTrigger(studies.copy(days = emptySet()), LocalDateTime.now()))
    }

    @Test fun overlapDetection() {
        val b = Activity(title = "b", days = setOf(1, 2), startMinute = 7 * 60, durationMinutes = 60)
        val c = Activity(title = "c", days = setOf(1), startMinute = 7 * 60 + 30, durationMinutes = 30)
        assertTrue(ScheduleMath.overlaps(studies, b))          // 5:30–7:30 vs 7:00–8:00 on Monday
        assertFalse(ScheduleMath.overlaps(studies, c))         // 7:30 start touches but doesn't overlap
        assertFalse(ScheduleMath.overlaps(studies, b.copy(days = setOf(2))))
    }

    @Test fun formatsTimes() {
        assertEquals("05:30", ScheduleMath.formatTime(330, true))
        assertEquals("5:30 am", ScheduleMath.formatTime(330, false))
        assertEquals("12:00 pm", ScheduleMath.formatTime(720, false))
        assertEquals("2h", ScheduleMath.formatDuration(120))
        assertEquals("1h 30m", ScheduleMath.formatDuration(90))
        assertEquals("05:30–07:30", ScheduleMath.formatRange(studies, true))
    }

    @Test fun streakCountsScheduledDaysOnly() {
        val mwf = setOf(1, 3, 5)
        val fri = monday.plusDays(4)
        val checkIns = listOf(monday, monday.plusDays(2), fri).map { it.toString() }
        assertEquals(3, ScheduleMath.streak(mwf, checkIns, fri))
        // Saturday isn't scheduled → streak still 3
        assertEquals(3, ScheduleMath.streak(mwf, checkIns, fri.plusDays(1)))
        // Next Monday not yet done today → doesn't break
        assertEquals(3, ScheduleMath.streak(mwf, checkIns, monday.plusWeeks(1)))
        // Missed next Monday, now Tuesday → broken
        assertEquals(0, ScheduleMath.streak(mwf, checkIns, monday.plusWeeks(1).plusDays(1)))
    }

    @Test fun parsesChecklistFromCoachReply() {
        val reply = """
            Here's your plan.
            ## This week
            - [ ] Mon: **10 min** warm-up, 3×12 side bends
            - [x] Wed: cat-cow 2×30 s
            * [ ] Fri: hip circles
            - not a task
        """.trimIndent()
        val tasks = ScheduleMath.parseChecklist(reply)
        assertEquals(3, tasks.size)
        assertEquals("Mon: 10 min warm-up, 3×12 side bends", tasks[0].text)
        assertTrue(tasks[1].done)
    }
}
