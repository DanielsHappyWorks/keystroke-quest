package com.keystrokequest

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComboBox
import javax.swing.JProgressBar

class RcToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(AllIcons.Actions.Lightning)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rc = RcService.getInstance()
        val s = rc.state

        val levelLabel = JBLabel()
        val xpLabel = JBLabel()
        val bar = JProgressBar(0, 1000)
        val todayLabel = JBLabel()
        val totalLabel = JBLabel()
        val comboLabel = JBLabel()
        val streakLabel = JBLabel()
        val deletesLabel = JBLabel()
        val runsLabel = JBLabel()
        val achievementsLabel = JBLabel()
        val questsLabel = JBLabel()
        val catsLabel = JBLabel()
        val gitLabel = JBLabel()
        val themeBox = JComboBox(Theme.entries.map { it.label }.toTypedArray())
        val toggles = mutableListOf<Pair<JBCheckBox, () -> Boolean>>()

        fun refresh() {
            levelLabel.text = "Level ${s.level} — ${Progression.title(s.level)}"
            bar.value = (s.xp * 1000 / rc.xpForNext()).toInt()
            xpLabel.text = "${s.xp} / ${rc.xpForNext()} XP"
            todayLabel.text = "%,d".format(rc.todayChars())
            totalLabel.text = "%,d".format(s.totalChars)
            comboLabel.text = "x${s.bestCombo}"
            streakLabel.text = "${rc.currentStreak()} day(s)"
            deletesLabel.text = "%,d".format(s.deletes)
            runsLabel.text = "${s.runsPassed} passed / ${s.runFailures} failed"
            achievementsLabel.text = Progression.achievements.joinToString("<br>", "<html>", "</html>") {
                if (it.id in s.unlocked) "✓ <b>${it.title}</b> — ${it.desc} (+${it.xp} XP)" else "🔒 <font color=gray>${it.desc} (+${it.xp} XP)</font>"
            }
            questsLabel.text = rc.questRows().joinToString("<br>", "<html>", "</html>") { (q, progress, done) ->
                if (done) "✓ <s>${q.text}</s> (+${q.xp} XP)" else "☐ ${q.text} — ${minOf(progress, q.target)}/${q.target} (+${q.xp} XP)"
            }
            gitLabel.text = "${s.commits} commits · ${s.refactors} refactorings · ${s.cleanFiles} files cleaned"
            catsLabel.text = if (s.cats.isEmpty()) {
                "<html>No cats yet. Reach level 2 to get your first one.</html>"
            } else {
                s.cats.sortedByDescending { it.level }.joinToString("<br>", "<html>", "</html>") {
                    "🐱 <b>${it.name}</b> (level ${it.level}) — ${CatLook(it.seed).description} · ${it.pets} pets"
                }
            }
            themeBox.selectedItem = Theme.of(s.theme).label
            toggles.forEach { (box, get) -> box.isSelected = get() }
        }

        fun toggle(text: String, get: () -> Boolean, set: (Boolean) -> Unit) = JBCheckBox(text, get()).also { box ->
            box.addActionListener {
                set(box.isSelected)
                rc.fireChanged()
            }
            toggles += box to get
        }

        themeBox.addActionListener { s.theme = themeBox.selectedItem as String }

        val content = panel {
            group("Progress") {
                row { cell(levelLabel) }
                row { cell(xpLabel) }
                row { cell(bar).resizableColumn().align(AlignX.FILL) }
            }
            group("Cats") {
                row { cell(toggle("Show cats (turn off for best performance)", { s.showCats }) { s.showCats = it }) }
                row { cell(catsLabel) }
                row { comment("Click a cat to pet it: hearts and +1 XP (once a second per cat).") }
            }
            group("Daily Quests") {
                row { cell(questsLabel) }
            }
            group("Stats") {
                row("Characters today:") { cell(todayLabel) }
                row("Characters total:") { cell(totalLabel) }
                row("Best combo:") { cell(comboLabel) }
                row("Daily streak:") { cell(streakLabel) }
                row("Characters deleted:") { cell(deletesLabel) }
                row("Runs:") { cell(runsLabel) }
                row("Activity:") { cell(gitLabel) }
            }
            group("Trophies") {
                row { cell(achievementsLabel) }
            }
            group("Effects") {
                row("Theme:") { cell(themeBox) }
                row { cell(toggle("Blips when typing", { s.blips }) { s.blips = it }) }
                row { cell(toggle("Character labels", { s.chars }) { s.chars = it }) }
                row { cell(toggle("Booms when deleting", { s.explosions }) { s.explosions = it }) }
                row { cell(toggle("Screen shake", { s.shake }) { s.shake = it }) }
                row { cell(toggle("Combo streak", { s.combo }) { s.combo = it }) }
                row { cell(toggle("Caret trail", { s.caretTrail }) { s.caretTrail = it }) }
                row { cell(toggle("Line / block sparkles", { s.lineComplete }) { s.lineComplete = it }) }
                row { cell(toggle("Run, commit and debugger effects", { s.runEffects }) { s.runEffects = it }) }
                row { cell(toggle("Level-up fireworks", { s.fireworks }) { s.fireworks = it }) }
                row { cell(toggle("Sound", { s.sound }) { s.sound = it }) }
            }
            group("Wellbeing") {
                row { cell(toggle("Break reminders", { s.breakReminders }) { s.breakReminders = it }) }
            }
            group("Accessibility") {
                row { cell(toggle("Reduced effects (XP still counts)", { s.reducedEffects }) { s.reducedEffects = it }) }
            }
            row {
                button("Celebrate!") {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    ApplicationManager.getApplication().getService(TypingService::class.java).celebrate(editor, s)
                }
                button("Export stats card") { StatsCard.export(project) }
                button("Reset progress") {
                    if (Messages.showYesNoDialog(project, "Reset ALL progress: level, XP, cats, trophies, stats and quests? Your settings are kept.", "Keystroke Quest", null) == Messages.YES) {
                        rc.reset()
                    }
                }
            }
        }
        content.border = JBUI.Borders.empty(8, 14)

        refresh()
        ApplicationManager.getApplication().messageBus.connect(toolWindow.disposable)
            .subscribe(RcListener.TOPIC, object : RcListener {
                override fun changed(leveledUp: Boolean) = refresh()
            })

        val scroll = JBScrollPane(content).apply { border = JBUI.Borders.empty() }
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(scroll, "", false))
    }
}
