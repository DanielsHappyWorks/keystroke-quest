package com.keystrokequest

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.CubicCurve2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.PI
import kotlin.math.sin

/** Draws cats as vector shapes (no image assets). Sprite space: feet at (0,0), cat faces +x, y grows downwards. */
object CatSprite {
    const val SCALE = 1.25

    private enum class Mood { OPEN, SLEEP, HAPPY }

    fun draw(g0: Graphics2D, a: CatActor) {
        val g = g0.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val s = SCALE * a.look.scale
            g.translate(a.x, a.y)
            g.color = Color(0, 0, 0, 45)
            g.fill(Ellipse2D.Double(-18.0 * s, -2.5 * s, 36.0 * s, 5.0 * s))
            g.scale(a.dir * s, s)
            when (a.state) {
                CatState.WALK -> walk(g, a)
                CatState.SIT -> sit(g, a, groom = false, happy = false)
                CatState.PETTED -> sit(g, a, groom = false, happy = true)
                CatState.GROOM -> sit(g, a, groom = true, happy = false)
                CatState.SLEEP -> sleep(g, a)
                CatState.JUMP, CatState.FALL -> air(g, a)
            }
        } finally {
            g.dispose()
        }
        if (a.state == CatState.SLEEP) zzz(g0, a)
    }

    private fun zzz(g0: Graphics2D, a: CatActor) {
        val g = g0.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val s = SCALE * a.look.scale
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
            for (i in 0..1) {
                val p = ((a.anim + i * 30) % 90) / 90.0
                g.color = Color(200, 210, 255, ((1 - p) * 220).toInt())
                g.drawString("z", (a.x + a.dir * (20 * s + i * 6 + p * 8)).toFloat(), (a.y - 26 * s - p * 24).toFloat())
            }
        } finally {
            g.dispose()
        }
    }

    private fun stroke(g: Graphics2D, w: Float) {
        g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }

    private fun tail(g: Graphics2D, look: CatLook, c: CubicCurve2D) {
        stroke(g, 4.5f)
        g.color = look.fur
        g.draw(c)
    }

    private fun leg(g: Graphics2D, look: CatLook, x0: Double, y0: Double, x1: Double, y1: Double, color: Color) {
        stroke(g, 4f)
        g.color = color
        g.draw(Line2D.Double(x0, y0, x1, y1))
        if (look.pattern == Pattern.TUXEDO || look.pattern == Pattern.SOCKS) {
            g.color = look.accent
            g.fill(Ellipse2D.Double(x1 - 2.8, y1 - 3.2, 5.6, 4.2))
        }
    }

    private fun head(g: Graphics2D, look: CatLook, hx: Double, hy: Double, mood: Mood) {
        val pink = Color(240, 150, 160)
        g.color = look.fur
        g.fill(poly(hx - 7.5, hy - 3, hx - 5.5, hy - 13, hx - 0.5, hy - 6.5))
        g.fill(poly(hx + 0.5, hy - 6.5, hx + 5.5, hy - 13, hx + 7.5, hy - 3))
        g.color = pink
        g.fill(poly(hx - 6.0, hy - 5, hx - 5.2, hy - 10, hx - 2.5, hy - 6.5))
        g.fill(poly(hx + 2.5, hy - 6.5, hx + 5.2, hy - 10, hx + 6.0, hy - 5))
        g.color = look.fur
        val face = Ellipse2D.Double(hx - 8, hy - 7.5, 16.0, 15.0)
        g.fill(face)

        when (look.pattern) {
            Pattern.TABBY -> {
                stroke(g, 1.8f)
                g.color = look.accent
                for (dx in doubleArrayOf(-3.0, 0.0, 3.0)) g.draw(Line2D.Double(hx + dx, hy - 7.2, hx + dx * 0.8, hy - 4.2))
            }
            Pattern.TUXEDO -> {
                g.color = look.accent
                g.fill(Ellipse2D.Double(hx + 1.5, hy + 0.2, 8.0, 6.5))
            }
            Pattern.SPOTTED -> {
                g.color = look.accent
                g.fill(Ellipse2D.Double(hx - 6.5, hy - 6.5, 6.0, 5.0))
            }
            else -> Unit
        }

        val eyeY = hy - 1.5
        for (ex in doubleArrayOf(hx + 0.5, hx + 5.5)) {
            when (mood) {
                Mood.OPEN -> {
                    g.color = look.eyes
                    g.fill(Ellipse2D.Double(ex - 2.2, eyeY - 2.4, 4.4, 4.8))
                    g.color = Color(20, 20, 24)
                    g.fill(Ellipse2D.Double(ex - 0.7, eyeY - 2.2, 1.4, 4.4))
                }
                Mood.SLEEP -> {
                    stroke(g, 1.3f)
                    g.color = Color(30, 30, 36)
                    g.draw(Arc2D.Double(ex - 2.2, eyeY - 1.5, 4.4, 3.0, 200.0, 140.0, Arc2D.OPEN))
                }
                Mood.HAPPY -> {
                    stroke(g, 1.4f)
                    g.color = Color(30, 30, 36)
                    g.draw(Arc2D.Double(ex - 2.2, eyeY - 1.0, 4.4, 3.2, 20.0, 140.0, Arc2D.OPEN))
                }
            }
        }
        g.color = pink
        g.fill(Ellipse2D.Double(hx + 7.2, hy + 0.8, 2.6, 2.0))
        stroke(g, 0.7f)
        g.color = Color(200, 200, 205, 190)
        g.draw(Line2D.Double(hx + 8.0, hy + 2.5, hx + 15.0, hy + 0.5))
        g.draw(Line2D.Double(hx + 8.0, hy + 3.0, hx + 15.0, hy + 4.5))
        if (mood == Mood.HAPPY) {
            g.color = Color(255, 120, 150, 90)
            g.fill(Ellipse2D.Double(hx + 1.0, hy + 2.0, 4.5, 3.0))
        }
    }

    private fun poly(vararg v: Double): Shape {
        val p = Path2D.Double()
        p.moveTo(v[0], v[1])
        for (i in 2 until v.size step 2) p.lineTo(v[i], v[i + 1])
        p.closePath()
        return p
    }

    /** Fills the coat pattern over the body, clipped to the body shape. */
    private fun bodyPattern(g: Graphics2D, look: CatLook, body: Shape, box: Rectangle2D) {
        val old = g.clip
        g.clip(body)
        g.color = look.accent
        when (look.pattern) {
            Pattern.TABBY -> {
                stroke(g, 2.2f)
                for (i in 0..3) {
                    val x = box.x + 6 + i * (box.width - 12) / 3
                    g.draw(Line2D.Double(x, box.y, x + 1.5, box.y + box.height * 0.55))
                }
            }
            Pattern.TUXEDO -> g.fill(Ellipse2D.Double(box.x + box.width * 0.2, box.y + box.height * 0.55, box.width * 0.6, box.height * 0.8))
            Pattern.SPOTTED -> for (sp in look.spots) {
                g.fill(Ellipse2D.Double(box.x + sp.fx * box.width, box.y + sp.fy * box.height, sp.size, sp.size * 0.8))
            }
            else -> Unit
        }
        g.clip = old
    }

    /**
     * Collar, bow or scarf. [rot] turns the neck band (not the hanging scarf end) so it wraps across the
     * neck: 0 suits a neck leaning forward (walking, jumping), a negative angle suits an upright neck (sitting).
     */
    private fun accessory(g: Graphics2D, look: CatLook, nx: Double, ny: Double, hx: Double, hy: Double, rot: Double = 0.0) {
        val saved = g.transform
        when (look.accessory) {
            1 -> {
                g.rotate(rot, nx, ny)
                stroke(g, 2.6f)
                g.color = look.accessoryColor
                g.draw(Line2D.Double(nx - 2.5, ny - 4.5, nx + 1.5, ny + 4.5))
                g.color = Color(250, 205, 60)
                g.fill(Ellipse2D.Double(nx - 0.5, ny + 3.0, 4.0, 4.0))
                g.transform = saved
            }
            2 -> {
                g.color = look.accessoryColor
                g.fill(poly(hx - 5.5, hy - 7.5, hx - 11.0, hy - 11.0, hx - 11.0, hy - 4.0))
                g.fill(poly(hx - 5.5, hy - 7.5, hx - 0.5, hy - 12.0, hx - 0.5, hy - 5.0))
                g.fill(Ellipse2D.Double(hx - 7.0, hy - 9.0, 3.4, 3.4))
            }
            3 -> {
                g.rotate(rot, nx, ny)
                stroke(g, 3.4f)
                g.color = look.accessoryColor
                g.draw(Line2D.Double(nx - 3.0, ny - 4.0, nx + 2.0, ny + 5.0))
                g.transform = saved
                stroke(g, 3f)
                g.draw(Line2D.Double(nx - 1.0, ny + 2.0, nx - 3.5, ny + 10.0))
            }
        }
    }

    private fun walk(g: Graphics2D, a: CatActor) {
        val look = a.look
        val phase = a.anim * 0.45
        val hips = doubleArrayOf(-11.0, -5.0, 9.0, 14.0)
        val offsets = doubleArrayOf(0.0, PI, PI, 0.0)
        val far = CatLook.darker(look.fur, 0.78)
        tail(g, look, CubicCurve2D.Double(-15.0, -18.0, -30.0, -14.0, -33.0, -30.0 + sin(phase * 0.5) * 2, -27.0, -39.0))
        for (i in intArrayOf(1, 3)) leg(g, look, hips[i], -12.0, hips[i] + sin(phase + offsets[i]) * 5, -1.0, far)
        val body = RoundRectangle2D.Double(-17.0, -27.0, 33.0, 17.0, 16.0, 16.0)
        g.color = look.fur
        g.fill(body)
        bodyPattern(g, look, body, body.bounds2D)
        for (i in intArrayOf(0, 2)) leg(g, look, hips[i], -12.0, hips[i] + sin(phase + offsets[i]) * 5, -1.0, look.fur)
        head(g, look, 20.0, -29.0, Mood.OPEN)
        accessory(g, look, 14.0, -26.0, 20.0, -29.0)
    }

    private fun sit(g: Graphics2D, a: CatActor, groom: Boolean, happy: Boolean) {
        val look = a.look
        val wag = if (happy) sin(a.anim * 0.6) * 6 else sin(a.anim * 0.1) * 3
        val far = CatLook.darker(look.fur, 0.78)
        // Resting tail: lies along the ground behind the cat with a small upward flick at the tip.
        tail(g, look, CubicCurve2D.Double(-11.0, -3.0, -20.0, 0.0, -29.0, 0.0, -33.0 + wag * 0.3, -7.0 - wag * 0.4))
        leg(g, look, 7.0, -12.0, 7.0, -1.0, far)
        val body = Area(Ellipse2D.Double(-15.0, -19.0, 23.0, 19.0)).apply { add(Area(Ellipse2D.Double(0.0, -35.0, 15.0, 33.0))) }
        g.color = look.fur
        g.fill(body)
        bodyPattern(g, look, body, Rectangle2D.Double(-15.0, -35.0, 30.0, 35.0))
        if (groom) {
            val up = (a.anim / 6) % 2 == 0
            leg(g, look, 11.0, -14.0, 17.0, if (up) -36.0 else -33.0, look.fur)
        } else {
            leg(g, look, 11.0, -12.0, 11.0, -1.0, look.fur)
        }
        head(g, look, 9.0, -40.0, if (happy) Mood.HAPPY else Mood.OPEN)
        accessory(g, look, 8.0, -32.0, 9.0, -40.0, Math.toRadians(-52.0))
    }

    private fun sleep(g: Graphics2D, a: CatActor) {
        val look = a.look
        g.scale(1.0, 1 + 0.03 * sin(a.anim * 0.08))
        val body = Ellipse2D.Double(-19.0, -18.0, 38.0, 18.0)
        g.color = look.fur
        g.fill(body)
        bodyPattern(g, look, body, body.bounds2D)
        tail(g, look, CubicCurve2D.Double(-17.0, -6.0, -27.0, 4.0, 4.0, 7.0, 17.0, -1.0))
        head(g, look, 19.0, -9.0, Mood.SLEEP)
        accessory(g, look, 12.0, -12.0, 19.0, -9.0)
    }

    private fun air(g: Graphics2D, a: CatActor) {
        val look = a.look
        g.rotate((a.vy / 6).coerceIn(-1.0, 1.0) * 0.4, 0.0, -14.0)
        val far = CatLook.darker(look.fur, 0.78)
        tail(g, look, CubicCurve2D.Double(-15.0, -20.0, -26.0, -22.0, -34.0, -20.0, -40.0, -24.0))
        leg(g, look, 9.0, -12.0, 20.0, -6.0, far)
        leg(g, look, -8.0, -12.0, -20.0, -5.0, far)
        val body = RoundRectangle2D.Double(-17.0, -27.0, 33.0, 17.0, 16.0, 16.0)
        g.color = look.fur
        g.fill(body)
        bodyPattern(g, look, body, body.bounds2D)
        leg(g, look, 13.0, -12.0, 24.0, -9.0, look.fur)
        leg(g, look, -12.0, -12.0, -24.0, -8.0, look.fur)
        head(g, look, 20.0, -29.0, Mood.OPEN)
        accessory(g, look, 14.0, -26.0, 20.0, -29.0)
    }
}
