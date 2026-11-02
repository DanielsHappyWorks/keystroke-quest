package com.keystrokequest

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ToggleReducedEffectsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rc = RcService.getInstance()
        rc.state.reducedEffects = !rc.state.reducedEffects
        rc.fireChanged()
    }
}

class ToggleSoundAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rc = RcService.getInstance()
        rc.state.sound = !rc.state.sound
        rc.fireChanged()
    }
}

class ResetProgressAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        if (Messages.showYesNoDialog(e.project, "Reset ALL progress: level, XP, cats, trophies, stats and quests? Your settings are kept.", "Keystroke Quest", null) == Messages.YES) {
            RcService.getInstance().reset()
        }
    }
}
