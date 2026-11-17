package com.aiconicstudios.pa.coach

import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.Goal
import com.aiconicstudios.pa.data.Persona
import com.aiconicstudios.pa.data.PlanTask
import com.aiconicstudios.pa.data.ScheduleMath
import com.aiconicstudios.pa.data.Workspace
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The built-in coach. It builds a plan from [Programs], sized to the activity's time slot,
 * and adjusts it from simple feedback. Fully offline and free.
 */
object OfflineCoach {

    data class Reply(val text: String, val workspace: Workspace)

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun durationLabel(days: Int): String = when {
        days % 30 == 0 && days >= 30 -> if (days == 30) "1 month" else "${days / 30} months"
        days % 7 == 0 -> if (days == 7) "1 week" else "${days / 7} weeks"
        else -> "$days days"
    }

    fun totalWeeks(goal: Goal) = ceil(goal.durationDays / 7.0).toInt().coerceAtLeast(1)

    fun weekIndex(goal: Goal, today: LocalDate): Int {
        val start = LocalDate.parse(goal.startDate)
        return ((today.toEpochDay() - start.toEpochDay()) / 7).toInt().coerceIn(0, totalWeeks(goal) - 1)
    }

    /** Starts a fresh goal: builds the plan message and this week's checklist. */
    fun startGoal(activity: Activity, ws: Workspace, goal: Goal, persona: Persona, today: LocalDate): Reply {
        val program = Programs.forGoal(activity.category, goal.text)
        val base = ws.copy(goal = goal, intensity = 0, chat = emptyList(), plan = null)
        val week = weekIndex(goal, today)
        val tasks = weekTasks(activity, goal, 0, week)
        val weeks = totalWeeks(goal)
        val sessions = countSessions(activity, goal)
        val text = buildString {
            append(pick(persona, opening)).append("\n\n")
            append("**Goal:** ${goal.text} in ${durationLabel(goal.durationDays)}.\n")
            append("**How:** ${program.approach}.\n")
            append("**Your slot:** ${ScheduleMath.daysLabel(activity.days)}, ${ScheduleMath.formatDuration(activity.durationMinutes)} each — that's $sessions sessions in total.\n")
            if (program.physical && goal.durationDays < 14)
                append("\nTwo weeks is short for a body change, so we'll aim for a strong, safe start.\n")
            append("\n✅ Your checklist for this week is ready above: ${tasks.size} session(s).\n")
            append("\n## Roadmap\n")
            phaseWeeks(program, weeks).forEach { (phase, range) ->
                append("- **${weekRangeLabel(range)} · ${phase.name}:** ${phase.focus}\n")
            }
            if (weeks >= 2) append("- **Last session:** test day — try your goal and note the result 📏\n")
            append("\n💡 ").append(program.tips.first())
            if (program.physical) append("\n\nStop any move that hurts (not the same as hard) and get advice if pain continues.")
        }
        return Reply(text, base.copy(tasks = tasks, planWeek = week, plan = text))
    }

    /** Called when a new week starts: a fresh checklist for that week. */
    fun refreshWeek(activity: Activity, ws: Workspace, persona: Persona, today: LocalDate): Reply? {
        val goal = ws.goal ?: return null
        val week = weekIndex(goal, today)
        if (week == ws.planWeek) return null
        val program = Programs.forGoal(activity.category, goal.text)
        val tasks = weekTasks(activity, goal, ws.intensity, week)
        val phase = phaseFor(program, week, totalWeeks(goal))
        val text = buildString {
            append(pick(persona, newWeek).replace("{n}", "${week + 1}")).append("\n\n")
            append("**Phase: ${phase.name}** — ${phase.focus}.\n\n")
            append("✅ ${tasks.size} session(s) added to your checklist.\n")
            append("\n💡 ").append(program.tips[week % program.tips.size])
        }
        return Reply(text, ws.copy(tasks = tasks, planWeek = week))
    }

    /** Builds one checklist item per scheduled session in the given goal week. */
    fun weekTasks(activity: Activity, goal: Goal, intensity: Int, week: Int): List<PlanTask> {
        val program = Programs.forGoal(activity.category, goal.text)
        val weeks = totalWeeks(goal)
        val start = LocalDate.parse(goal.startDate)
        val end = start.plusDays(goal.durationDays.toLong() - 1)
        val allSessions = sessionDates(activity, start, end)
        val lastSession = allSessions.lastOrNull()
        val weekStart = start.plusWeeks(week.toLong())
        val phase = phaseFor(program, week, weeks)
        return allSessions
            .filter { !it.isBefore(weekStart) && it.isBefore(weekStart.plusWeeks(1)) }
            .map { date ->
                val index = allSessions.indexOf(date)
                val body = if (date == lastSession && weeks >= 2)
                    "Test day! Warm up, then try: ${goal.text}. Note your result 📏"
                else session(program, phase, activity.durationMinutes, index, week, intensity, goal.text)
                PlanTask(text = "${date.format(dayFmt)}: $body", date = date.toString())
            }
    }

    /** Reacts to a chat message using simple keyword rules. */
    fun respond(activity: Activity, ws: Workspace, persona: Persona, message: String, today: LocalDate): Reply {
        val goal = ws.goal
        val m = message.lowercase()
        fun has(vararg words: String) = words.any { it in m }
        if (goal == null) return Reply("Set a goal above first and I'll build your plan. 🎯", ws)
        val program = Programs.forGoal(activity.category, goal.text)
        val week = weekIndex(goal, today)

        return when {
            has("pain", "hurt", "injur", "ache") -> {
                val newWs = adjust(activity, ws, -1, week)
                Reply(
                    "Ouch — let's be careful. I've made this week easier. Skip any move that hurts, " +
                        "rest the area, and if pain lasts more than a few days see a doctor or physio. " +
                        "Hard is fine; painful is not.\n\n" + checklist(newWs.tasks), newWs
                )
            }
            has("too easy", "easy", "more", "harder", "bored", "level up") -> {
                if (ws.intensity >= 3) return Reply(pick(persona, maxedOut), ws)
                val newWs = adjust(activity, ws, +1, week)
                Reply(pick(persona, harder) + "\n\n" + checklist(newWs.tasks), newWs)
            }
            has("too hard", "hard", "tired", "difficult", "exhaust", "sore", "less", "easier") -> {
                if (ws.intensity <= -3) return Reply("We're at the gentlest level already. Even 10 minutes of it counts — show up and do what you can. 💙", ws)
                val newWs = adjust(activity, ws, -1, week)
                Reply(pick(persona, easier) + "\n\n" + checklist(newWs.tasks), newWs)
            }
            has("miss", "skip", "didn't", "didnt", "couldn't", "forgot", "behind") ->
                Reply(pick(persona, missed) + "\n\nNext up: " + (nextTask(ws, today) ?: "your next session in the timetable."), ws)
            has("today", "now", "what should", "next") -> {
                val t = todayTask(ws, today)
                Reply(if (t != null) "Today's session:\n" + t.text.substringAfter(": ").split(" · ").joinToString("\n") { "- $it" } else "No session today. Next up: ${nextTask(ws, today) ?: "check your timetable"}. Rest is part of the plan. 😌", ws)
            }
            has("progress", "how am i", "stats", "doing") -> Reply(progress(activity, ws, goal, today), ws)
            has("motivat", "lazy", "can't be bothered", "don't feel", "dont feel", "procrast", "tip") ->
                Reply(pick(persona, motivate) + "\n\n💡 " + program.tips.random(), ws)
            has("new plan", "reset", "restart", "regenerate") -> {
                val tasks = weekTasks(activity, goal, ws.intensity, week)
                Reply("Fresh checklist for this week:\n\n" + checklist(tasks), ws.copy(tasks = tasks, planWeek = week))
            }
            has("thank", "thx", "great", "done", "did it", "finished") -> Reply(pick(persona, thanks), ws)
            else -> Reply(
                "I'm your built-in coach, so I understand simple messages. Try one of the quick buttons below, or say things like " +
                    "“too easy”, “too hard”, “I missed a session”, “what's today?”, “motivate me” or “my progress”.",
                ws,
            )
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun adjust(activity: Activity, ws: Workspace, delta: Int, week: Int): Workspace {
        val goal = ws.goal ?: return ws
        val intensity = (ws.intensity + delta).coerceIn(-3, 3)
        val fresh = weekTasks(activity, goal, intensity, week)
        // Keep ticks on sessions that were already done.
        val doneDates = ws.tasks.filter { it.done }.mapNotNull { it.date }.toSet()
        return ws.copy(intensity = intensity, planWeek = week, tasks = fresh.map { it.copy(done = it.date in doneDates) })
    }

    private fun checklist(tasks: List<PlanTask>) =
        "✅ Checklist updated (${tasks.count { !it.done }} session(s) to go this week)."

    fun todayTask(ws: Workspace, today: LocalDate) = ws.tasks.firstOrNull { it.date == today.toString() }

    private fun nextTask(ws: Workspace, today: LocalDate) =
        ws.tasks.firstOrNull { !it.done && (it.date == null || it.date >= today.toString()) }?.text

    private fun progress(activity: Activity, ws: Workspace, goal: Goal, today: LocalDate): String {
        val start = LocalDate.parse(goal.startDate)
        val day = (today.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
        val planned = sessionDates(activity, start, minOf(today, start.plusDays(goal.durationDays.toLong() - 1))).size
        val done = ws.checkIns.count { it >= goal.startDate }
        val pct = if (planned == 0) 100 else (done * 100 / planned)
        val streak = ScheduleMath.streak(activity.days, ws.checkIns, today)
        val verdict = when {
            pct >= 90 -> "Outstanding consistency. 🏆"
            pct >= 70 -> "Solid — you're on track. 💪"
            pct >= 40 -> "Decent, but a couple more check-ins a week will make the difference."
            else -> "Let's rebuild momentum: just do the next session, nothing more."
        }
        return "📊 Day ${minOf(day, goal.durationDays)} of ${goal.durationDays}\n" +
            "- Sessions done: $done of $planned so far ($pct%)\n" +
            "- Current streak: 🔥 $streak\n" +
            "- Checklist: ${ws.tasks.count { it.done }}/${ws.tasks.size} this week\n\n$verdict"
    }

    private fun sessionDates(activity: Activity, from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek.value in activity.days }
            .toList()

    private fun countSessions(activity: Activity, goal: Goal): Int {
        val start = LocalDate.parse(goal.startDate)
        return sessionDates(activity, start, start.plusDays(goal.durationDays.toLong() - 1)).size
    }

    /** Which phase a week falls in, from the phases' shares of the total weeks. */
    fun phaseFor(program: Program, week: Int, weeks: Int): Phase =
        phaseWeeks(program, weeks).firstOrNull { week in it.second }?.first ?: program.phases.last()

    private fun phaseWeeks(program: Program, weeks: Int): List<Pair<Phase, IntRange>> {
        val phases = if (weeks < program.phases.size) program.phases.take(weeks) else program.phases
        var startWeek = 0
        var acc = 0.0
        val total = phases.sumOf { it.share }
        return phases.mapIndexed { i, p ->
            acc += p.share / total
            val endWeek = if (i == phases.lastIndex) weeks - 1
            else ((acc * weeks).roundToInt() - 1).coerceIn(startWeek, weeks - 1)
            (p to startWeek..endWeek).also { startWeek = endWeek + 1 }
        }.filter { !it.second.isEmpty() }
    }

    private fun weekRangeLabel(r: IntRange) =
        if (r.first == r.last) "Week ${r.first + 1}" else "Weeks ${r.first + 1}–${r.last + 1}"

    /** Writes one session, e.g. "5 min warm-up · Cat-cow 2×10 · Plank 2×30 s · 5 min cool-down". */
    fun session(program: Program, phase: Phase, minutes: Int, index: Int, week: Int, intensity: Int, goal: String): String {
        val warm = if (program.warmUp == null) 0 else if (minutes >= 60) 10 else if (minutes >= 20) 5 else 2
        val cool = if (program.coolDown == null) 0 else if (minutes >= 20) 5 else 2
        val main = (minutes - warm - cool).coerceAtLeast(5)
        val growth = (1 + 0.08 * week) * (1 + 0.15 * intensity)

        val parts = mutableListOf<String>()
        program.warmUp?.let { parts += it.replace("{w}", "$warm") }

        if (phase.moves.any { it.dose == Dose.ROUNDS }) {
            // Running: one structured block per session, rotating through the phase's options.
            val move = phase.moves[index % phase.moves.size]
            val block = minOf(main, phase.maxMainMinutes)
            parts += if (move.dose == Dose.ROUNDS) {
                val n = (block / move.amount).coerceAtLeast(2)
                "${move.name} ×${(n * (1 + 0.15 * intensity)).roundToInt().coerceAtLeast(2)}"
            } else move.name.replace("{min}", "${(block * (1 + 0.1 * intensity)).roundToInt()}")
        } else if (program.movesPer10Min > 0) {
            // Workouts: rotate a handful of moves from the phase so sessions vary.
            val count = (main / 10.0 * program.movesPer10Min).roundToInt().coerceIn(2, phase.moves.size)
            val chosen = (0 until count).map { phase.moves[(index * 2 + it) % phase.moves.size] }.distinct()
            val shareMoves = chosen.filter { it.dose == Dose.SHARE }
            val shareMinutes = (main - 6 * (chosen.size - shareMoves.size)).coerceAtLeast(5)
            chosen.forEach { mv ->
                parts += if (mv.dose == Dose.SHARE)
                    fill(mv.name, (shareMinutes / shareMoves.size.coerceAtLeast(1)), goal)
                else dose(mv, growth)
            }
        } else {
            // Time-block programmes (study, reading, work…): split the time by weight.
            val total = phase.moves.sumOf { it.amount }
            phase.moves.forEach { mv ->
                val min = (main * mv.amount / total.toDouble()).roundToInt().coerceAtLeast(1)
                parts += fill(mv.name, min, goal)
            }
        }
        program.coolDown?.let { parts += it.replace("{c}", "$cool") }
        return parts.joinToString(" · ")
    }

    private fun fill(template: String, minutes: Int, goal: String): String {
        val text = template
            .replace("{min}", "$minutes")
            .replace("{pages}", "${(minutes * 0.7).roundToInt().coerceAtLeast(1)}")
            .replace("{goal}", goal)
        return if ("{min}" in template) text else "$minutes min $text"
    }

    private fun dose(m: Move, growth: Double): String {
        val n = (m.amount * growth).let { if (m.dose == Dose.HOLD || m.dose == Dose.HOLD_SIDE) (it / 5).roundToInt() * 5 else it.roundToInt() }
            .coerceAtLeast(if (m.dose == Dose.HOLD || m.dose == Dose.HOLD_SIDE) 10 else 3)
        return when (m.dose) {
            Dose.REPS -> "${m.name} ${m.sets}×$n"
            Dose.REPS_SIDE -> "${m.name} ${m.sets}×$n/side"
            Dose.HOLD -> "${m.name} ${m.sets}×$n s"
            Dose.HOLD_SIDE -> "${m.name} ${m.sets}×$n s/side"
            Dose.ROUNDS, Dose.SHARE -> m.name
        }
    }

    // ---- Personality --------------------------------------------------------------------------

    private fun pick(p: Persona, lines: Map<Persona, List<String>>) = lines.getValue(p).random()

    private val opening = mapOf(
        Persona.MENTOR to listOf("Here's a calm, steady path to your goal.", "Good choice. Let's take this one step at a time."),
        Persona.SERGEANT to listOf("Listen up! Here's the mission.", "Goal received. Excuses not accepted. Here's the plan."),
        Persona.HYPE to listOf("Ohhh this is going to be FUN! 🎉 Here's the game plan:", "Let's GO! 🚀 Your plan is ready:"),
    )
    private val newWeek = mapOf(
        Persona.MENTOR to listOf("Week {n} begins. Here's your gentle next step."),
        Persona.SERGEANT to listOf("Week {n}. New orders. Move!"),
        Persona.HYPE to listOf("WEEK {n} UNLOCKED! 🔓🎉"),
    )
    private val harder = mapOf(
        Persona.MENTOR to listOf("Great sign — your body and mind are adapting. I've raised the challenge a little."),
        Persona.SERGEANT to listOf("Too easy? Then we work harder. Updated."),
        Persona.HYPE to listOf("Look at you levelling up! ⚡ Made it spicier:"),
    )
    private val easier = mapOf(
        Persona.MENTOR to listOf("Thanks for being honest. I've eased it off — consistency matters more than intensity."),
        Persona.SERGEANT to listOf("Fine. Dialled back. But you show up every single session."),
        Persona.HYPE to listOf("No stress! 💛 Toned it down so you can keep the streak alive:"),
    )
    private val maxedOut = mapOf(
        Persona.MENTOR to listOf("We're at the top level for this plan. If it still feels easy, set a bigger goal with “New goal”."),
        Persona.SERGEANT to listOf("Maxed out. Want more? Set a tougher goal, soldier."),
        Persona.HYPE to listOf("You've MAXED this plan! 🏆 Time for a bigger goal?"),
    )
    private val missed = mapOf(
        Persona.MENTOR to listOf("That's okay — one missed session changes nothing. Don't double up; just do the next one."),
        Persona.SERGEANT to listOf("Missed one. It happens once. Not twice. Next session, full effort."),
        Persona.HYPE to listOf("Life happens! 🙌 No guilt — we bounce back next session."),
    )
    private val motivate = mapOf(
        Persona.MENTOR to listOf("You don't need motivation, just the first two minutes. Start small and let momentum carry you.",
            "Future you is built by what you do today — even a short session counts."),
        Persona.SERGEANT to listOf("Motivation is optional. Discipline isn't. Alarm rings, you move.",
            "Nobody's coming to do it for you. Get up. Two minutes. Go."),
        Persona.HYPE to listOf("You've got this!! 💥 Just start — I'll be cheering the whole time! 📣",
            "Every session is XP. Go grab some! ⭐"),
    )
    private val thanks = mapOf(
        Persona.MENTOR to listOf("Well done. Remember to tap “I did today's session” to log it. 🌱"),
        Persona.SERGEANT to listOf("Good work. Log it and rest. Tomorrow we go again."),
        Persona.HYPE to listOf("YESSS! 🎉 Don't forget to log it for your streak! 🔥"),
    )
}
