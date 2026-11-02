package com.keystrokequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.util.Random
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager

/**
 * Per-window home for the player's cats. A transparent layer is added to the IDE frame's layered
 * pane; cats stand on the top edges of the visible panels, wander, jump between panels, groom and nap.
 * The layer only catches mouse events over a cat, so the IDE underneath stays fully clickable.
 */
@Service(Service.Level.PROJECT)
class CatWorld(private val project: Project) : Disposable {
    private class Heart(var x: Double, var y: Double, val vx: Double, var life: Int, val size: Double)

    private val rnd = Random()
    private val cats = mutableListOf<CatActor>()
    private val hearts = mutableListOf<Heart>()
    private val seen = HashSet<Long>()
    private var platforms: List<Platform> = emptyList()
    private var layer: CatLayer? = null
    private var pane: JLayeredPane? = null
    private var firstSync = true
    private var started = false
    private var frame = 0
    private var heartRect = Rectangle()

    private val timer = Timer(33) { tick() }
    private val resizer = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            pane?.let { layer?.setBounds(0, 0, it.width, it.height) }
        }
    }

    /** Hooks into level-ups and settings changes. Call on the EDT. */
    fun start() {
        if (started) return
        started = true
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(RcListener.TOPIC, object : RcListener {
            override fun changed(leveledUp: Boolean) {
                ApplicationManager.getApplication().invokeLater { sync() }
            }
        })
        sync()
    }

    /** Reconciles what is on screen with the saved cats and the settings. */
    private fun sync() {
        if (project.isDisposed) return
        val rc = RcService.getInstance()
        val s = rc.state
        rc.grantCats()
        if (!s.showCats || s.reducedEffects) {
            detach()
            return
        }
        val layeredPane = layeredPane()
        if (layeredPane == null) {
            // The frame can lag behind the startup activity; try again shortly.
            Timer(1000) { sync() }.apply { isRepeats = false; start() }
            return
        }
        attach(layeredPane)

        val wanted = s.cats.sortedByDescending { it.level }.take(s.maxCatsShown.coerceAtLeast(1))
        cats.removeAll { a ->
            val gone = wanted.none { it.seed == a.data.seed }
            if (gone) layer?.repaint(a.lastRect)
            gone
        }
        refreshPlatforms()
        for (d in wanted) {
            if (cats.any { it.data.seed == d.seed }) continue
            val actor = CatActor(d, CatLook(d.seed))
            val arriving = !firstSync && d.seed !in seen
            val ground = platforms.first { it.key == GROUND_KEY }
            val px = ground.x1 + 40 + rnd.nextDouble() * (ground.x2 - ground.x1 - 80).coerceAtLeast(1)
            if (arriving) {
                actor.drop(px, -40.0)
                announce(actor)
            } else {
                actor.place(ground, px)
            }
            cats += actor
        }
        seen.addAll(s.cats.map { it.seed })
        firstSync = false
        if (!timer.isRunning) timer.start()
    }

    private fun announce(a: CatActor) {
        val s = RcService.getInstance().state
        service<TypingService>().toast("A new cat joined you!", "${a.data.name}: ${a.look.description}. Click to pet (+1 XP).", project)
        SoundEngine.meow(if (s.sound) s.volume else 0, Theme.of(s.theme))
    }

    private fun layeredPane(): JLayeredPane? {
        val frame = WindowManager.getInstance().getIdeFrame(project) ?: return null
        return (frame.component as? JComponent)?.rootPane?.layeredPane
    }

    private fun attach(p: JLayeredPane) {
        if (layer != null && pane === p) return
        detach()
        val l = CatLayer()
        l.setBounds(0, 0, p.width, p.height)
        p.add(l, JLayeredPane.PALETTE_LAYER as Any)
        p.addComponentListener(resizer)
        pane = p
        layer = l
    }

    private fun detach() {
        timer.stop()
        val l = layer ?: return
        pane?.removeComponentListener(resizer)
        pane?.remove(l)
        pane?.repaint()
        layer = null
        pane = null
        cats.clear()
        hearts.clear()
    }

    private fun refreshPlatforms() {
        val l = layer ?: return
        val window = SwingUtilities.getWindowAncestor(l)
        val list = ArrayList<Platform>()
        fun add(key: Any, c: JComponent?) {
            if (c == null || !c.isShowing || c.width < 80 || SwingUtilities.getWindowAncestor(c) !== window) return
            val p = SwingUtilities.convertPoint(c, 0, 0, l)
            if (p.y > 40 && p.y < l.height) list += Platform(key, p.x.coerceAtLeast(0), (p.x + c.width).coerceAtMost(l.width), p.y)
        }
        val twm = ToolWindowManager.getInstance(project)
        for (id in twm.toolWindowIds) {
            val tw = twm.getToolWindow(id) ?: continue
            if (tw.isVisible) add(id, tw.component)
        }
        add("editor", FileEditorManager.getInstance(project).selectedEditor?.component)

        val status = WindowManager.getInstance().getStatusBar(project)?.component
        val groundY = if (status != null && status.isShowing) SwingUtilities.convertPoint(status, 0, 0, l).y else l.height - 24
        val ground = Platform(GROUND_KEY, 0, l.width, groundY.coerceAtLeast(60))
        list.removeAll { it.y >= ground.y }
        list += ground
        platforms = list

        // Keep cats glued to panels that moved, and let those whose panel closed fall.
        for (a in cats) {
            val old = a.plat ?: continue
            val now = list.firstOrNull { it.key == old.key }
            if (now == null) {
                a.plat = null
                a.drop(a.x, a.y)
            } else {
                a.plat = now
                a.y = now.y.toDouble()
                a.x = a.x.coerceIn(now.x1 + 14.0, now.x2 - 14.0)
            }
        }
    }

    private fun tick() {
        val l = layer ?: return
        val window = SwingUtilities.getWindowAncestor(l)
        // Frozen while the IDE is in the background: no CPU, and nothing moves behind your back.
        if (window == null || !window.isActive || !l.isShowing) return
        frame++
        if (frame % 30 == 1) refreshPlatforms()
        for (a in cats) {
            a.update(platforms)
            val r = a.dirtyRect()
            l.repaint(a.lastRect.union(r))
            a.lastRect = r
        }
        if (hearts.isNotEmpty()) {
            val it = hearts.iterator()
            var box: Rectangle? = null
            while (it.hasNext()) {
                val h = it.next()
                h.x += h.vx
                h.y -= 1.1
                if (--h.life <= 0) it.remove()
                val r = Rectangle((h.x - h.size - 2).toInt(), (h.y - h.size - 2).toInt(), (h.size * 2 + 4).toInt(), (h.size * 2 + 4).toInt())
                box = box?.union(r) ?: r
            }
            val dirty = box?.let { heartRect.union(it) } ?: heartRect
            l.repaint(dirty)
            heartRect = box ?: Rectangle()
        }
    }

    private fun catAt(x: Int, y: Int): CatActor? = cats.lastOrNull { it.hitRect().contains(x, y) }

    /** A click only counts as a pet (hearts, meow, +1 XP) once per cooldown; clicks in between do nothing. */
    private fun pet(a: CatActor) {
        val now = System.currentTimeMillis()
        if (now - a.lastPetMs < PET_COOLDOWN_MS) return
        a.lastPetMs = now
        val s = RcService.getInstance().state
        a.pet()
        a.data.pets++
        val sc = CatSprite.SCALE * a.look.scale
        repeat(6) {
            hearts += Heart(a.x + a.dir * 12 * sc + rnd.nextGaussian() * 8, a.y - 44 * sc + rnd.nextGaussian() * 4, rnd.nextGaussian() * 0.4, 38 + rnd.nextInt(16), 4.5 + rnd.nextDouble() * 3)
        }
        SoundEngine.meow(if (s.sound) s.volume / 2 else 0, Theme.of(s.theme))
        Rewards.settle(project, Metric.CHARS, 0, 1)
    }

    private fun drawAll(g0: Graphics2D) {
        g0.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        for (a in cats) CatSprite.draw(g0, a)
        for (h in hearts) {
            val alpha = (h.life / 20.0).coerceIn(0.0, 1.0)
            g0.color = Color(255, 90, 130, (alpha * 235).toInt())
            val s = h.size
            val p = Path2D.Double()
            p.moveTo(h.x, h.y + s * 0.9)
            p.curveTo(h.x - s * 1.6, h.y - s * 0.2, h.x - s * 0.7, h.y - s * 1.3, h.x, h.y - s * 0.4)
            p.curveTo(h.x + s * 0.7, h.y - s * 1.3, h.x + s * 1.6, h.y - s * 0.2, h.x, h.y + s * 0.9)
            g0.fill(p)
            g0.color = Color(255, 255, 255, (alpha * 120).toInt())
            g0.fill(Ellipse2D.Double(h.x - s * 0.7, h.y - s * 0.6, s * 0.45, s * 0.35))
        }
    }

    override fun dispose() {
        detach()
    }

    private inner class CatLayer : JComponent() {
        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            ToolTipManager.sharedInstance().registerComponent(this)
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    catAt(e.x, e.y)?.let { pet(it) }
                }
            })
        }

        // Only cats catch the mouse; everywhere else clicks fall through to the IDE.
        override fun contains(x: Int, y: Int) = catAt(x, y) != null

        override fun getToolTipText(e: MouseEvent): String? =
            catAt(e.x, e.y)?.let { "${it.data.name}: ${it.look.description} (level ${it.data.level}, ${it.data.pets} pets)" }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                drawAll(g2)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PET_COOLDOWN_MS = 1000L
    }
}
