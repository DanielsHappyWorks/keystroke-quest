package com.keystrokequest

import java.awt.Color
import java.time.LocalDate
import java.util.Random

enum class Wave { SQUARE, MIX, SINE }

/** Visual + audio flavour. Selected by name in [RcState.theme]. */
enum class Theme(
    val label: String, val wave: Wave, val loudness: Double, val sizeScale: Float, val pixel: Boolean,
    /** Particles sink like rain instead of bursting upward. */
    val droop: Boolean = false,
) {
    NEON("Neon", Wave.MIX, 1.0, 1f, false),
    RETRO("Retro 8-bit", Wave.SQUARE, 0.9, 1.3f, true),
    MINIMAL("Minimal", Wave.SINE, 0.6, 0.7f, false),
    SAD("Sad", Wave.SINE, 0.7, 0.9f, false, droop = true);

    fun color(r: Random): Color = when (this) {
        NEON -> Color.getHSBColor(r.nextFloat(), 0.75f, 1f)
        RETRO -> RETRO_COLORS[r.nextInt(RETRO_COLORS.size)]
        MINIMAL -> MINIMAL_COLORS[r.nextInt(MINIMAL_COLORS.size)]
        SAD -> SAD_COLORS[r.nextInt(SAD_COLORS.size)]
    }

    companion object {
        private val RETRO_COLORS = listOf(Color(0, 255, 70), Color(255, 176, 0), Color(255, 60, 60), Color(60, 200, 255))
        private val MINIMAL_COLORS = listOf(Color(225, 232, 255), Color(150, 190, 255), Color(190, 170, 255))
        private val SAD_COLORS = listOf(Color(90, 120, 180), Color(120, 140, 170), Color(70, 90, 140), Color(150, 160, 180))
        fun of(name: String?): Theme = entries.firstOrNull { it.label == name } ?: NEON
    }
}

/** Trophies pay [xp] once on unlock. */
class Achievement(val id: String, val title: String, val desc: String, val xp: Int, val check: (RcState) -> Boolean)

object Progression {
    private val titles = listOf(
        1 to "Script Kiddie", 3 to "Code Monkey", 5 to "Hacker", 8 to "Dev Ninja",
        12 to "Code Wizard", 17 to "Architect", 25 to "Grandmaster", 40 to "Ridiculous",
    )

    fun title(level: Int): String = titles.last { level >= it.first }.second

    val achievements = listOf(
        Achievement("first", "Hello, World", "Type your first character", 5) { it.totalChars >= 1 },
        Achievement("chars1k", "Warming Up", "Type 1,000 characters", 75) { it.totalChars >= 1_000 },
        Achievement("chars10k", "Keyboard Warrior", "Type 10,000 characters", 200) { it.totalChars >= 10_000 },
        Achievement("chars100k", "Typewriter of Doom", "Type 100,000 characters", 500) { it.totalChars >= 100_000 },
        Achievement("combo25", "On a Roll", "Reach a x25 combo", 75) { it.bestCombo >= 25 },
        Achievement("combo100", "Unstoppable", "Reach a x100 combo", 200) { it.bestCombo >= 100 },
        Achievement("demolition", "Demolition Crew", "Delete 500 characters", 100) { it.deletes >= 500 },
        Achievement("night", "Night Owl", "Type 100 characters between midnight and 4am", 100) { it.nightChars >= 100 },
        Achievement("level5", "Hacker", "Reach level 5", 125) { it.level >= 5 },
        Achievement("level10", "Double Digits", "Reach level 10", 250) { it.level >= 10 },
        Achievement("streak3", "Hat Trick", "Code 3 days in a row", 100) { it.streakDays >= 3 },
        Achievement("streak7", "Full Week", "Code 7 days in a row", 250) { it.streakDays >= 7 },
        Achievement("green", "Green Machine", "10 successful runs", 125) { it.runsPassed >= 10 },
        Achievement("commit1", "Committed", "Make your first commit", 50) { it.commits >= 1 },
        Achievement("commit25", "Commit Machine", "Make 25 commits", 150) { it.commits >= 25 },
        Achievement("refactor10", "Refactorer", "Do 10 refactorings", 125) { it.refactors >= 10 },
        Achievement("clean10", "Spotless", "Clear all warnings in 10 files", 125) { it.cleanFiles >= 10 },
        Achievement("pause25", "Bug Hunter", "Hit 25 breakpoints", 125) { it.debugPauses >= 25 },
        Achievement("spring", "Spring Cleaning", "Delete 5 large blocks of code", 75) { it.bigDeletes >= 5 },
        Achievement("quests10", "Quest Master", "Complete 10 daily quests", 150) { it.questsDone >= 10 },
        Achievement("cat1", "Cat Person", "Earn your first cat", 50) { it.cats.isNotEmpty() },
        Achievement("cat5", "Cat Cafe", "Have 5 cats", 100) { it.cats.size >= 5 },
        Achievement("cat10", "A Clowder", "Have 10 cats", 200) { it.cats.size >= 10 },
        Achievement("cat25", "Cat Sanctuary", "Have 25 cats", 500) { it.cats.size >= 25 },
        Achievement("lives9", "Nine Lives", "Reach level 9", 150) { it.level >= 9 },
        Achievement("pet1", "Pet Pal", "Pet a cat for the first time", 12) { it.cats.sumOf { c -> c.pets } >= 1 },
        Achievement("pet50", "Good Human", "Pet cats 50 times", 75) { it.cats.sumOf { c -> c.pets } >= 50 },
        Achievement("pet250", "Professional Petter", "Pet cats 250 times", 200) { it.cats.sumOf { c -> c.pets } >= 250 },
        Achievement("pet1000", "Purr-fessor", "Pet cats 1,000 times", 400) { it.cats.sumOf { c -> c.pets } >= 1000 },
        Achievement("bestfriend", "Best Friends", "Pet the same cat 50 times", 150) { it.cats.any { c -> c.pets >= 50 } },
        Achievement("petall", "Gotta Pet 'Em All", "Pet every one of at least 5 cats", 150) { it.cats.size >= 5 && it.cats.all { c -> c.pets >= 1 } },
        Achievement("oops", "It Happens", "Have a run fail", 50) { it.runFailures >= 1 },
    )
}

/** What a daily quest counts. */
enum class Metric { CHARS, COMBO, RUNS, COMMITS, REFACTORS, CLEAN, PAUSES }

class Quest(val metric: Metric, val target: Int, val xp: Int) {
    val text: String
        get() = when (metric) {
            Metric.CHARS -> "Type %,d characters".format(target)
            Metric.COMBO -> "Reach a x$target combo"
            Metric.RUNS -> "Pass $target runs"
            Metric.COMMITS -> "Make $target commit(s)"
            Metric.REFACTORS -> "Do $target refactoring(s)"
            Metric.CLEAN -> "Clear all warnings in $target file(s)"
            Metric.PAUSES -> "Hit $target breakpoint(s)"
        }
}

object Quests {
    const val PER_DAY = 3

    private val choices = mapOf(
        Metric.CHARS to (listOf(500, 1000, 2000) to 40),
        Metric.COMBO to (listOf(25, 50) to 40),
        Metric.RUNS to (listOf(3, 5) to 30),
        Metric.COMMITS to (listOf(1, 2) to 30),
        Metric.REFACTORS to (listOf(1, 2) to 30),
        Metric.CLEAN to (listOf(2, 3) to 30),
        Metric.PAUSES to (listOf(3, 5) to 30),
    )

    /** Same quests for everyone on a given day: one typing quest plus two others. */
    fun forDate(date: LocalDate): List<Quest> {
        val rnd = Random(date.toEpochDay())
        val metrics = listOf(Metric.CHARS) + (Metric.entries - Metric.CHARS).shuffled(rnd).take(PER_DAY - 1)
        return metrics.map { m ->
            val (targets, xp) = choices.getValue(m)
            Quest(m, targets[rnd.nextInt(targets.size)], xp)
        }
    }
}
