import com.keystrokequest.*

/** Dev helper: runs the cat state machine on fake panels and prints what the cats did. */
fun main() {
    val plats = listOf(
        Platform("ground", 0, 1400, 900), Platform("editor", 300, 1100, 300),
        Platform("project", 0, 280, 200), Platform("terminal", 300, 1100, 600),
    )
    val cats = mutableListOf<CatData>()
    repeat(6) { cats += CatFactory.create(it + 2, cats) }
    val counts = HashMap<CatState, Int>()
    val visited = HashSet<Any>()
    for (d in cats) {
        val a = CatActor(d, CatLook(d.seed))
        a.place(plats.first { it.key == "ground" }, 700.0)
        repeat(30 * 60 * 5) {
            a.update(plats)
            counts.merge(a.state, 1, Int::plus)
            a.plat?.let { visited += it.key }
            check(a.x in -50.0..1450.0 && a.y in -100.0..950.0) { "cat escaped: ${a.x},${a.y} ${a.state}" }
        }
    }
    println("frames per state: $counts")
    println("platforms visited: $visited")
    val a = CatActor(cats[0], CatLook(cats[0].seed)); a.drop(500.0, -40.0)
    var n = 0; while (a.state == CatState.FALL && n++ < 500) a.update(plats)
    println("arrival landed after $n frames on ${a.plat?.key} y=${a.y}")
    println("unique looks: ${cats.map { CatLook(it.seed).key }.toSet().size}/${cats.size}")
}
