package com.aiconicstudios.pa.coach

import com.aiconicstudios.pa.data.Category

/** How a move's amount is written and scaled. */
enum class Dose {
    /** "Squats 2×12" */
    REPS,
    /** "Hip circles 2×8/side" */
    REPS_SIDE,
    /** "Plank 2×30 s" */
    HOLD,
    /** "Seated twist 2×30 s/side" */
    HOLD_SIDE,
    /** "Run 1 min / walk 2 min ×10": amount = minutes per round, fills the session. */
    ROUNDS,
    /** A time block that takes a weighted share of the session; name may use {min} and {pages}. */
    SHARE,
}

data class Move(val name: String, val amount: Int, val dose: Dose, val sets: Int = 2)

data class Phase(
    val name: String,
    /** Fraction of the goal's weeks spent in this phase. */
    val share: Double,
    val focus: String,
    val moves: List<Move>,
    /** Cap on the main block for ROUNDS programmes, so beginners don't overdo it. */
    val maxMainMinutes: Int = Int.MAX_VALUE,
)

data class Program(
    val id: String,
    val approach: String,
    val warmUp: String?,
    val coolDown: String?,
    val phases: List<Phase>,
    val tips: List<String>,
    /** How many main moves fit per 10 minutes of session (0 = use time shares). */
    val movesPer10Min: Double = 0.0,
    val physical: Boolean = false,
)

/**
 * Built-in coaching programmes. Everything runs on the phone — no internet, no account, no cost.
 * {goal} is replaced with the user's goal text, {min} with the minutes given to a block,
 * {pages} with an estimate of pages readable in that time.
 */
object Programs {

    private val mobility = Program(
        id = "mobility",
        approach = "gentle range first, then longer holds and core control so the new flexibility sticks",
        warmUp = "{w} min warm-up: march on the spot + arm circles",
        coolDown = "{c} min cool-down: slow breathing in child's pose",
        movesPer10Min = 1.0,
        physical = true,
        phases = listOf(
            Phase("Loosen up", 0.35, "easy range of motion every session, no forcing", listOf(
                Move("Standing side bends", 10, Dose.REPS_SIDE),
                Move("Cat-cow", 10, Dose.REPS),
                Move("Seated spinal twist", 30, Dose.HOLD_SIDE),
                Move("Hip circles", 8, Dose.REPS_SIDE),
                Move("Child's pose side reach", 30, Dose.HOLD_SIDE),
                Move("Knee-to-chest hug", 30, Dose.HOLD_SIDE),
            )),
            Phase("Build range", 0.4, "longer holds and twisting under control", listOf(
                Move("Standing windmills", 10, Dose.REPS_SIDE),
                Move("Thread-the-needle", 30, Dose.HOLD_SIDE),
                Move("Lying knee-drop twists", 10, Dose.REPS_SIDE),
                Move("Gate-pose side stretch", 30, Dose.HOLD_SIDE),
                Move("Side plank on knees", 20, Dose.HOLD_SIDE),
                Move("Bird-dog", 8, Dose.REPS_SIDE),
            )),
            Phase("Deepen & strengthen", 0.25, "deeper stretches plus core work to hold the gains", listOf(
                Move("Overhead side bend with reach", 40, Dose.HOLD_SIDE),
                Move("Low lunge with side bend", 30, Dose.HOLD_SIDE),
                Move("Open-book rotations", 10, Dose.REPS_SIDE),
                Move("Russian twists", 16, Dose.REPS),
                Move("Side plank", 25, Dose.HOLD_SIDE),
                Move("Seated twist with deep exhale", 40, Dose.HOLD_SIDE),
            )),
        ),
        tips = listOf(
            "Stretch to mild tension, never pain — breathe out as you go deeper.",
            "Measure your side-bend reach on day 1 (fingertips down your leg) so you can see progress.",
            "Lay your mat out before the alarm rings so starting takes zero effort.",
        ),
    )

    private val running = Program(
        id = "running",
        approach = "run-walk intervals that slowly swap walking for running",
        warmUp = "{w} min brisk walk",
        coolDown = "{c} min easy walk + calf stretch",
        physical = true,
        phases = listOf(
            Phase("Run-walk", 0.3, "short runs, plenty of walking — finish feeling you could do more", listOf(
                Move("Run 1 min / walk 2 min", 3, Dose.ROUNDS),
                Move("Run 90 s / walk 2 min", 4, Dose.ROUNDS),
            ), maxMainMinutes = 25),
            Phase("Run more", 0.4, "longer runs, shorter walks", listOf(
                Move("Run 3 min / walk 1 min", 4, Dose.ROUNDS),
                Move("Run 5 min / walk 1 min", 6, Dose.ROUNDS),
                Move("Run 8 min / walk 1 min", 9, Dose.ROUNDS),
            ), maxMainMinutes = 35),
            Phase("Run strong", 0.3, "continuous running at a chatty pace", listOf(
                Move("Run 12 min / walk 1 min", 13, Dose.ROUNDS),
                Move("Easy continuous run {min} min", 1, Dose.SHARE),
                Move("Run 15 min / walk 1 min", 16, Dose.ROUNDS),
            ), maxMainMinutes = 45),
        ),
        tips = listOf(
            "Go slow enough to talk in short sentences — speed comes later.",
            "Never run two days back to back while starting out; rest days build you.",
            "Good shoes matter more than any gadget.",
        ),
    )

    private val strength = Program(
        id = "strength",
        approach = "easier versions first, then harder variations as your form improves",
        warmUp = "{w} min warm-up: jumping jacks + arm swings",
        coolDown = "{c} min cool-down: chest, quad and hip stretches",
        movesPer10Min = 1.0,
        physical = true,
        phases = listOf(
            Phase("Foundations", 0.35, "perfect form on easier versions", listOf(
                Move("Incline push-ups (hands on a table)", 8, Dose.REPS, 3),
                Move("Bodyweight squats", 12, Dose.REPS, 3),
                Move("Glute bridges", 12, Dose.REPS),
                Move("Plank", 20, Dose.HOLD),
                Move("Dead bug", 6, Dose.REPS_SIDE),
                Move("Wall sit", 20, Dose.HOLD),
            )),
            Phase("Build", 0.4, "more reps and harder variations", listOf(
                Move("Knee push-ups", 10, Dose.REPS, 3),
                Move("Reverse lunges", 8, Dose.REPS_SIDE, 3),
                Move("Plank", 35, Dose.HOLD),
                Move("Superman hold", 20, Dose.HOLD),
                Move("Mountain climbers", 20, Dose.REPS),
                Move("Chair dips", 8, Dose.REPS),
            )),
            Phase("Peak", 0.25, "full versions and slow negatives", listOf(
                Move("Full push-ups", 8, Dose.REPS, 3),
                Move("Slow negative push-ups (5 s down)", 5, Dose.REPS),
                Move("Jump squats", 10, Dose.REPS),
                Move("Side plank", 25, Dose.HOLD_SIDE),
                Move("Hollow-body hold", 20, Dose.HOLD),
                Move("Split squats", 8, Dose.REPS_SIDE),
            )),
        ),
        tips = listOf(
            "Stop each set with 1–2 good reps left in the tank — quality beats grinding.",
            "Rest 60–90 s between sets.",
            "Sleep and protein do half the work; don't skip them.",
        ),
    )

    private val fitness = Program(
        id = "fitness",
        approach = "a mix of cardio and simple circuits that gets steadily harder",
        warmUp = "{w} min warm-up: march + arm circles",
        coolDown = "{c} min cool-down: stretch legs and shoulders",
        movesPer10Min = 0.8,
        physical = true,
        phases = listOf(
            Phase("Get moving", 0.35, "build the habit with moderate effort", listOf(
                Move("Brisk walk {min} min", 3, Dose.SHARE),
                Move("Bodyweight squats", 12, Dose.REPS),
                Move("Incline push-ups", 8, Dose.REPS),
                Move("Step-ups", 10, Dose.REPS_SIDE),
                Move("Plank", 20, Dose.HOLD),
            )),
            Phase("Raise the pace", 0.4, "short bursts of harder effort", listOf(
                Move("Jog/walk intervals {min} min", 3, Dose.SHARE),
                Move("Jumping jacks", 40, Dose.HOLD, 3),
                Move("Lunges", 10, Dose.REPS_SIDE),
                Move("Mountain climbers", 20, Dose.REPS),
                Move("Glute bridges", 15, Dose.REPS),
            )),
            Phase("Push it", 0.25, "circuits with less rest", listOf(
                Move("Jog {min} min", 3, Dose.SHARE),
                Move("Burpees", 8, Dose.REPS, 3),
                Move("Squat jumps", 10, Dose.REPS),
                Move("Plank shoulder taps", 10, Dose.REPS_SIDE),
                Move("Skaters", 12, Dose.REPS_SIDE),
            )),
        ),
        tips = listOf(
            "Consistency beats intensity — showing up is the win.",
            "Pair this with an extra glass of water and an earlier bedtime.",
            "Track one simple number weekly (waist, steps or resting heart rate), not every day.",
        ),
    )

    private val studies = Program(
        id = "studies",
        approach = "understand → practise → test, using active recall and spaced repetition",
        warmUp = "{w} min: skim yesterday's notes",
        coolDown = "{c} min: write tomorrow's first task",
        phases = listOf(
            Phase("Understand", 0.35, "learn the material properly", listOf(
                Move("{min} min learn new material on {goal} (phone in another room)", 5, Dose.SHARE),
                Move("{min} min turn it into 10 flashcards", 2, Dose.SHARE),
                Move("{min} min explain it out loud as if teaching (Feynman)", 2, Dose.SHARE),
            )),
            Phase("Practise", 0.4, "retrieve, don't re-read", listOf(
                Move("{min} min practice questions", 5, Dose.SHARE),
                Move("{min} min blank-page recall: write everything you remember", 2, Dose.SHARE),
                Move("{min} min flashcard review", 2, Dose.SHARE),
                Move("{min} min fix your weakest topic", 2, Dose.SHARE),
            )),
            Phase("Exam mode", 0.25, "timed practice under real conditions", listOf(
                Move("{min} min timed past paper / mock test", 6, Dose.SHARE),
                Move("{min} min mark it and log every mistake", 2, Dose.SHARE),
                Move("{min} min redo each mistake from memory", 2, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Testing yourself beats re-reading — if it feels hard, it's working.",
            "Work in 25-minute focus blocks with 5-minute breaks.",
            "Review flashcards the day after, then 3 days after, then a week after.",
        ),
    )

    private val reading = Program(
        id = "reading",
        approach = "a steady page target each session, plus quick notes so it sticks",
        warmUp = null,
        coolDown = null,
        phases = listOf(
            Phase("Warm up the habit", 0.3, "comfortable pace, no pressure", listOf(
                Move("{min} min reading (≈{pages} pages)", 8, Dose.SHARE),
                Move("{min} min jot 3 key ideas", 1, Dose.SHARE),
            )),
            Phase("Steady pace", 0.45, "longer focus, phone away", listOf(
                Move("{min} min focused reading, phone away (≈{pages} pages)", 9, Dose.SHARE),
                Move("{min} min 3-line summary", 1, Dose.SHARE),
            )),
            Phase("Finish strong", 0.25, "finish and reflect", listOf(
                Move("{min} min reading (≈{pages} pages)", 8, Dose.SHARE),
                Move("{min} min note one idea to use this week", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Keep the book where your phone usually sits.",
            "Quitting a book you don't enjoy is allowed — pick the next one.",
            "Read the first page the moment the alarm rings; momentum does the rest.",
        ),
    )

    private val project = Program(
        id = "project",
        approach = "define done, break it into small tasks, then ship a little every session",
        warmUp = "{w} min: re-read your task list",
        coolDown = "{c} min: note where you stopped and the next step",
        phases = listOf(
            Phase("Plan", 0.2, "know exactly what 'done' looks like", listOf(
                Move("{min} min define the finish line for “{goal}” and list milestones", 3, Dose.SHARE),
                Move("{min} min break the first milestone into small tasks", 3, Dose.SHARE),
                Move("{min} min do the smallest task right now", 4, Dose.SHARE),
            )),
            Phase("Build", 0.55, "focused deep work on one task at a time", listOf(
                Move("{min} min deep work on the next task (notifications off)", 7, Dose.SHARE),
                Move("{min} min clear one blocker", 2, Dose.SHARE),
                Move("{min} min update your task list", 1, Dose.SHARE),
            )),
            Phase("Finish & ship", 0.25, "polish, check, deliver", listOf(
                Move("{min} min finish the remaining pieces", 6, Dose.SHARE),
                Move("{min} min check it / get feedback", 2, Dose.SHARE),
                Move("{min} min ship it or list what's left", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Start each session with the task you're avoiding.",
            "If a task takes more than one session, it's too big — split it.",
            "Done is better than perfect.",
        ),
    )

    private val mindful = Program(
        id = "mindful",
        approach = "short, easy practice first, then longer quiet sits",
        warmUp = null,
        coolDown = null,
        phases = listOf(
            Phase("Settle", 0.35, "breathing and body awareness", listOf(
                Move("{min} min slow breathing: 4 in, 6 out", 2, Dose.SHARE),
                Move("{min} min body scan, head to toe", 3, Dose.SHARE),
            )),
            Phase("Focus", 0.4, "train attention", listOf(
                Move("{min} min count breaths to 10, repeat", 3, Dose.SHARE),
                Move("{min} min mindful walk", 2, Dose.SHARE),
            )),
            Phase("Deepen", 0.25, "longer, quieter practice", listOf(
                Move("{min} min open-awareness sit", 4, Dose.SHARE),
                Move("{min} min journal: 3 good things today", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "A wandering mind is normal — noticing it IS the practice.",
            "Same place, same time makes it automatic.",
            "Two minutes on a bad day still counts.",
        ),
    )

    private val creative = Program(
        id = "creative",
        approach = "daily practice, then focused work on your piece, then finishing and sharing",
        warmUp = "{w} min warm-up: quick sketches / scales / free-writing",
        coolDown = null,
        phases = listOf(
            Phase("Practise", 0.3, "skills and inspiration", listOf(
                Move("{min} min skill drill", 3, Dose.SHARE),
                Move("{min} min study a work you admire and copy one thing", 2, Dose.SHARE),
            )),
            Phase("Create", 0.45, "make the thing", listOf(
                Move("{min} min work on “{goal}”", 6, Dose.SHARE),
                Move("{min} min step back and critique honestly", 1, Dose.SHARE),
            )),
            Phase("Finish & share", 0.25, "polish and show it", listOf(
                Move("{min} min finish and polish", 6, Dose.SHARE),
                Move("{min} min share it with one person", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Quantity leads to quality — make lots of small things.",
            "Stop mid-sentence or mid-sketch; it's easier to start next time.",
            "Keep a swipe file of things that inspire you.",
        ),
    )

    private val meals = Program(
        id = "meals",
        approach = "plan, prep and cook simple balanced meals until it's routine",
        warmUp = null,
        coolDown = "{c} min: clean as you go",
        phases = listOf(
            Phase("Simple start", 0.35, "easy wins", listOf(
                Move("{min} min plan 3 meals: protein + veg + carb", 2, Dose.SHARE),
                Move("{min} min wash & chop vegetables for 2 days", 3, Dose.SHARE),
                Move("{min} min cook one simple batch dish", 5, Dose.SHARE),
            )),
            Phase("Batch it", 0.4, "cook once, eat twice", listOf(
                Move("{min} min cook a double batch", 6, Dose.SHARE),
                Move("{min} min portion & label meals", 2, Dose.SHARE),
            )),
            Phase("Level up", 0.25, "variety", listOf(
                Move("{min} min try one new healthy recipe", 6, Dose.SHARE),
                Move("{min} min write next week's shopping list", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Half the plate vegetables is an easy rule.",
            "Keep 3 go-to recipes you can cook without thinking.",
            "Shop with a list, never hungry.",
        ),
    )

    private val rest = Program(
        id = "rest",
        approach = "a calm, repeatable wind-down so sleep comes easier",
        warmUp = null,
        coolDown = null,
        phases = listOf(
            Phase("Wind down", 0.4, "lower the stimulation", listOf(
                Move("{min} min screens off, dim the lights", 2, Dose.SHARE),
                Move("{min} min gentle stretching + slow breathing", 2, Dose.SHARE),
                Move("{min} min read something light", 2, Dose.SHARE),
            )),
            Phase("Routine", 0.6, "same steps, same order, same time", listOf(
                Move("{min} min tidy tomorrow's things", 1, Dose.SHARE),
                Move("{min} min write tomorrow's top 3", 1, Dose.SHARE),
                Move("{min} min breathing 4-7-8 in bed", 2, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Wake at the same time daily — even weekends. Bedtime follows.",
            "Keep the room cool, dark and quiet.",
            "No caffeine after lunch.",
        ),
    )

    private val habit = Program(
        id = "habit",
        approach = "start tiny, then grow it once it's automatic",
        warmUp = null,
        coolDown = "{c} min: tick it off and note how it went",
        phases = listOf(
            Phase("Start tiny", 0.3, "so easy you can't say no", listOf(
                Move("{min} min: {goal} — easy version", 1, Dose.SHARE),
            )),
            Phase("Grow", 0.45, "full sessions", listOf(
                Move("{min} min: {goal}", 4, Dose.SHARE),
                Move("{min} min: review what worked", 1, Dose.SHARE),
            )),
            Phase("Level up", 0.25, "stretch yourself", listOf(
                Move("{min} min: {goal} — harder version", 4, Dose.SHARE),
                Move("{min} min: plan the next level", 1, Dose.SHARE),
            )),
        ),
        tips = listOf(
            "Never miss twice in a row.",
            "Make it obvious: put the tools where you'll see them.",
            "Celebrate small wins — tick the box!",
        ),
    )

    private val keywordPrograms = listOf(
        listOf("flex", "waist", "stretch", "mobil", "yoga", "split", "posture", "twist", "supple") to mobility,
        listOf("run", "5k", "10k", "km", "jog", "marathon", "sprint", "endurance") to running,
        listOf("push", "pull", "strength", "strong", "muscle", "abs", "core", "plank", "squat", "arm") to strength,
        listOf("lose", "weight", "fat", "slim", "tone", "fit", "health", "energy", "cardio", "kg") to fitness,
        listOf("exam", "study", "test", "learn", "grade", "course", "revise", "language", "words") to studies,
        listOf("read", "book", "pages", "novel") to reading,
        listOf("project", "ship", "launch", "build", "backlog", "business", "promotion", "app", "website") to project,
        listOf("meditat", "calm", "stress", "mindful", "anxiety") to mindful,
        listOf("sleep", "bed", "wake", "rest", "nap") to rest,
        listOf("cook", "meal", "eat", "diet", "vegetable", "recipe") to meals,
        listOf("draw", "paint", "write", "story", "song", "guitar", "piano", "portfolio", "music", "design") to creative,
    )

    /** Picks the programme for a goal: the activity type decides, keywords refine it. */
    fun forGoal(category: Category, goal: String): Program {
        // Match the start of whole words, so "score" doesn't trigger "core".
        val tokens = goal.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        fun byKeyword(allowed: Set<Program>? = null) = keywordPrograms
            .filter { allowed == null || it.second in allowed }
            .firstOrNull { (words, _) -> words.any { w -> tokens.any { it.startsWith(w) } } }?.second
        return when (category) {
            Category.EXERCISE -> byKeyword(setOf(mobility, running, strength, fitness)) ?: fitness
            Category.STUDIES -> studies
            Category.READING -> reading
            Category.WORK -> project
            Category.MINDFUL -> mindful
            Category.CREATIVE -> creative
            Category.MEALS -> meals
            Category.REST -> rest
            Category.CUSTOM -> byKeyword() ?: habit
        }
    }
}
