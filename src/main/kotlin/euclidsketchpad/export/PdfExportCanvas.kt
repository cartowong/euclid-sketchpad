package euclidsketchpad.export

import euclidsketchpad.APP_NAME
import euclidsketchpad.APP_VERSION
import euclidsketchpad.geometry.GeometryUtils.FONT_SIZE
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Vector PDF of one page sized exactly width × height points. */
internal class PdfExportCanvas(
    private val width: Int,
    private val height: Int,
    transparentBackground: Boolean
) : ExportCanvas {

    private val doc = PDDocument()
    private val font: PDType0Font
    private val cs: PDPageContentStream
    private val unsupportedChars = linkedSetOf<String>()

    init {
        val page = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
        doc.addPage(page)
        font = PdfExportCanvas::class.java.getResourceAsStream("/fonts/DejaVuSans.ttf")!!.use {
            PDType0Font.load(doc, it, true)
        }
        cs = PDPageContentStream(doc, page)
        // Flip to the walker's top-down pixel space; text is flipped back in text().
        cs.transform(Matrix(1f, 0f, 0f, -1f, 0f, height.toFloat()))
        if (!transparentBackground) {
            cs.setNonStrokingColor(1f, 1f, 1f)
            cs.addRect(0f, 0f, width.toFloat(), height.toFloat())
            cs.fill()
        }
    }

    private inline fun draw(block: () -> Unit) {
        cs.saveGraphicsState()
        block()
        cs.restoreGraphicsState()
    }

    private fun setAlpha(fill: Double, stroke: Double) {
        if (fill >= 1.0 && stroke >= 1.0) return
        val gs = PDExtendedGraphicsState()
        gs.nonStrokingAlphaConstant = fill.toFloat()
        gs.strokingAlphaConstant = stroke.toFloat()
        cs.setGraphicsStateParameters(gs)
    }

    private fun setStroke(s: Stroke) {
        val (r, g, b) = rgb(s.color)
        cs.setStrokingColor(r, g, b)
        cs.setLineWidth(s.width.toFloat())
        if (s.dashed) cs.setLineDashPattern(FloatArray(DASH_PATTERN.size) { DASH_PATTERN[it].toFloat() }, 0f)
    }

    private fun setFill(color: String) {
        val (r, g, b) = rgb(color)
        cs.setNonStrokingColor(r, g, b)
    }

    override fun gridLine(vertical: Boolean, at: Double, stroke: Stroke) {
        if (vertical) line(at, 0.0, at, height.toDouble(), stroke)
        else line(0.0, at, width.toDouble(), at, stroke)
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) = draw {
        setAlpha(1.0, stroke.opacity)
        setStroke(stroke)
        cs.moveTo(x1.toFloat(), y1.toFloat())
        cs.lineTo(x2.toFloat(), y2.toFloat())
        cs.stroke()
    }

    private fun polygonPath(points: DoubleArray) {
        cs.moveTo(points[0].toFloat(), points[1].toFloat())
        for (i in 2 until points.size step 2) cs.lineTo(points[i].toFloat(), points[i + 1].toFloat())
        cs.closePath()
    }

    override fun polygon(points: DoubleArray, color: String, opacity: Double) = draw {
        setAlpha(opacity, 1.0)
        setFill(color)
        polygonPath(points)
        cs.fill()
    }

    override fun arrowhead(points: DoubleArray, color: String) = draw {
        setFill(color)
        polygonPath(points)
        cs.fill()
    }

    override fun circle(cx: Double, cy: Double, r: Double, stroke: Stroke) = draw {
        setAlpha(1.0, stroke.opacity)
        setStroke(stroke)
        arcPath(cx, cy, r, 0.0, 2 * PI, moveToStart = true)
        cs.closePath()
        cs.stroke()
    }

    override fun dot(cx: Double, cy: Double, r: Double, color: String) = draw {
        setFill(color)
        arcPath(cx, cy, r, 0.0, 2 * PI, moveToStart = true)
        cs.closePath()
        cs.fill()
    }

    override fun arc(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, stroke: Stroke) = draw {
        setAlpha(1.0, stroke.opacity)
        setStroke(stroke)
        arcPath(cx, cy, r, startRad, sweepRad, moveToStart = true)
        cs.stroke()
    }

    override fun wedge(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, color: String, fillOpacity: Double, stroke: Stroke) = draw {
        setAlpha(fillOpacity, stroke.opacity)
        setFill(color)
        setStroke(stroke)
        cs.moveTo(cx.toFloat(), cy.toFloat())
        arcPath(cx, cy, r, startRad, sweepRad, moveToStart = false)
        cs.closePath()
        cs.fillAndStroke()
    }

    /** Approximates the arc with cubic Béziers of at most 90° each. */
    private fun arcPath(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, moveToStart: Boolean) {
        // Screen y points down, so counter-clockwise on screen subtracts the sine.
        fun px(a: Double) = (cx + r * cos(a)).toFloat()
        fun py(a: Double) = (cy - r * sin(a)).toFloat()
        if (moveToStart) cs.moveTo(px(startRad), py(startRad)) else cs.lineTo(px(startRad), py(startRad))
        val n = ceil(abs(sweepRad) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = sweepRad / n
        val k = 4.0 / 3.0 * tan(step / 4)
        for (i in 0 until n) {
            val a1 = startRad + i * step
            val a2 = a1 + step
            // Tangent at angle a is (-r sin a, -r cos a) in screen space.
            cs.curveTo(
                (px(a1) - k * r * sin(a1)).toFloat(), (py(a1) - k * r * cos(a1)).toFloat(),
                (px(a2) + k * r * sin(a2)).toFloat(), (py(a2) + k * r * cos(a2)).toFloat(),
                px(a2), py(a2)
            )
        }
    }

    override fun text(x: Double, y: Double, text: String) = draw {
        cs.beginText()
        cs.setFont(font, FONT_SIZE.toFloat())
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.setTextMatrix(Matrix(1f, 0f, 0f, -1f, x.toFloat(), y.toFloat()))
        cs.showText(encodable(text))
        cs.endText()
    }

    /** Replaces characters DejaVu Sans has no glyph for with '?', remembering them for the warning. */
    private fun encodable(text: String): String {
        val sb = StringBuilder()
        text.codePoints().forEach { cp ->
            val ch = String(Character.toChars(cp))
            val ok = try {
                font.encode(ch)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
            if (ok) sb.append(ch) else {
                sb.append('?')
                unsupportedChars.add(ch)
            }
        }
        return sb.toString()
    }

    override fun finish(file: File): List<String> {
        doc.use {
            cs.close()
            it.documentInformation.title = file.nameWithoutExtension
            it.documentInformation.creator = "$APP_NAME $APP_VERSION"
            it.save(file)
        }
        if (unsupportedChars.isEmpty()) return emptyList()
        return listOf(
            "Some label characters are not available in the PDF font and were replaced with \"?\": " +
                unsupportedChars.joinToString(" ")
        )
    }

    private fun rgb(hex: String): Triple<Float, Float, Float> {
        val v = hex.removePrefix("#").toInt(16)
        return Triple(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
    }
}
