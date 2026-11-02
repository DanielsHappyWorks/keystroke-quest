package com.keystrokequest

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import java.awt.Color

/** Project-scoped event hooks: run results, debugger pauses, refactorings and warning-clean files. */
@Service(Service.Level.PROJECT)
class RcProjectService(private val project: Project) : Disposable {
    private var started = false
    private var lastPauseMs = 0L
    private val warningCounts = HashMap<String, Int>()
    private val lastCleanMs = HashMap<String, Long>()

    fun start() {
        if (started) return
        started = true
        val bus = project.messageBus.connect(this)

        bus.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
                // 130/137/143: the user pressed Stop (SIGINT/SIGKILL/SIGTERM), which is neither a pass nor a fail.
                if (exitCode in STOPPED_EXIT_CODES) return
                ApplicationManager.getApplication().invokeLater { onRunFinished(exitCode == 0) }
            }
        })

        bus.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
            override fun processStarted(debugProcess: XDebugProcess) {
                debugProcess.session.addSessionListener(object : XDebugSessionListener {
                    override fun sessionPaused() {
                        ApplicationManager.getApplication().invokeLater { onDebugPause() }
                    }
                })
            }
        })

        bus.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
            override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) = Unit
            override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
                ApplicationManager.getApplication().invokeLater { onRefactoring() }
            }
            override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) = Unit
            override fun undoRefactoring(refactoringId: String) = Unit
        })

        bus.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
            override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                ApplicationManager.getApplication().invokeLater { onDaemonFinished(fileEditors) }
            }
        })
    }

    private fun effectsOn(): Boolean {
        val s = RcService.getInstance().state
        return s.runEffects && !s.reducedEffects
    }

    private fun volume(s: RcState) = if (s.sound) s.volume else 0

    private fun onRunFinished(success: Boolean) {
        if (project.isDisposed) return
        val s = RcService.getInstance().state
        if (success) {
            Rewards.event(project, Metric.RUNS)
        } else {
            s.runFailures++
            Rewards.settle(project, Metric.RUNS, 0, 0)
        }
        if (!effectsOn()) return
        val editor = Rewards.selectedEditor(project)
        val theme = Theme.of(s.theme)
        if (editor != null) {
            val overlay = Effects.overlay(editor)
            if (success) {
                overlay.confetti()
            } else {
                overlay.flash(Color(255, 40, 40))
                if (s.shake) overlay.shake(s.shakeIntensity + 6)
            }
        }
        if (success) SoundEngine.fanfare(volume(s), theme) else SoundEngine.boom(volume(s), theme)
    }

    private fun onDebugPause() {
        if (project.isDisposed) return
        val now = System.currentTimeMillis()
        // Stepping pauses the session on every line; only react to a pause once in a while.
        if (now - lastPauseMs < PAUSE_COOLDOWN_MS) return
        lastPauseMs = now
        Rewards.event(project, Metric.PAUSES)
        if (!effectsOn()) return
        val s = RcService.getInstance().state
        Rewards.selectedEditor(project)?.let { Effects.overlay(it).flash(Color(255, 200, 40)) }
        SoundEngine.chime(volume(s), Theme.of(s.theme))
    }

    private fun onRefactoring() {
        if (project.isDisposed) return
        Rewards.event(project, Metric.REFACTORS, xp = REFACTOR_XP)
        if (!effectsOn()) return
        val s = RcService.getInstance().state
        Rewards.selectedEditor(project)?.let { Effects.overlay(it).sparkle(it.caretModel.offset, 24, ring = true) }
        SoundEngine.chime(volume(s), Theme.of(s.theme))
    }

    /** Rewards a file going from "has warnings or errors" to "none", at most once per file per 30 seconds. */
    private fun onDaemonFinished(fileEditors: Collection<FileEditor>) {
        if (project.isDisposed) return
        for (fe in fileEditors) {
            val editor = (fe as? TextEditor)?.editor ?: continue
            val file = fe.file ?: continue
            val markup = DocumentMarkupModel.forDocument(editor.document, project, false) ?: continue
            val count = markup.allHighlighters.count {
                val info = HighlightInfo.fromRangeHighlighter(it as? RangeHighlighterEx ?: return@count false)
                info != null && info.severity >= HighlightSeverity.WARNING
            }
            val key = file.url
            val before = warningCounts.put(key, count)
            if (before == null || before == 0 || count != 0) continue

            val now = System.currentTimeMillis()
            if (now - (lastCleanMs[key] ?: 0L) < CLEAN_COOLDOWN_MS) continue
            lastCleanMs[key] = now
            Rewards.event(project, Metric.CLEAN, xp = CLEAN_XP)
            if (effectsOn() && !editor.isDisposed) {
                val s = RcService.getInstance().state
                Effects.overlay(editor).sparkle(editor.caretModel.offset, 16)
                SoundEngine.chime(volume(s), Theme.of(s.theme))
            }
        }
    }

    /** Called by the commit handler (see rc-vcs.xml) when a commit succeeds. */
    fun onCommit() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            Rewards.event(project, Metric.COMMITS, xp = COMMIT_XP)
            if (!effectsOn()) return@invokeLater
            val s = RcService.getInstance().state
            Rewards.selectedEditor(project)?.let { if (s.fireworks) Effects.overlay(it).fireworks() }
            SoundEngine.fanfare(volume(s), Theme.of(s.theme))
        }
    }

    override fun dispose() = Unit

    private companion object {
        val STOPPED_EXIT_CODES = setOf(130, 137, 143)
        const val PAUSE_COOLDOWN_MS = 5_000L
        const val CLEAN_COOLDOWN_MS = 30_000L
        const val REFACTOR_XP = 15
        const val CLEAN_XP = 10
        const val COMMIT_XP = 20
    }
}
