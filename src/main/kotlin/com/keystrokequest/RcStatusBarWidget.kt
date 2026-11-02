package com.keystrokequest

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class RcStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = RcStatusBarWidget.ID
    override fun getDisplayName() = "Keystroke Quest"
    override fun isAvailable(project: Project) = RcService.getInstance().state.enableStatusBar
    override fun createWidget(project: Project): StatusBarWidget = RcStatusBarWidget(project)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}

class RcStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null

    override fun ID() = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(RcListener.TOPIC, object : RcListener {
                override fun changed(leveledUp: Boolean) = statusBar.updateWidget(ID)
            })
    }

    override fun getText(): String {
        val rc = RcService.getInstance()
        return "🚀 ${Progression.title(rc.state.level)} · Lv ${rc.state.level} · ${rc.state.xp}/${rc.xpForNext()} XP"
    }

    override fun getAlignment() = Component.CENTER_ALIGNMENT
    override fun getTooltipText() = "Keystroke Quest — click to open the panel"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("Keystroke Quest")?.activate(null)
    }

    override fun dispose() {
        statusBar = null
    }

    companion object {
        const val ID = "KeystrokeQuest.StatusBar"
    }
}
