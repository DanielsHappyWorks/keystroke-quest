package com.keystrokequest

import java.util.Random
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthesizes every sound at runtime through javax.sound, so the plugin ships no audio assets and
 * needs no native helper. Each [Theme] has its own voicing:
 *  - Neon: sliding synth blips, noisy sweep booms, bright two-note chimes.
 *  - Retro: pentatonic 8-bit blips, sample-and-hold noise booms, bit-crushed arpeggios.
 *  - Minimal: soft low sine ticks, noise-free thumps, gentle chimes.
 *  - Sad: drooping low sines, minor descending chimes, a sad-trombone level-up.
 * Work is done on a single daemon thread; excess requests are dropped.
 */
object SoundEngine {
    private const val RATE = 22050f
    private val format = AudioFormat(RATE, 16, 1, true, false)
    private val random = Random()

    private val pool = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8),
        { r -> Thread(r, "rc-sound").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )

    // C major pentatonic across a few octaves, for the retro blip.
    private val PENTATONIC = listOf(0, 2, 4, 7, 9).flatMap { n -> (4..6).map { o -> 261.63 * 2.0.pow((n + (o - 4) * 12) / 12.0) } }

    fun blip(pitchHz: Double, volume: Int, theme: Theme) = play(volume, theme) {
        when (theme) {
            Theme.NEON -> tone(pitchHz, 70, 40.0, Wave.MIX, slide = 1.3)
            Theme.RETRO -> bitcrush(tone(snap(pitchHz), 55, 22.0, Wave.SQUARE), 4)
            Theme.MINIMAL -> tone(pitchHz * 0.55, 45, 60.0, Wave.SINE, attackMs = 4)
            Theme.SAD -> tone(pitchHz * 0.45, 95, 28.0, Wave.SINE, slide = 0.82, attackMs = 4)
        }
    }

    /** A short rising-and-falling "mew" with a hint of second harmonic. */
    fun meow(volume: Int, theme: Theme) = play(volume, theme) {
        val n = samples(320)
        var phase = 0.0
        DoubleArray(n) { i ->
            val f = i / n.toDouble()
            phase += 2 * PI * (430 + 360 * sin(PI * f)) / RATE
            (sin(phase) * 0.6 + sin(2 * phase) * 0.25) * Math.sqrt(sin(PI * f))
        }
    }

    fun chime(volume: Int, theme: Theme) = play(volume, theme) {
        when (theme) {
            Theme.NEON -> tone(880.0, 80, 14.0, Wave.MIX) + tone(1318.5, 200, 10.0, Wave.MIX)
            Theme.RETRO -> bitcrush(tone(784.0, 60, 8.0, Wave.SQUARE) + tone(987.8, 60, 8.0, Wave.SQUARE) + tone(1174.7, 140, 8.0, Wave.SQUARE), 4)
            Theme.MINIMAL -> tone(660.0, 90, 9.0, Wave.SINE, attackMs = 5) + tone(990.0, 260, 7.0, Wave.SINE, attackMs = 5)
            Theme.SAD -> tone(440.0, 130, 7.0, Wave.SINE, attackMs = 5) + tone(349.2, 340, 5.0, Wave.SINE, slide = 0.97, attackMs = 5)
        }
    }

    fun boom(volume: Int, theme: Theme) = play(volume, theme) {
        when (theme) {
            Theme.NEON -> {
                val n = samples(300)
                var phase = 0.0
                DoubleArray(n) { i ->
                    val env = exp(-(i / RATE.toDouble()) * 12)
                    phase += 2 * PI * (150 - 110 * (i / n.toDouble())) / RATE
                    (sin(phase) * 0.7 + noise() * 0.5) * env
                }
            }
            Theme.RETRO -> {
                val n = samples(250)
                var held = 0.0
                bitcrush(
                    DoubleArray(n) { i ->
                        if (i % 6 == 0) held = noise()
                        held * exp(-(i / RATE.toDouble()) * 14)
                    },
                    3,
                )
            }
            Theme.MINIMAL -> tone(90.0, 190, 17.0, Wave.SINE, slide = 0.55, attackMs = 3)
            Theme.SAD -> tone(70.0, 340, 8.0, Wave.SINE, slide = 0.5, attackMs = 10)
        }
    }

    fun fanfare(volume: Int, theme: Theme) = play(volume, theme) {
        when (theme) {
            Theme.NEON -> listOf(523.25 to 110, 659.25 to 110, 783.99 to 110, 1046.5 to 380)
                .map { (f, ms) -> tone(f, ms, 6.0, Wave.MIX) }.reduce { a, b -> a + b }
            Theme.RETRO -> bitcrush(
                listOf(523.25 to 70, 659.25 to 70, 783.99 to 70, 1046.5 to 70, 783.99 to 70, 1046.5 to 320)
                    .map { (f, ms) -> tone(f, ms, 5.0, Wave.SQUARE) }.reduce { a, b -> a + b },
                4,
            )
            Theme.MINIMAL -> listOf(392.0 to 140, 493.9 to 140, 587.3 to 140, 783.99 to 420)
                .map { (f, ms) -> tone(f, ms, 4.0, Wave.SINE, attackMs = 6) }.reduce { a, b -> a + b }
            // Sad trombone: wah, wah, wah, waaaah.
            Theme.SAD -> tone(311.1, 230, 3.0, Wave.MIX, attackMs = 12) + tone(293.7, 230, 3.0, Wave.MIX, attackMs = 12) +
                tone(277.2, 230, 3.0, Wave.MIX, attackMs = 12) + tone(261.6, 800, 2.2, Wave.MIX, slide = 0.86, attackMs = 12)
        }
    }

    private fun samples(ms: Int) = (RATE * ms / 1000).toInt()

    private fun noise() = random.nextDouble() * 2 - 1

    /** Snap to the nearest pentatonic note so fast typing sounds like a melody. */
    private fun snap(freq: Double) = PENTATONIC.minBy { kotlin.math.abs(ln(it / freq)) }

    private fun bitcrush(pcm: DoubleArray, bits: Int): DoubleArray {
        val levels = (1 shl bits).toDouble()
        for (i in pcm.indices) pcm[i] = floor(pcm[i] * levels / 2) / (levels / 2)
        return pcm
    }

    /** [slide] is the end-frequency multiplier (1.0 = steady pitch). */
    private fun tone(freq: Double, ms: Int, decay: Double, w: Wave, slide: Double = 1.0, attackMs: Int = 0): DoubleArray {
        val n = samples(ms)
        val attack = samples(attackMs).coerceAtLeast(1)
        var phase = 0.0
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            phase += 2 * PI * freq * (1 + (slide - 1) * i / n.toDouble()) / RATE
            val s = sin(phase)
            val wave = when (w) {
                Wave.SQUARE -> if (s >= 0) 0.5 else -0.5
                Wave.MIX -> (if (s >= 0) 0.6 else -0.6) * 0.7 + s * 0.3
                Wave.SINE -> s * 0.8
            }
            val fadeIn = if (attackMs > 0) (i / attack.toDouble()).coerceAtMost(1.0) else 1.0
            wave * exp(-t * decay) * fadeIn
        }
    }

    private fun play(volume: Int, theme: Theme, generate: () -> DoubleArray) {
        if (volume <= 0) return
        pool.execute {
            try {
                val pcm = generate()
                val gain = volume.coerceIn(0, 100) / 100.0 * 0.6 * theme.loudness
                val bytes = ByteArray(pcm.size * 2)
                for (i in pcm.indices) {
                    val v = (pcm[i] * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                val clip = AudioSystem.getClip()
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                clip.open(format, bytes, 0, bytes.size)
                clip.start()
            } catch (_: Throwable) {
                // No audio device (headless, remote dev, CI): stay silent.
            }
        }
    }
}
