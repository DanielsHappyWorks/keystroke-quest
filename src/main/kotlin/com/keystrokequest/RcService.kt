package com.keystrokequest

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import java.time.LocalDate
import java.time.LocalTime

/** Persisted settings + progression. Setting names mirror the VS Code "Ridiculous Coding" extension. */
// The state name and file name below are kept from before the rename so existing progress still loads.
class RcState {
    var xp: Long = 0
    var level: Int = 1

    var explosions: Boolean = true
    var blips: Boolean = true
    var chars: Boolean = true
    var shake: Boolean = true
    var sound: Boolean = true
    var fireworks: Boolean = true
    var enableStatusBar: Boolean = true
    var reducedEffects: Boolean = false

    var combo: Boolean = true
    var caretTrail: Boolean = true
    var lineComplete: Boolean = true
    var runEffects: Boolean = true
    var breakReminders: Boolean = true
    var breakMinutes: Int = 45
    var theme: String? = "Neon"

    var totalChars: Long = 0
    var charsToday: Long = 0
    var deletes: Long = 0
    var nightChars: Long = 0
    var bestCombo: Int = 0
    var streakDays: Int = 0
    var todayDate: String? = null
    var runsPassed: Int = 0
    var runFailures: Int = 0
    var unlocked: MutableList<String> = mutableListOf()
    var commits: Int = 0
    var refactors: Int = 0
    var cleanFiles: Int = 0
    var debugPauses: Int = 0
    var bigDeletes: Int = 0
    var questsDone: Int = 0
    var questDate: String? = null
    var questProgress: MutableList<Int> = mutableListOf()
    var questClaimed: MutableList<Boolean> = mutableListOf()

    var showCats: Boolean = true
    var maxCatsShown: Int = 8
    var catLevelReached: Int = 1
    var cats: MutableList<CatData> = mutableListOf()

    var baseXp: Int = 50
    var shakeIntensity: Int = 4
    var volume: Int = 60
}

interface RcListener {
    fun changed(leveledUp: Boolean)

    companion object {
        val TOPIC: Topic<RcListener> = Topic.create("Keystroke Quest state", RcListener::class.java)
    }
}

@Service(Service.Level.APP)
@State(name = "RidiculousCodingState", storages = [Storage("ridiculousCoding.xml")])
class RcService : PersistentStateComponent<RcState> {
    private var data = RcState()

    override fun getState(): RcState = data

    override fun loadState(state: RcState) {
        XmlSerializerUtil.copyBean(state, data)
    }

    /** XP needed to go from [level] to the next one. */
    fun xpForNext(level: Int = data.level): Long {
        val base = data.baseXp.coerceAtLeast(1).toLong()
        return base * level + base * level * level / 4
    }

    /** Adds XP; returns true when at least one level was gained. */
    fun addXp(amount: Int): Boolean {
        data.xp += amount
        var leveled = false
        while (data.xp >= xpForNext()) {
            data.xp -= xpForNext()
            data.level++
            leveled = true
        }
        if (leveled) grantCats()
        fireChanged(leveled)
        return leveled
    }

    /** Gives one new, unique cat for every level reached that has not been rewarded yet. */
    fun grantCats(): List<CatData> {
        val fresh = mutableListOf<CatData>()
        while (data.catLevelReached < data.level) {
            data.catLevelReached++
            val cat = CatFactory.create(data.catLevelReached, data.cats)
            data.cats.add(cat)
            fresh += cat
        }
        return fresh
    }

    fun recordTyping(chars: Int, deleted: Int, combo: Int) {
        rollDay()
        data.totalChars += chars
        data.charsToday += chars
        data.deletes += deleted
        if (combo > data.bestCombo) data.bestCombo = combo
        if (LocalTime.now().hour < 4) data.nightChars += chars
    }

    private fun rollDay() {
        val today = LocalDate.now()
        if (data.todayDate == today.toString()) return
        data.streakDays = if (data.todayDate == today.minusDays(1).toString()) data.streakDays + 1 else 1
        data.charsToday = 0
        data.todayDate = today.toString()
    }

    /** Days in a row including today; 0 if the last active day is older than yesterday. */
    fun currentStreak(): Int {
        val today = LocalDate.now()
        return if (data.todayDate == today.toString() || data.todayDate == today.minusDays(1).toString()) data.streakDays else 0
    }

    fun todayChars(): Long = if (data.todayDate == LocalDate.now().toString()) data.charsToday else 0

    /**
     * Unlocks every achievement whose condition now holds, pays its XP, and returns the new ones.
     * Paying XP can unlock more (level trophies), so this repeats until nothing new unlocks.
     */
    fun checkAchievements(): List<Achievement> {
        val unlocked = mutableListOf<Achievement>()
        var leveled = false
        while (true) {
            val fresh = Progression.achievements.filter { it.id !in data.unlocked && it.check(data) }
            if (fresh.isEmpty()) break
            data.unlocked.addAll(fresh.map { it.id })
            unlocked += fresh
            leveled = addXp(fresh.sumOf { it.xp }) || leveled
        }
        if (leveled) service<TypingService>().celebrate(null, data)
        return unlocked
    }

    /** Rolls the daily quests over if the date changed while the IDE stayed open. True if it did. */
    fun refreshDay(): Boolean {
        if (data.questDate == LocalDate.now().toString()) return false
        ensureQuests()
        fireChanged(false)
        return true
    }

    private fun ensureQuests() {
        val today = LocalDate.now().toString()
        if (data.questDate == today && data.questProgress.size == Quests.PER_DAY) return
        data.questDate = today
        data.questProgress = MutableList(Quests.PER_DAY) { 0 }
        data.questClaimed = MutableList(Quests.PER_DAY) { false }
    }

    /** Today's quests with their progress and whether each is complete. */
    fun questRows(): List<Triple<Quest, Int, Boolean>> {
        ensureQuests()
        return Quests.forDate(LocalDate.now()).mapIndexed { i, q -> Triple(q, data.questProgress[i], data.questClaimed[i]) }
    }

    /** Advances matching quests; returns those completed by this update. */
    fun questProgress(metric: Metric, amount: Int): List<Quest> {
        ensureQuests()
        val done = mutableListOf<Quest>()
        Quests.forDate(LocalDate.now()).forEachIndexed { i, q ->
            if (q.metric != metric || data.questClaimed[i]) return@forEachIndexed
            data.questProgress[i] = if (metric == Metric.COMBO) maxOf(data.questProgress[i], amount) else data.questProgress[i] + amount
            if (data.questProgress[i] >= q.target) {
                data.questClaimed[i] = true
                data.questsDone++
                done += q
            }
        }
        return done
    }

    /** Wipes all progress (level, XP, cats, trophies, stats, quests). Settings are kept. */
    fun reset() {
        data.xp = 0
        data.level = 1
        data.cats.clear()
        data.catLevelReached = 1
        data.unlocked.clear()
        data.totalChars = 0
        data.charsToday = 0
        data.deletes = 0
        data.nightChars = 0
        data.bestCombo = 0
        data.streakDays = 0
        data.todayDate = null
        data.runsPassed = 0
        data.runFailures = 0
        data.commits = 0
        data.refactors = 0
        data.cleanFiles = 0
        data.debugPauses = 0
        data.bigDeletes = 0
        data.questsDone = 0
        data.questDate = null
        data.questProgress = mutableListOf()
        data.questClaimed = mutableListOf()
        fireChanged(false)
    }

    fun fireChanged(leveledUp: Boolean = false) {
        ApplicationManager.getApplication().messageBus.syncPublisher(RcListener.TOPIC).changed(leveledUp)
    }

    companion object {
        fun getInstance(): RcService = service()
    }
}
