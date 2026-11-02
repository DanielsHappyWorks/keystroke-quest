package com.keystrokequest

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Point
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RcStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<TypingService>()
        project.service<RcProjectService>().start()
        ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) project.service<CatWorld>().start() }
    }
}

/** Listens to document edits in the focused editor and turns them into XP, combo, effects and sound. */
@Service(Service.Level.APP)
class TypingService : Disposable {
    private var lastEditNanos = 0L
    private var smoothedCps = 3.0

    private var combo = 0
    private var lastComboMs = 0L
    private var lastBigDeleteMs = 0L

    @Volatile private var sessionStartMs = 0L
    @Volatile private var lastActivityMs = 0L
    @Volatile private var lastReminderMs = 0L
    @Volatile private var reminded = false

    private val breakCheck: ScheduledFuture<*>

    init {
        val factory = EditorFactory.getInstance()
        factory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = onChange(event)
        }, this)
        factory.eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = onCaret(event)
        }, this)
        factory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorReleased(event: EditorFactoryEvent) = Effects.release(event.editor)
        }, this)
        breakCheck = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay({ checkBreak() }, 1, 1, TimeUnit.MINUTES)
    }

    private fun theme(s: RcState) = Theme.of(s.theme)

    private fun onCaret(event: CaretEvent) {
        val s = RcService.getInstance().state
        if (!s.caretTrail || s.reducedEffects) return
        val editor = event.editor
        if (!editor.contentComponent.isFocusOwner) return
        val pos = event.newPosition ?: return
        val p = editor.logicalPositionToXY(pos)
        Effects.overlay(editor).trail(Point(p.x, p.y + editor.lineHeight / 2))
    }

    private fun onChange(event: DocumentEvent) {
        val editor = EditorFactory.getInstance().getEditors(event.document)
            .firstOrNull { it.contentComponent.isFocusOwner && !it.isViewer } ?: return

        val inserted = event.newLength
        val removed = event.oldLength
        val offset = event.offset
        val text = event.newFragment.toString()
        val rc = RcService.getInstance()
        val s = rc.state

        val nowMs = System.currentTimeMillis()
        trackActivity(nowMs, rc)

        val nowNs = System.nanoTime()
        val dtMs = ((nowNs - lastEditNanos) / 1_000_000.0).coerceIn(60.0, 1000.0)
        lastEditNanos = nowNs
        smoothedCps = smoothedCps * 0.7 + (1000.0 / dtMs) * 0.3

        val typing = inserted > removed && inserted <= 40
        var milestone = false
        if (typing) {
            combo = if (nowMs - lastComboMs <= COMBO_WINDOW_MS) combo + 1 else 1
            lastComboMs = nowMs
            milestone = combo in MILESTONES
        }

        val chars = inserted.coerceAtMost(5)
        val deleted = (removed - inserted).coerceIn(0, 5)
        var bonus = if (s.combo && typing) 1 + (combo / 25).coerceAtMost(2) else 1
        if (typing && isTestFile(event)) bonus *= 2
        rc.recordTyping(if (inserted > 0) chars else 0, deleted, combo)
        var leveled = if (inserted > 0) rc.addXp(chars * bonus) else false
        // Deleting a big block (dead code) is rewarded, at most once a minute so it can't be farmed.
        if (removed - inserted >= BIG_DELETE_CHARS && nowMs - lastBigDeleteMs > 60_000) {
            lastBigDeleteMs = nowMs
            s.bigDeletes++
            leveled = rc.addXp(BIG_DELETE_XP) || leveled
            toast("Spring cleaning", "+$BIG_DELETE_XP XP for deleting dead code.", editor.project)
        }
        val unlocked = rc.checkAchievements()
        if (inserted > 0) Rewards.settle(editor.project, Metric.CHARS, chars, 0)
        if (typing) Rewards.settle(editor.project, Metric.COMBO, combo, 0)

        ApplicationManager.getApplication().invokeLater {
            if (!editor.isDisposed) react(editor, s, inserted, removed, offset, text, leveled, milestone)
            if (unlocked.isNotEmpty()) announce(unlocked, editor.project)
        }
    }

    private fun isTestFile(event: DocumentEvent): Boolean {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return false
        val path = file.path
        return "/test/" in path || "/tests/" in path || file.nameWithoutExtension.let { it.endsWith("Test") || it.endsWith("Tests") || it.endsWith("Spec") }
    }

    private fun react(
        editor: Editor, s: RcState, inserted: Int, removed: Int, offset: Int, text: String,
        leveled: Boolean, milestone: Boolean,
    ) {
        if (s.reducedEffects) return
        val overlay = Effects.overlay(editor)
        val theme = theme(s)
        val at = if (inserted > removed) offset + inserted else offset
        val volume = if (s.sound) s.volume else 0
        val power = if (s.combo) (combo / 10).coerceAtMost(8) else 0

        if (inserted > removed) {
            if (text.contains('\n')) {
                overlay.newline(at)
                if (s.lineComplete) lineComplete(editor, overlay, offset, volume, theme)
            } else if (s.blips) {
                val label = if (s.chars && inserted == 1 && !text[0].isWhitespace()) text[0].toString() else null
                overlay.blip(at, label, power)
            }
            SoundEngine.blip(500 + smoothedCps.coerceAtMost(12.0) * 45 + power * 25, volume, theme)
            if (s.combo && combo >= 3) overlay.combo(combo, COMBO_WINDOW_MS)
        } else if (removed > inserted) {
            if (s.explosions) overlay.boom(at, power)
            SoundEngine.boom(volume, theme)
        }

        if (s.shake) overlay.shake(s.shakeIntensity + power / 2)

        if (milestone && s.combo) {
            overlay.sparkle(at, 30, ring = true)
            overlay.shake(s.shakeIntensity + 4)
            SoundEngine.chime(volume, theme)
        }
        if (leveled) celebrate(editor, s)
    }

    private fun lineComplete(editor: Editor, overlay: EffectsOverlay, offset: Int, volume: Int, theme: Theme) {
        val doc = editor.document
        if (offset > doc.textLength) return
        val start = doc.getLineStartOffset(doc.getLineNumber(offset))
        val line = doc.getText(TextRange(start, offset)).trim()
        when {
            line.endsWith("}") -> {
                overlay.sparkle(offset, 28, ring = true)
                SoundEngine.chime(volume, theme)
            }
            line.endsWith(";") || line.endsWith(")") -> overlay.sparkle(offset, 10)
        }
    }

    private fun trackActivity(now: Long, rc: RcService) {
        if (now - lastActivityMs > IDLE_BREAK_MS) {
            if (reminded && rc.state.breakReminders) {
                rc.addXp(BREAK_BONUS_XP)
                toast("Welcome back!", "+$BREAK_BONUS_XP XP for taking a break.", null)
            }
            sessionStartMs = now
            lastReminderMs = 0
            reminded = false
        }
        lastActivityMs = now
    }

    /** Runs on a background thread once a minute. */
    private fun checkBreak() {
        // Roll the daily quests over at midnight without needing an IDE restart.
        ApplicationManager.getApplication().invokeLater { RcService.getInstance().refreshDay() }
        val s = RcService.getInstance().state
        val now = System.currentTimeMillis()
        if (!s.breakReminders || lastActivityMs == 0L || now - lastActivityMs > IDLE_BREAK_MS) return
        val since = maxOf(sessionStartMs, lastReminderMs)
        if (now - since >= s.breakMinutes.coerceAtLeast(1) * 60_000L) {
            lastReminderMs = now
            reminded = true
            val minutes = (now - sessionStartMs) / 60_000
            toast("Time for a break", "You've been coding for $minutes minutes. Stand up, stretch, drink some water.", ProjectManager.getInstance().openProjects.firstOrNull())
        }
    }

    fun announce(unlocked: List<Achievement>, project: Project?) {
        val s = RcService.getInstance().state
        unlocked.forEach { toast("Trophy unlocked: ${it.title}", "${it.desc} — +${it.xp} XP", project) }
        if (!s.reducedEffects) SoundEngine.chime(if (s.sound) s.volume else 0, theme(s))
    }

    fun celebrate(editor: Editor?, s: RcState) {
        if (s.reducedEffects) return
        if (s.fireworks && editor != null) Effects.overlay(editor).fireworks()
        SoundEngine.fanfare(if (s.sound) s.volume else 0, theme(s))
        toast("Level ${s.level} reached! 🚀", "You are now a ${Progression.title(s.level)}.", editor?.project)
    }

    fun toast(title: String, content: String, project: Project?) {
        NotificationGroupManager.getInstance().getNotificationGroup("Keystroke Quest")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    override fun dispose() {
        breakCheck.cancel(false)
    }

    private companion object {
        const val COMBO_WINDOW_MS = 1500L
        const val IDLE_BREAK_MS = 5 * 60_000L
        const val BREAK_BONUS_XP = 25
        const val BIG_DELETE_CHARS = 200
        const val BIG_DELETE_XP = 10
        val MILESTONES = setOf(10, 25, 50, 100, 200)
    }
}
