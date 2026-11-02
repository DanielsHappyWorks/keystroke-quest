package com.keystrokequest

import java.awt.Rectangle
import java.util.Random
import kotlin.math.abs

const val GROUND_KEY = "ground"

enum class CatState { WALK, SIT, GROOM, SLEEP, JUMP, FALL, PETTED }

/** A horizontal ledge a cat can stand on: the top edge of an IDE panel, in cat-layer coordinates. */
class Platform(val key: Any, val x1: Int, val x2: Int, val y: Int)

/** One cat on screen: position, behaviour state machine and physics. Pure logic, no IntelliJ APIs. */
class CatActor(val data: CatData, val look: CatLook) {
    private val rnd = Random()

    var x = 0.0
    var y = 0.0
    var vy = 0.0
    var dir = 1
    var state = CatState.SIT
    var anim = 0
    var plat: Platform? = null
    var lastPetMs = 0L
    var lastRect = Rectangle()

    private var t = 0
    private var dur = 60

    private var jx0 = 0.0
    private var jy0 = 0.0
    private var jx1 = 0.0
    private var jy1 = 0.0
    private var jt = 0
    private var jFrames = 1
    private var jArc = 0.0
    private var jTarget: Platform? = null

    fun place(p: Platform, px: Double) {
        plat = p
        x = px
        y = p.y.toDouble()
        enter(CatState.SIT)
    }

    fun drop(px: Double, fromY: Double) {
        plat = null
        x = px
        y = fromY
        vy = 0.0
        state = CatState.FALL
    }

    fun hitRect(): Rectangle {
        val s = CatSprite.SCALE * look.scale
        val w = (54 * s).toInt()
        val h = (46 * s).toInt()
        return Rectangle((x - w / 2).toInt(), (y - h).toInt(), w, h)
    }

    /** Area to repaint: the cat plus room for hearts, a swishing tail, and zzz above it. */
    fun dirtyRect(): Rectangle = hitRect().apply { grow(14, 28) }

    val airborne get() = state == CatState.JUMP || state == CatState.FALL

    fun pet() {
        if (!airborne) enter(CatState.PETTED)
    }

    private fun enter(s: CatState) {
        state = s
        t = 0
        dur = when (s) {
            CatState.WALK -> 90 + rnd.nextInt(150)
            CatState.SIT -> 60 + rnd.nextInt(120)
            CatState.GROOM -> 60 + rnd.nextInt(60)
            CatState.SLEEP -> 300 + rnd.nextInt(450)
            CatState.PETTED -> 50
            else -> 30
        }
        if (s == CatState.WALK) dir = if (rnd.nextBoolean()) 1 else -1
    }

    fun update(platforms: List<Platform>) {
        anim++
        t++
        when (state) {
            CatState.WALK -> {
                val p = plat ?: return
                x += dir * 1.1 * look.energy
                if (x < p.x1 + 14) {
                    x = p.x1 + 14.0
                    if (rnd.nextInt(5) < 2 && tryJump(platforms)) return
                    dir = 1
                } else if (x > p.x2 - 14) {
                    x = p.x2 - 14.0
                    if (rnd.nextInt(5) < 2 && tryJump(platforms)) return
                    dir = -1
                }
                if (rnd.nextInt(400) == 0) dir = -dir
                if (t >= dur) chooseNext(platforms)
            }
            CatState.SIT, CatState.GROOM, CatState.PETTED, CatState.SLEEP -> if (t >= dur) chooseNext(platforms)
            CatState.JUMP -> {
                jt++
                val f = (jt / jFrames.toDouble()).coerceAtMost(1.0)
                val oldY = y
                x = jx0 + (jx1 - jx0) * f
                y = jy0 + (jy1 - jy0) * f - 4 * jArc * f * (1 - f)
                vy = y - oldY
                if (f >= 1.0) {
                    plat = jTarget
                    vy = 0.0
                    enter(CatState.SIT)
                }
            }
            CatState.FALL -> fall(platforms)
        }
    }

    private fun fall(platforms: List<Platform>) {
        val prev = y
        vy += 0.7
        y += vy
        val landing = platforms
            .filter { x >= it.x1 && x <= it.x2 && it.y >= prev - 2 && it.y <= y }
            .minByOrNull { it.y }
            ?: platforms.firstOrNull { it.key == GROUND_KEY }?.takeIf { y >= it.y }
        if (landing != null) {
            plat = landing
            y = landing.y.toDouble()
            x = x.coerceIn(landing.x1 + 14.0, landing.x2 - 14.0)
            vy = 0.0
            enter(CatState.SIT)
        }
    }

    private fun chooseNext(platforms: List<Platform>) {
        val canJump = jumpOptions(platforms).isNotEmpty()
        val weights = listOf(
            CatState.WALK to 40.0,
            CatState.SIT to 22.0,
            CatState.GROOM to 8.0,
            CatState.SLEEP to 18.0 / look.energy,
            CatState.JUMP to if (canJump) 22.0 * look.energy else 0.0,
        )
        var roll = rnd.nextDouble() * weights.sumOf { it.second }
        var pick = CatState.SIT
        for ((s, w) in weights) {
            roll -= w
            if (roll <= 0) {
                pick = s
                break
            }
        }
        if (pick == CatState.JUMP) {
            if (!tryJump(platforms)) enter(CatState.SIT)
        } else {
            enter(pick)
        }
    }

    private fun jumpOptions(platforms: List<Platform>): List<Pair<Platform, Double>> {
        val p = plat ?: return emptyList()
        return platforms.filter { it.key != p.key && it.x2 - it.x1 > 40 && it.y > 30 }.mapNotNull { q ->
            val tx = x.coerceIn(q.x1 + 14.0, q.x2 - 14.0)
            val rise = p.y - q.y
            if (abs(tx - x) <= 320 && rise <= 560 && rise >= -900) q to tx else null
        }
    }

    private fun tryJump(platforms: List<Platform>): Boolean {
        val p = plat ?: return false
        val options = jumpOptions(platforms)
        if (options.isEmpty()) return false
        val (q, tx) = options[rnd.nextInt(options.size)]
        val dx = abs(tx - x)
        jx0 = x
        jy0 = y
        jx1 = tx
        jy1 = q.y.toDouble()
        jt = 0
        jFrames = (18 + dx / 10).toInt()
        jArc = maxOf(28.0, 0.3 * dx, (p.y - q.y) + 24.0)
        jTarget = q
        dir = if (tx >= x) 1 else -1
        plat = null
        state = CatState.JUMP
        return true
    }
}
