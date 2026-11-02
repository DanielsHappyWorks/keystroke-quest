package com.keystrokequest

import java.awt.Color
import java.util.Random

/** Persisted cat. Everything about its appearance is derived from [seed], so only the seed is stored. */
class CatData {
    var name: String = ""
    var seed: Long = 0
    var level: Int = 0
    var pets: Int = 0
}

enum class Pattern(val word: String) { SOLID("solid"), TABBY("tabby"), TUXEDO("tuxedo"), SPOTTED("spotted"), SOCKS("socks") }

class Spot(val fx: Double, val fy: Double, val size: Double)

/**
 * Deterministic appearance for a cat seed. [key] identifies the combination of coat, shade, pattern,
 * eyes and accessory, which [CatFactory] keeps unique across a player's cats.
 */
class CatLook(seed: Long) {
    private class Base(val name: String, val color: Color)

    private val r = Random(seed)
    private val baseIdx = r.nextInt(BASES.size)
    private val shade = r.nextInt(3)
    private val eyeIdx = r.nextInt(EYES.size)

    val pattern: Pattern = Pattern.entries[r.nextInt(Pattern.entries.size)]
    val accessory: Int = r.nextInt(ACCESSORIES.size)
    val accessoryColor: Color = Color.getHSBColor(r.nextFloat(), 0.7f, 0.95f)
    val scale: Float = 0.9f + r.nextFloat() * 0.25f
    /** 0.6 = sleepy, 1.5 = bouncy. Scales walking speed and how often the cat jumps instead of napping. */
    val energy: Double = 0.6 + r.nextDouble() * 0.9
    val spots: List<Spot> = List(3) { Spot(0.15 + r.nextDouble() * 0.7, 0.1 + r.nextDouble() * 0.55, 6 + r.nextDouble() * 5) }

    val fur: Color = tint(BASES[baseIdx].color, shade)
    val eyes: Color = EYES[eyeIdx].second
    val accent: Color = when (pattern) {
        Pattern.SOLID -> fur
        Pattern.TABBY -> darker(fur, 0.62)
        Pattern.TUXEDO, Pattern.SOCKS -> if (luma(fur) > 200) Color(70, 70, 78) else Color(246, 246, 242)
        Pattern.SPOTTED -> tint(BASES[(baseIdx + 4) % BASES.size].color, 1).let { if (luma(fur) > 150) darker(it, 0.8) else it }
    }

    val key: String = "${pattern.ordinal}-$baseIdx-$shade-$eyeIdx-$accessory"

    val description: String =
        "${SHADE_WORDS[shade]}${BASES[baseIdx].name} ${pattern.word} cat with ${EYES[eyeIdx].first} eyes${ACCESSORIES[accessory]}"

    companion object {
        private val BASES = listOf(
            Base("white", Color(245, 245, 240)), Base("black", Color(66, 66, 78)), Base("grey", Color(135, 140, 150)),
            Base("orange", Color(232, 140, 58)), Base("cream", Color(238, 214, 170)), Base("brown", Color(124, 84, 54)),
            Base("chocolate", Color(92, 58, 44)), Base("silver", Color(190, 196, 205)), Base("blue-grey", Color(110, 130, 160)),
            Base("ginger", Color(196, 96, 48)), Base("lilac", Color(170, 150, 175)), Base("honey", Color(214, 170, 80)),
        )
        private val EYES = listOf(
            "green" to Color(90, 200, 100), "amber" to Color(240, 180, 40), "blue" to Color(90, 160, 240),
            "copper" to Color(200, 110, 50), "hazel" to Color(160, 150, 60),
        )
        private val SHADE_WORDS = listOf("dark ", "", "light ")
        private val ACCESSORIES = listOf("", ", bell collar", ", bow", ", scarf")

        fun luma(c: Color) = (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue).toInt()

        fun darker(c: Color, f: Double) = Color((c.red * f).toInt(), (c.green * f).toInt(), (c.blue * f).toInt())

        private fun tint(c: Color, shade: Int): Color = when (shade) {
            0 -> darker(c, 0.82)
            2 -> Color(c.red + (255 - c.red) / 4, c.green + (255 - c.green) / 4, c.blue + (255 - c.blue) / 4)
            else -> c
        }
    }
}

object CatFactory {
    private val NAMES = listOf(
        "Mochi", "Biscuit", "Pixel", "Noodle", "Pickles", "Waffles", "Tofu", "Maple", "Ziggy", "Pumpkin",
        "Miso", "Pancake", "Bean", "Clover", "Sprout", "Nugget", "Juniper", "Olive", "Cosmo", "Pepper",
        "Marshmallow", "Turnip", "Fudge", "Basil", "Pretzel", "Muffin", "Comet", "Whiskers", "Sushi", "Cinnamon",
        "Dumpling", "Ginger", "Luna", "Mango", "Peanut", "Sesame", "Taco", "Toffee", "Willow", "Yuzu",
        "Bagel", "Cheddar", "Doodle", "Espresso", "Figaro", "Gizmo", "Hobbit", "Indie", "Jellybean", "Kiwi",
        "Latte", "Mittens", "Nacho", "Oreo", "Poppy", "Quill", "Ravioli", "Smudge", "Truffle", "Waffle",
    )

    /** Creates a cat whose look and name differ from every cat in [existing]. */
    fun create(level: Int, existing: List<CatData>): CatData {
        val usedLooks = existing.map { CatLook(it.seed).key }.toSet()
        val usedNames = existing.map { it.name }.toSet()
        val rnd = Random()
        var seed = rnd.nextLong()
        // The look space holds thousands of combinations; if it ever fills up, accept a repeat.
        var attempts = 0
        while (CatLook(seed).key in usedLooks && attempts++ < 500) seed = rnd.nextLong()
        val name = NAMES.filter { it !in usedNames }.let { if (it.isEmpty()) "Cat #$level" else it[rnd.nextInt(it.size)] }
        return CatData().also { it.name = name; it.seed = seed; it.level = level }
    }
}
