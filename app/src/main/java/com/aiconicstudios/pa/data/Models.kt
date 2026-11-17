package com.aiconicstudios.pa.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Days are ISO day-of-week numbers: 1 = Monday … 7 = Sunday. */
@Serializable
data class Activity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: Category = Category.CUSTOM,
    val days: Set<Int>,
    /** Minutes after midnight, e.g. 5:30 → 330. */
    val startMinute: Int,
    val durationMinutes: Int,
    /** Index into the block colour palette. */
    val color: Int = category.defaultColor,
    val reminder: Boolean = true,
    /** Ring this many minutes before the start (0 = exactly at the start). */
    val leadMinutes: Int = 0,
    val notes: String = "",
) {
    val endMinute: Int get() = startMinute + durationMinutes
}

@Serializable
enum class Category(
    val emoji: String,
    val label: String,
    val coachName: String,
    val coachRole: String,
    val defaultColor: Int,
    val goalIdeas: List<String>,
) {
    STUDIES("📚", "Studies", "Professor Ada", "study coach and learning strategist", 0,
        listOf("Score an A in my next exam", "Finish a full textbook", "Learn 500 new words")),
    EXERCISE("🏃", "Exercise", "Coach Kai", "fitness and mobility coach", 1,
        listOf("Flexible waist", "Run 5 km without stopping", "Do 20 clean push-ups")),
    WORK("💼", "Work", "PM Nova", "project manager", 2,
        listOf("Ship my side project", "Clear my backlog", "Prepare a promotion case")),
    READING("📖", "Reading", "Librarian Sol", "reading coach", 3,
        listOf("Read 4 books this month", "Finish the book I started", "Read 30 pages a day")),
    MINDFUL("🧘", "Mindfulness", "Guide Mira", "mindfulness and wellbeing guide", 4,
        listOf("Meditate daily without skipping", "Sleep before 11pm", "Lower my stress")),
    CREATIVE("🎨", "Creative", "Muse Ivo", "creative mentor", 5,
        listOf("Finish a 10-piece portfolio", "Write a short story", "Learn a song on guitar")),
    MEALS("🍽️", "Meals", "Chef Basil", "nutrition and meal-prep coach", 6,
        listOf("Cook at home 5 days a week", "Eat more vegetables", "Meal-prep on Sundays")),
    REST("😴", "Rest", "Doc Luna", "rest and recovery coach", 7,
        listOf("Wake up refreshed", "Build a wind-down routine", "Nap without oversleeping")),
    CUSTOM("✨", "Custom", "Atlas", "personal assistant and goal coach", 8,
        listOf("Build a new habit", "Finish a personal project", "Stay consistent")),
}

@Serializable
enum class Persona(val label: String, val emoji: String, val voice: String) {
    MENTOR("Calm Mentor", "🦉",
        "Warm, patient and encouraging. Explain the why briefly. Never guilt-trip."),
    SERGEANT("Drill Sergeant", "🪖",
        "Direct, punchy, high standards, a little tough-love humour. Short sentences. Still kind underneath."),
    HYPE("Hype Friend", "🎉",
        "Upbeat, playful and celebratory. A sprinkle of emojis. Makes progress feel like a game."),
}

@Serializable
data class Goal(
    val text: String,
    val durationDays: Int,
    /** ISO date (yyyy-MM-dd) the goal was set. */
    val startDate: String,
)

@Serializable
data class PlanTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    /** ISO date of the session this task belongs to, if any. */
    val date: String? = null,
)

@Serializable
data class ChatMsg(val fromUser: Boolean, val text: String)

@Serializable
data class Workspace(
    val goal: Goal? = null,
    val plan: String? = null,
    val tasks: List<PlanTask> = emptyList(),
    val chat: List<ChatMsg> = emptyList(),
    /** ISO dates the user checked in ("I did it"). */
    val checkIns: List<String> = emptyList(),
    /** Which week of the goal the checklist was built for (-1 = none yet). */
    val planWeek: Int = -1,
    /** Difficulty adjustment from "too easy" / "too hard" feedback, -3..+3. */
    val intensity: Int = 0,
)

@Serializable
data class Settings(
    val firstHour: Int = 5,
    val lastHour: Int = 23,
    val use24h: Boolean = true,
    val persona: Persona = Persona.MENTOR,
    val ringUntilDismissed: Boolean = true,
    val hourHeightDp: Int = 64,
)

@Serializable
data class AppState(
    val activities: List<Activity> = emptyList(),
    val workspaces: Map<String, Workspace> = emptyMap(),
    val settings: Settings = Settings(),
) {
    fun workspace(id: String): Workspace = workspaces[id] ?: Workspace()
}
