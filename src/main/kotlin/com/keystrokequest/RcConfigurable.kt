package com.keystrokequest

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.panel

class RcConfigurable : BoundConfigurable("Keystroke Quest") {
    override fun createPanel(): DialogPanel {
        val s = RcService.getInstance().state
        return panel {
            group("Effects") {
                row { checkBox("Blips when typing").bindSelected(s::blips) }
                row { checkBox("Show character labels").bindSelected(s::chars) }
                row { checkBox("Booms when deleting").bindSelected(s::explosions) }
                row { checkBox("Screen shake").bindSelected(s::shake) }
                row("Shake intensity (px):") { slider(0, 12, 1, 4).bindValue(s::shakeIntensity) }
                row { checkBox("Fireworks on level-up").bindSelected(s::fireworks) }
                row { checkBox("Combo streak (bigger effects, bonus XP)").bindSelected(s::combo) }
                row { checkBox("Caret trail").bindSelected(s::caretTrail) }
                row { checkBox("Sparkles when a line or block is completed").bindSelected(s::lineComplete) }
                row { checkBox("Effects for finished runs, commits and debugger pauses").bindSelected(s::runEffects) }
                row("Theme and sound pack:") { comboBox(Theme.entries.map { it.label }).bindItem(s::theme) }
            }
            group("Audio") {
                row { checkBox("Play sounds").bindSelected(s::sound) }
                row("Volume:") { slider(0, 100, 10, 25).bindValue(s::volume) }
            }
            group("Progression") {
                row { checkBox("Show XP / level in the status bar").bindSelected(s::enableStatusBar) }
                row("Base XP per level:") { intTextField(1..100_000).bindIntText(s::baseXp) }
            }
            group("Cats") {
                row { checkBox("Show virtual cats (a new one every level)").bindSelected(s::showCats) }
                row("Max cats shown:") { intTextField(1..50).bindIntText(s::maxCatsShown) }
                row { comment("Turn cats off, or lower the limit, if the IDE feels slow. Your cats are kept either way.") }
            }
            group("Wellbeing") {
                row { checkBox("Remind me to take breaks").bindSelected(s::breakReminders) }
                row("Remind after (minutes):") { intTextField(1..600).bindIntText(s::breakMinutes) }
            }
            group("Accessibility") {
                row { checkBox("Reduced effects (no visuals, no sound, no shake; XP still counts)").bindSelected(s::reducedEffects) }
            }
        }
    }

    override fun apply() {
        super.apply()
        RcService.getInstance().fireChanged()
        ProjectManager.getInstance().openProjects.forEach {
            it.getService(StatusBarWidgetsManager::class.java)?.updateWidget(RcStatusBarWidgetFactory::class.java)
        }
    }
}
