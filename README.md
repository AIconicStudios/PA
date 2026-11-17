# PA — Timetable, alarms & AI goal coaches (Android)

A weekly timetable that looks like the real thing: **days across the top, hours down the side**.
Every block repeats every week, **rings an alarm when it starts**, and opens its **own coach workspace**
that turns a goal ("flexible waist in a month") into a plan that fits that exact time slot.
**Free and fully offline** — no account, no API key, no internet.

| Timetable | Coach workspace | Setting a goal |
|---|---|---|
| ![](docs/screenshots/timetable.png) | ![](docs/screenshots/workspace.png) | ![](docs/screenshots/workspace_new_goal.png) |

## Features

**Timetable**
- Mon–Sun columns, hour rows; today's column highlighted with a live red "now" line.
- Tap any empty slot to add an activity there (snaps to the half hour); tap a block to open its coach; long-press to edit.
- Pick the start and end time (e.g. 05:30–07:30 → shows **2h**), or tap a duration chip.
- Repeat on any days (quick picks: Weekdays / Weekend / Daily). Overlapping blocks are flagged.
- 9 activity types (Studies, Exercise, Work, Reading, Mindfulness, Creative, Meals, Rest, Custom), 10 colours.
- Zoom (− / +) and visible-hour range in Settings. "Now / Up next" card with countdown at the top.
- "Load a sample week" button for first-time users.

**Alarms**
- Rings at the start of each activity, or 5/10/15/30 min early, every week.
- Exact alarms (`setAlarmClock`) that work in Doze; uses your alarm ringtone; can keep ringing until you respond.
- Notification shows today's checklist item, with **Snooze 5 min** and **Open coach** actions.
- Re-booked automatically after reboot, app update, or clock/time-zone change. "Ring a test alarm" in Settings.

**Built-in coach per activity** (offline, free)
- Each type has its own coach (Coach Kai for exercise, Professor Ada for studies, PM Nova for work, …).
- Set a goal and a time frame (1 week to 3 months). The coach picks a programme
  (stretching/mobility, running, strength, general fitness, study, reading, project, mindfulness,
  creative, meals, sleep or habit), splits it into phases, and fills every scheduled session with exercises or tasks sized to your slot.
- A fresh checklist appears each week, getting gradually harder; the last session is a test day.
- Quick buttons: "What's today?", "Too easy", "Too hard", "I missed a session", "Motivate me", "My progress".
  Too easy / too hard re-scales the sessions.
- Coach personality: Calm Mentor 🦉, Drill Sergeant 🪖 or Hype Friend 🎉.

**Motivation**
- "I did today's session" check-ins, 🔥 streaks (counted on scheduled days only), XP and levels
  (Rookie → Legend), goal progress bar (day X of N).

## Setup

1. Install the APK (from the **Actions → Android build → PA-debug-apk** artifact) or build it yourself:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`
2. Allow notifications when asked (needed for alarms).

## Tech

Kotlin + Jetpack Compose (Material 3), min Android 8.0 (API 26), target API 35.
Data is a single JSON file in app storage. The coach (`coach/Programs.kt`, `coach/OfflineCoach.kt`) runs entirely on the phone.

```bash
./gradlew :app:testDebugUnitTest   # unit tests + renders docs/screenshots/*.png
```
