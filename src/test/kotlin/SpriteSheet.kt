import com.keystrokequest.CatActor
import com.keystrokequest.CatFactory
import com.keystrokequest.CatSprite
import com.keystrokequest.CatState
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Dev helper: renders every cat pose to a PNG so sprites can be checked without launching an IDE. */
fun main(args: Array<String>) {
    val states = listOf(CatState.WALK, CatState.SIT, CatState.GROOM, CatState.SLEEP, CatState.JUMP, CatState.PETTED)
    val cats = mutableListOf<com.keystrokequest.CatData>()
    repeat(8) { cats += CatFactory.create(it + 2, cats) }
    val cw = 120
    val ch = 100
    val img = BufferedImage(cw * states.size, ch * cats.size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(43, 45, 48)
    g.fillRect(0, 0, img.width, img.height)
    cats.forEachIndexed { r, d ->
        states.forEachIndexed { c, st ->
            val a = CatActor(d, com.keystrokequest.CatLook(d.seed))
            a.state = st
            a.anim = 10 + c * 7
            a.x = c * cw + 60.0
            a.y = r * ch + 80.0
            a.vy = if (st == CatState.JUMP) -4.0 else 0.0
            CatSprite.draw(g, a)
        }
        g.color = Color.WHITE
        g.drawString("${d.name}: ${com.keystrokequest.CatLook(d.seed).description}", 4, r * ch + 12)
    }
    g.dispose()
    ImageIO.write(img, "png", File(args[0]))
}
