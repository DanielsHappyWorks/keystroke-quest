package com.keystrokequest

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

/** Renders a shareable PNG summary of the player's progress, styled by the current theme. */
object StatsCard {
    private const val W = 1000
    private const val H = 540

    private class Look(val top: Color, val bottom: Color, val accent: Color)

    private fun look(theme: Theme) = when (theme) {
        Theme.NEON -> Look(Color(27, 16, 51), Color(70, 25, 100), Color(255, 79, 216))
        Theme.RETRO -> Look(Color(4, 18, 8), Color(10, 48, 16), Color(0, 255, 70))
        Theme.MINIMAL -> Look(Color(30, 32, 38), Color(48, 51, 62), Color(150, 190, 255))
        Theme.SAD -> Look(Color(16, 22, 36), Color(42, 52, 76), Color(130, 150, 185))
    }

    fun render(): BufferedImage {
        val rc = RcService.getInstance()
        val s = rc.state
        val look = look(Theme.of(s.theme))
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.paint = GradientPaint(0f, 0f, look.top, 0f, H.toFloat(), look.bottom)
            g.fillRoundRect(0, 0, W, H, 36, 36)

            g.color = look.accent
            g.font = Font("SansSerif", Font.BOLD, 22)
            g.drawString("🚀  KEYSTROKE QUEST", 48, 64)

            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 56)
            g.drawString(Progression.title(s.level), 48, 140)
            g.color = look.accent
            g.font = Font("SansSerif", Font.BOLD, 28)
            g.drawString("Level ${s.level}", 48, 182)

            // XP bar
            val need = rc.xpForNext()
            val frac = (s.xp.toDouble() / need).coerceIn(0.0, 1.0)
            g.color = Color(255, 255, 255, 40)
            g.fillRoundRect(48, 205, W - 96, 22, 22, 22)
            g.color = look.accent
            g.fillRoundRect(48, 205, ((W - 96) * frac).toInt().coerceAtLeast(22), 22, 22, 22)
            g.color = Color(255, 255, 255, 200)
            g.font = Font("SansSerif", Font.PLAIN, 16)
            g.drawString("${s.xp} / $need XP", 48, 250)

            val stats = listOf(
                "%,d".format(s.totalChars) to "characters typed",
                "x${s.bestCombo}" to "best combo",
                "${rc.currentStreak()}" to "day streak",
                "${s.unlocked.size}/${Progression.achievements.size}" to "achievements",
                "${s.commits}" to "commits",
                "${s.runsPassed}" to "runs passed",
                "${s.refactors}" to "refactorings",
                "${s.questsDone}" to "quests done",
            )
            val cols = 4
            val cellW = (W - 96) / cols
            stats.forEachIndexed { i, (value, label) ->
                val x = 48 + (i % cols) * cellW
                val y = 330 + (i / cols) * 100
                g.color = Color.WHITE
                g.font = Font("SansSerif", Font.BOLD, 40)
                g.drawString(value, x, y)
                g.color = Color(255, 255, 255, 150)
                g.font = Font("SansSerif", Font.PLAIN, 16)
                g.drawString(label, x, y + 26)
            }
        } finally {
            g.dispose()
        }
        return img
    }

    fun export(project: Project?) {
        val dialog = FileChooserFactory.getInstance()
            .createSaveFileDialog(FileSaverDescriptor("Export Stats Card", "Save your Keystroke Quest stats as a PNG", "png"), project)
        val wrapper = dialog.save(null as Path?, "keystroke-quest-stats.png") ?: return
        val file = wrapper.file
        ImageIO.write(render(), "png", file)
        com.intellij.openapi.components.service<TypingService>()
            .toast("Stats card saved", file.absolutePath, project)
    }
}

class ExportStatsCardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = StatsCard.export(e.project)
}
