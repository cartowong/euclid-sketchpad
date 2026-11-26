package euclidsketchpad.export

import euclidsketchpad.geometry.GeometryUtils.FONT_SIZE
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.Group
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.scene.text.Font
import javafx.scene.text.Text
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.Polygon as FxPolygon

/** Builds a JavaFX scene graph and snapshots it; must run on the FX thread. */
internal class PngExportCanvas(
    private val width: Double,
    private val height: Double,
    private val transparentBackground: Boolean
) : ExportCanvas {

    private val group = Group()

    init {
        if (!transparentBackground) {
            group.children.add(Rectangle(width, height).apply { fill = Color.WHITE })
        }
    }

    private fun Shape.applyStroke(s: Stroke) {
        stroke = Color.web(s.color, s.opacity)
        strokeWidth = s.width
        if (s.dashed) strokeDashArray.addAll(DASH_PATTERN.toList())
    }

    override fun gridLine(vertical: Boolean, at: Double, stroke: Stroke) {
        val line = if (vertical) FxLine(at, 0.0, at, height) else FxLine(0.0, at, width, at)
        line.applyStroke(stroke)
        group.children.add(line)
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        group.children.add(FxLine(x1, y1, x2, y2).apply { applyStroke(stroke) })
    }

    override fun polygon(points: DoubleArray, color: String, opacity: Double) {
        group.children.add(FxPolygon(*points).apply {
            fill = Color.web(color, opacity)
            stroke = null
        })
    }

    override fun arrowhead(points: DoubleArray, color: String) {
        val c = Color.web(color)
        group.children.add(FxPolygon(*points).apply { fill = c; stroke = c })
    }

    override fun circle(cx: Double, cy: Double, r: Double, stroke: Stroke) {
        group.children.add(FxCircle(cx, cy, r).apply {
            fill = Color.TRANSPARENT
            applyStroke(stroke)
        })
    }

    override fun dot(cx: Double, cy: Double, r: Double, color: String) {
        group.children.add(FxCircle(cx, cy, r).apply { fill = Color.web(color) })
    }

    override fun arc(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, stroke: Stroke) {
        group.children.add(Arc(cx, cy, r, r, startRad * 180.0 / PI, sweepRad * 180.0 / PI).apply {
            type = ArcType.OPEN
            fill = Color.TRANSPARENT
            applyStroke(stroke)
        })
    }

    override fun wedge(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, color: String, fillOpacity: Double, stroke: Stroke) {
        group.children.add(Arc(cx, cy, r, r, startRad * 180.0 / PI, sweepRad * 180.0 / PI).apply {
            type = ArcType.ROUND
            fill = Color.web(color, fillOpacity)
            applyStroke(stroke)
        })
    }

    override fun text(x: Double, y: Double, text: String) {
        group.children.add(Text(x, y, text).apply {
            font = Font.font(FONT_SIZE)
            fill = Color.BLACK
        })
    }

    override fun finish(file: File): List<String> {
        val params = SnapshotParameters()
        params.fill = if (transparentBackground) Color.TRANSPARENT else Color.WHITE
        params.viewport = Rectangle2D(0.0, 0.0, width, height)
        val image = group.snapshot(params, null)
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file)
        return emptyList()
    }
}
