package com.keystrokequest

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/** Single place where gameplay events turn into counters, XP, quest progress, level-ups and toasts. */
object Rewards {
    fun selectedEditor(project: Project?): Editor? =
        project?.takeUnless { it.isDisposed }?.let { FileEditorManager.getInstance(it).selectedTextEditor }

    /** Counts [metric] in the lifetime stats, then settles XP / quests / achievements. EDT only. */
    fun event(project: Project?, metric: Metric, amount: Int = 1, xp: Int = 0) {
        val s = RcService.getInstance().state
        when (metric) {
            Metric.COMMITS -> s.commits += amount
            Metric.REFACTORS -> s.refactors += amount
            Metric.CLEAN -> s.cleanFiles += amount
            Metric.PAUSES -> s.debugPauses += amount
            Metric.RUNS -> s.runsPassed += amount
            Metric.CHARS, Metric.COMBO -> Unit
        }
        settle(project, metric, amount, xp)
    }

    /** Applies [xp], advances quests for [metric], and announces anything earned. EDT only. */
    fun settle(project: Project?, metric: Metric, amount: Int, xp: Int) {
        val rc = RcService.getInstance()
        val typing = service<TypingService>()
        var leveled = if (xp > 0) rc.addXp(xp) else false
        val quests = rc.questProgress(metric, amount)
        if (quests.isNotEmpty()) leveled = rc.addXp(quests.sumOf { it.xp }) || leveled
        val unlocked = rc.checkAchievements()

        if (leveled) typing.celebrate(selectedEditor(project), rc.state)
        quests.forEach { typing.toast("Quest complete: ${it.text}", "+${it.xp} XP", project) }
        if (unlocked.isNotEmpty()) typing.announce(unlocked, project)
        if (xp > 0 || quests.isNotEmpty()) rc.fireChanged(leveled)
    }
}
