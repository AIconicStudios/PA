package com.aiconicstudios.pa.ai

import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.ChatMsg
import com.aiconicstudios.pa.data.Persona
import com.aiconicstudios.pa.data.ScheduleMath
import com.aiconicstudios.pa.data.Workspace
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The per-activity AI coach. Each activity's workspace talks to Claude with a system prompt
 * describing that activity's slot in the timetable, the goal and the progress so far.
 */
class Coach(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    sealed interface Result {
        data class Reply(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun reply(
        activity: Activity,
        workspace: Workspace,
        persona: Persona,
        use24h: Boolean,
        conversation: List<ChatMsg>,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val builder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(systemPrompt(activity, workspace, persona, use24h))
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                // If a safety classifier declines, let the server retry on a fallback model automatically.
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
            // The API expects the conversation to start with the user; skip any leading coach lines.
            conversation.dropWhile { !it.fromUser }.forEach { msg ->
                if (msg.fromUser) builder.addUserMessage(msg.text) else builder.addAssistantMessage(msg.text)
            }
            val response = client.beta().messages().create(builder.build())

            if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                return@withContext Result.Failure(
                    "Your coach can't help with that request. Try rephrasing your goal."
                )
            }
            val text = response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("\n")
                .trim()
            if (text.isEmpty()) Result.Failure("Your coach went quiet — please try again.")
            else Result.Reply(text)
        } catch (e: UnauthorizedException) {
            Result.Failure("Your Claude API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) {
            Result.Failure("Your coach is busy right now. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            Result.Failure("Claude returned an error (${e.statusCode()}). Try again shortly.")
        } catch (e: IOException) {
            Result.Failure("No connection. Check your internet and try again.")
        } catch (e: RuntimeException) {
            Result.Failure("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        /** Kick-off message the app sends on the user's behalf when a goal is set. */
        fun planRequest(goal: String, durationDays: Int): String =
            "My goal: $goal. Time frame: ${durationLabel(durationDays)}. Please build my plan."

        fun durationLabel(days: Int): String = when {
            days % 30 == 0 && days >= 30 -> if (days == 30) "1 month" else "${days / 30} months"
            days % 7 == 0 -> if (days == 7) "1 week" else "${days / 7} weeks"
            else -> "$days days"
        }

        fun systemPrompt(activity: Activity, workspace: Workspace, persona: Persona, use24h: Boolean): String {
            val today = LocalDate.now()
            val goal = workspace.goal
            val sessionsPerWeek = activity.days.size
            val goalBlock = if (goal == null) "No goal set yet." else {
                val start = LocalDate.parse(goal.startDate)
                val dayNumber = (today.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1)
                val end = start.plusDays(goal.durationDays.toLong() - 1)
                """
                Goal: ${goal.text}
                Time frame: ${durationLabel(goal.durationDays)} (${goal.startDate} to $end), today is day $dayNumber of ${goal.durationDays}.
                Sessions checked in so far: ${workspace.checkIns.size}.
                Current streak: ${ScheduleMath.streak(activity.days, workspace.checkIns, today)}.
                Checklist progress: ${workspace.tasks.count { it.done }} of ${workspace.tasks.size} done.
                """.trimIndent()
            }
            val openTasks = workspace.tasks.filterNot { it.done }.take(15)
            return """
                You are ${activity.category.coachName}, the user's ${activity.category.coachRole}, living inside a timetable app.
                You are attached to one recurring block in their weekly timetable and your job is to get them to their goal using only the time that block gives you.

                The block:
                - Name: ${activity.title} (${activity.category.label})
                - When: ${ScheduleMath.daysLabel(activity.days)}, ${ScheduleMath.formatRange(activity, use24h)} (${ScheduleMath.formatDuration(activity.durationMinutes)} per session, $sessionsPerWeek session(s) per week)
                ${if (activity.notes.isNotBlank()) "- Notes from the user: ${activity.notes}" else ""}
                - Today: ${today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))}

                ${goalBlock}
                ${if (openTasks.isNotEmpty()) "Open checklist items:\n" + openTasks.joinToString("\n") { "- ${it.text}" } else ""}

                Voice: ${persona.label}. ${persona.voice}

                When asked to build or rebuild a plan:
                - Open with a one-line summary of the approach and whether the goal is realistic in the time frame (if not, say so and propose a realistic version).
                - Give a checklist for the next 7 days as markdown task lines, one per scheduled session, written exactly like "- [ ] Mon: 10 min warm-up, 3×12 standing side bends, 2×30 s cat-cow". Each item must fit inside one session's length.
                - Then outline the remaining weeks as short phases ("## Week 2–3: …") showing how difficulty progresses.
                - Finish with one tip for staying consistent.
                Keep it scannable on a phone: short headings, short bullets, no tables.

                For follow-up messages, answer briefly and practically. If the plan needs to change (missed sessions, too hard, new constraints), include an updated next-7-days checklist in the same "- [ ] …" format, because the app turns those lines into the user's checklist.
                For physical goals, favour safe progressions and tell the user to stop and seek advice if something hurts. Don't diagnose medical conditions.
            """.trimIndent()
        }
    }
}
