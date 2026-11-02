package com.keystrokequest

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.util.Random
import javax.swing.JComponent
import javax.swing.Timer

object Effects {
    private val KEY = Key.create<EffectsOverlay>("keystrokequest.overlay")

    fun overlay(editor: Editor): EffectsOverlay =
        editor.getUserData(KEY) ?: EffectsOverlay(editor).also { editor.putUserData(KEY, it) }

    fun release(editor: Editor) {
        editor.getUserData(KEY)?.dispose()
        editor.putUserData(KEY, null)
    }
}

/**
 * Transparent, mouse-transparent layer added on top of an editor's content component. Holds every
 * particle (blips, booms, trails, confetti, fireworks), the combo counter, screen flash and the
 * screen-shake state, driven by one timer that only runs while something is animating.
 */
class EffectsOverlay(private val editor: Editor) : JComponent() {
    private class Particle(
        var x: Double, var y: Double, var vx: Double, var vy: Double,
        val gravity: Double, val maxLife: Int, val color: Color, val size: Float,
        val label: String? = null, val ring: Boolean = false, val rect: Boolean = false,
    ) {
        var age = 0
    }

    private class Burst(var delay: Int, val x: Double, val y: Double)

    private val random = Random()
    private val particles = ArrayList<Particle>()
    private val bursts = ArrayList<Burst>()
    private val timer = Timer(16) { tick() }

    private var shakeFrames = 0
    private var shakeMagnitude = 0
    private var appliedDx = 0
    private var appliedDy = 0

    private var comboCount = 0
    private var comboExpireMs = 0L
    private var comboWindowMs = 1500L

    private var flashColor: Color = Color.RED
    private var flashFrames = 0

    init {
        isOpaque = false
        editor.contentComponent.add(this)
    }

    // Let every mouse event fall through to the editor underneath.
    override fun contains(x: Int, y: Int) = false

    fun dispose() {
        timer.stop()
        particles.clear()
        bursts.clear()
        editor.contentComponent.remove(this)
    }

    private fun theme() = Theme.of(RcService.getInstance().state.theme)

    private fun caretPoint(offset: Int): Point {
        val safe = offset.coerceIn(0, editor.document.textLength)
        val p = editor.offsetToXY(safe)
        return Point(p.x, p.y + editor.lineHeight / 2)
    }

    private fun start() {
        if (!timer.isRunning) timer.start()
    }

    private fun hasRoom() = particles.size < MAX_PARTICLES

    private fun add(p: Particle) {
        particles += p
    }

    private fun sized(base: Float) = base * theme().sizeScale

    private fun spark(
        x: Double, y: Double, vx: Double, vy: Double, gravity: Double, life: Int, color: Color, size: Float,
    ) = add(Particle(x, y, vx, vy, gravity, life, color, sized(size), rect = theme().pixel))

    fun blip(offset: Int, label: String?, power: Int = 0) {
        if (!hasRoom()) return
        val p = caretPoint(offset)
        val color = theme().color(random)
        val droop = theme().droop
        repeat(6 + power * 2) {
            val vx = random.nextGaussian() * (if (droop) 0.4 else 1.2 + power * 0.15)
            // Sad theme: tears drip down and linger instead of sparking upward.
            val vy = if (droop) random.nextDouble() * 0.8 + 0.2 else -random.nextDouble() * (2.5 + power * 0.3) - 0.5
            spark(
                p.x.toDouble(), p.y.toDouble(), vx, vy,
                if (droop) 0.07 else 0.12, (if (droop) 34 else 22) + random.nextInt(10), color, 2f + random.nextFloat() * 2f,
            )
        }
        if (label != null) {
            add(Particle(p.x.toDouble(), p.y - 4.0, random.nextGaussian() * 0.4, -1.6, 0.0, 34, color, 0f, label))
        }
        start()
    }

    fun boom(offset: Int, power: Int = 0) {
        if (!hasRoom()) return
        val p = caretPoint(offset)
        val t = theme()
        val warm = listOf(Color(255, 80, 40), Color(255, 160, 30), Color(255, 220, 80))
        repeat(16 + power * 2) {
            val angle = random.nextDouble() * Math.PI * 2
            val speed = 1.5 + random.nextDouble() * 3.5
            val c = if (t == Theme.NEON) warm[random.nextInt(warm.size)] else t.color(random)
            spark(p.x.toDouble(), p.y.toDouble(), Math.cos(angle) * speed, Math.sin(angle) * speed, 0.05, 24 + random.nextInt(14), c, 3f + random.nextFloat() * 3f)
        }
        add(Particle(p.x.toDouble(), p.y.toDouble(), 0.0, 0.0, 0.0, 16, Color(255, 140, 60), 0f, ring = true))
        start()
    }

    fun newline(offset: Int) {
        if (!hasRoom()) return
        val p = caretPoint(offset)
        val color = if (theme() == Theme.NEON) Color(80, 230, 255) else theme().color(random)
        repeat(10) {
            spark(p.x.toDouble(), p.y.toDouble(), 2.5 + random.nextDouble() * 4, random.nextGaussian() * 0.6, 0.02, 18 + random.nextInt(10), color, 2f + random.nextFloat() * 2f)
        }
        start()
    }

    /** Upward sparkle at [offset]; used when a line or block is completed. */
    fun sparkle(offset: Int, count: Int, ring: Boolean = false) {
        if (!hasRoom()) return
        val p = caretPoint(offset)
        val gold = listOf(Color(255, 215, 64), Color(255, 245, 160), Color.WHITE)
        repeat(count) {
            val angle = (if (theme().droop) Math.PI / 2 else -Math.PI / 2) + random.nextGaussian() * 0.9
            val speed = 1.5 + random.nextDouble() * 3.5
            val c = if (theme() == Theme.NEON) gold[random.nextInt(gold.size)] else theme().color(random)
            spark(p.x.toDouble(), p.y.toDouble(), Math.cos(angle) * speed, Math.sin(angle) * speed, 0.1, 28 + random.nextInt(14), c, 2f + random.nextFloat() * 3f)
        }
        if (ring) add(Particle(p.x.toDouble(), p.y.toDouble(), 0.0, 0.0, 0.0, 18, Color(255, 215, 64), 0f, ring = true))
        start()
    }

    /** Short-lived glow dot following the caret. */
    fun trail(contentPoint: Point) {
        if (particles.size > MAX_PARTICLES / 2) return
        val c = theme().color(random)
        add(Particle(contentPoint.x.toDouble(), contentPoint.y.toDouble(), 0.0, 0.0, 0.0, 14, c, sized(6f), rect = theme().pixel))
        start()
    }

    fun fireworks() {
        val r = editor.contentComponent.visibleRect
        repeat(6) { i ->
            bursts += Burst(
                i * 9,
                r.x + r.width * (0.15 + random.nextDouble() * 0.7),
                r.y + r.height * (0.1 + random.nextDouble() * 0.4),
            )
        }
        start()
    }

    fun confetti() {
        val r = editor.contentComponent.visibleRect
        repeat(90) {
            add(
                Particle(
                    r.x + random.nextDouble() * r.width, r.y - random.nextDouble() * 60,
                    random.nextGaussian() * 0.8, 1.5 + random.nextDouble() * 2.5,
                    0.02, 70 + random.nextInt(50),
                    if (theme().droop) theme().color(random) else Color.getHSBColor(random.nextFloat(), 0.8f, 1f),
                    sized(if (theme().droop) 3f else 5f + random.nextFloat() * 4f), rect = !theme().droop,
                ),
            )
        }
        start()
    }

    fun flash(color: Color) {
        flashColor = color
        flashFrames = FLASH_FRAMES
        start()
    }

    fun shake(magnitude: Int) {
        if (magnitude <= 0) return
        shakeMagnitude = magnitude
        shakeFrames = 6
        start()
    }

    fun combo(count: Int, windowMs: Long) {
        comboCount = count
        comboWindowMs = windowMs
        comboExpireMs = System.currentTimeMillis() + windowMs
        start()
    }

    private fun explodeBurst(b: Burst) {
        val base = theme().color(random)
        repeat(34) {
            val angle = random.nextDouble() * Math.PI * 2
            val speed = 1.0 + random.nextDouble() * 4.5
            val c = if (random.nextInt(4) == 0) Color.WHITE else base
            spark(b.x, b.y, Math.cos(angle) * speed, Math.sin(angle) * speed, 0.07, 45 + random.nextInt(35), c, 2.5f + random.nextFloat() * 2f)
        }
    }

    private fun tick() {
        setBounds(editor.contentComponent.visibleRect)

        val due = bursts.filter { --it.delay <= 0 }
        due.forEach { explodeBurst(it) }
        bursts.removeAll(due.toSet())

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx; p.y += p.vy
            p.vy += p.gravity
            p.vx *= 0.98
            if (++p.age >= p.maxLife) it.remove()
        }

        if (comboCount > 0 && System.currentTimeMillis() > comboExpireMs) comboCount = 0
        if (flashFrames > 0) flashFrames--

        stepShake()
        repaint()
        if (particles.isEmpty() && bursts.isEmpty() && shakeFrames == 0 && comboCount == 0 && flashFrames == 0) timer.stop()
    }

    private fun stepShake() {
        if (shakeFrames == 0) return
        shakeFrames--
        val sm = editor.scrollingModel
        val baseX = sm.horizontalScrollOffset - appliedDx
        val baseY = sm.verticalScrollOffset - appliedDy
        val falloff = shakeFrames / 6.0
        appliedDx = if (shakeFrames == 0) 0 else (random.nextGaussian() * shakeMagnitude * falloff).toInt()
        appliedDy = if (shakeFrames == 0) 0 else (random.nextGaussian() * shakeMagnitude * falloff).toInt()
        sm.disableAnimation()
        sm.scrollHorizontally((baseX + appliedDx).coerceAtLeast(0))
        sm.scrollVertically((baseY + appliedDy).coerceAtLeast(0))
        sm.enableAnimation()
    }

    override fun paintComponent(g: Graphics) {
        if (particles.isEmpty() && comboCount == 0 && flashFrames == 0) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val labelFont: Font = editor.contentComponent.font.deriveFont(Font.BOLD)

            if (flashFrames > 0) {
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f * flashFrames / FLASH_FRAMES)
                g2.color = flashColor
                g2.fillRect(0, 0, width, height)
            }

            g2.translate(-x, -y)
            for (p in particles) {
                val alpha = (1f - p.age / p.maxLife.toFloat()).coerceIn(0f, 1f)
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
                g2.color = p.color
                when {
                    p.label != null -> {
                        g2.font = labelFont
                        g2.drawString(p.label, p.x.toFloat(), p.y.toFloat())
                    }
                    p.ring -> {
                        val r = p.age * 3.0
                        g2.stroke = BasicStroke(2f)
                        g2.drawOval((p.x - r).toInt(), (p.y - r).toInt(), (r * 2).toInt(), (r * 2).toInt())
                    }
                    p.rect -> g2.fillRect((p.x - p.size / 2).toInt(), (p.y - p.size / 2).toInt(), p.size.toInt(), p.size.toInt())
                    else -> g2.fillOval((p.x - p.size / 2).toInt(), (p.y - p.size / 2).toInt(), p.size.toInt(), p.size.toInt())
                }
            }
            g2.translate(x, y)

            if (comboCount > 0) paintCombo(g2, labelFont)
        } finally {
            g2.dispose()
        }
    }

    private fun paintCombo(g2: Graphics2D, base: Font) {
        val remaining = ((comboExpireMs - System.currentTimeMillis()) / comboWindowMs.toDouble()).coerceIn(0.0, 1.0)
        val size = 16f + minOf(comboCount, 100) / 5f
        val color = theme().color(Random(comboCount / 10L))
        g2.composite = AlphaComposite.SrcOver
        g2.font = base.deriveFont(Font.BOLD, size)
        val text = "x$comboCount COMBO"
        val w = g2.fontMetrics.stringWidth(text)
        val right = width - 28
        g2.color = color
        g2.drawString(text, right - w, 44)
        g2.fillRect(right - w, 52, (w * remaining).toInt(), 3)
    }

    private companion object {
        const val MAX_PARTICLES = 400
        const val FLASH_FRAMES = 14
    }
}
