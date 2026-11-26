package euclidsketchpad.export

import euclidsketchpad.geometry.*
import euclidsketchpad.geometry.GeometryUtils.ARROWHEAD_SIZE
import euclidsketchpad.geometry.GeometryUtils.FONT_SIZE
import euclidsketchpad.geometry.GeometryUtils.LINE_EXTENT
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ObjectMetadata
import euclidsketchpad.model.StyleSettings
import euclidsketchpad.model.getObjectAnchor
import javafx.scene.text.Font
import javafx.scene.text.Text
import java.io.File
import kotlin.math.*

enum class ExportMethod { CANVAS, CUSTOM, AUTO_FIT }
enum class ExportFormat { PNG, SVG, PDF }

data class ExportSettings(
    val method: ExportMethod,
    val format: ExportFormat,
    val outputWidth: Int,
    val outputHeight: Int,
    val transparentBackground: Boolean,
    val includeGrid: Boolean,
    val boundsMinX: Double,
    val boundsMinY: Double,
    val boundsMaxX: Double,
    val boundsMaxY: Double
)

object ExportRenderer {

    /**
     * Writes the export to [file] and returns warnings for the user (empty if none).
     * PNG export snapshots a JavaFX node, so call this on the FX thread.
     */
    fun export(settings: ExportSettings, manager: GeometricObjectManager, file: File): List<String> {
        val w = settings.outputWidth
        val h = settings.outputHeight
        val canvas: ExportCanvas = when (settings.format) {
            ExportFormat.PNG -> PngExportCanvas(w.toDouble(), h.toDouble(), settings.transparentBackground)
            ExportFormat.SVG -> SvgExportCanvas(w, h, settings.transparentBackground)
            ExportFormat.PDF -> PdfExportCanvas(w, h, settings.transparentBackground)
        }
        render(settings, manager, canvas)
        return canvas.finish(file)
    }

    // --- Auto-fit bounds calculation ---

    fun computeAutoFitBounds(manager: GeometricObjectManager): DoubleArray {
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        fun include(x: Double, y: Double) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!meta.visible || meta.isLocked || !obj.isDefined) continue

            when {
                obj is LinearLike -> {
                    for (pid in obj.parentIds) {
                        val parent = manager.getObject(pid) ?: continue
                        if (parent is PointLike && parent.isDefined) {
                            include(parent.x, parent.y)
                        }
                    }
                }
                obj is Circle -> {
                    include(obj.cx + obj.radius, obj.cy)
                    include(obj.cx - obj.radius, obj.cy)
                    include(obj.cx, obj.cy + obj.radius)
                    include(obj.cx, obj.cy - obj.radius)
                }
                obj is Region -> {
                    for (pid in obj.parentIds) {
                        val parent = manager.getObject(pid) ?: continue
                        if (parent is PointLike && parent.isDefined) {
                            include(parent.x, parent.y)
                        }
                    }
                }
                obj is Angle -> {} // Angles don't contribute to bounding box
                obj is PointLike -> include(obj.x, obj.y)
            }
        }

        if (minX > maxX) {
            // No visible objects — fallback
            return doubleArrayOf(-100.0, -100.0, 100.0, 100.0)
        }

        // Handle degenerate cases (all objects at same point)
        if (maxX - minX < 1e-9) { minX -= 50.0; maxX += 50.0 }
        if (maxY - minY < 1e-9) { minY -= 50.0; maxY += 50.0 }

        // Add 4× the viewport margin fraction so that the clip inset used by
        // the export renderer for arrowhead placement still leaves data points
        // safely inside the clip rect with comfortable padding beyond the clip
        // boundary.
        val marginX = (maxX - minX) * 4 * GeometryUtils.VIEWPORT_MARGIN_FRACTION
        val marginY = (maxY - minY) * 4 * GeometryUtils.VIEWPORT_MARGIN_FRACTION
        return doubleArrayOf(minX - marginX, minY - marginY, maxX + marginX, maxY + marginY)
    }

    // --- Shared rendering walker ---

    private fun render(settings: ExportSettings, manager: GeometricObjectManager, canvas: ExportCanvas) {
        val scaleX = settings.outputWidth / (settings.boundsMaxX - settings.boundsMinX)
        val scaleY = settings.outputHeight / (settings.boundsMaxY - settings.boundsMinY)
        val scale = min(scaleX, scaleY)

        val tx = { mx: Double -> (mx - settings.boundsMinX) * scale }
        val ty = { my: Double -> (settings.boundsMaxY - my) * scale }

        if (settings.includeGrid) {
            renderGrid(canvas, settings, scale, tx, ty)
        }
        renderObjects(canvas, manager, settings, scale, tx, ty)
    }

    private fun renderGrid(canvas: ExportCanvas, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        val w = settings.outputWidth.toDouble()
        val h = settings.outputHeight.toDouble()
        val spacing = GeometryUtils.computeAdaptiveGridSpacing(scale)

        val gridStroke = Stroke("#d9d9d9", 0.5)
        val axisStroke = Stroke("#808080", 1.0)

        var x = floor(settings.boundsMinX / spacing) * spacing
        while (x <= settings.boundsMaxX) {
            val px = tx(x)
            if (px in 0.0..w) {
                canvas.gridLine(true, px, if (abs(x) < spacing * 0.01) axisStroke else gridStroke)
            }
            x += spacing
        }

        var y = floor(settings.boundsMinY / spacing) * spacing
        while (y <= settings.boundsMaxY) {
            val py = ty(y)
            if (py in 0.0..h) {
                canvas.gridLine(false, py, if (abs(y) < spacing * 0.01) axisStroke else gridStroke)
            }
            y += spacing
        }
    }

    private fun renderObjects(canvas: ExportCanvas, manager: GeometricObjectManager, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        // Clip lines/rays/circles in math coordinates, inset from the export bounds.
        val clip = GeometryUtils.computeClipRect(
            doubleArrayOf(settings.boundsMinX, settings.boundsMinY, settings.boundsMaxX, settings.boundsMaxY)
        )

        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            if (obj.vertices.size >= 3) {
                val coords = DoubleArray(obj.vertices.size * 2)
                for (i in obj.vertices.indices) {
                    coords[i * 2] = tx(obj.vertices[i].first)
                    coords[i * 2 + 1] = ty(obj.vertices[i].second)
                }
                canvas.polygon(coords, StyleSettings.colorToHex(meta.color), 0.3)
            }
        }

        // Pass 2: Angles (the angle sweeps clockwise on screen from its start ray)
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            val colorHex = StyleSettings.colorToHex(meta.color)
            canvas.wedge(
                tx(obj.vertexX), ty(obj.vertexY), StyleSettings.angleArcRadius(meta.size),
                obj.startAngleRadians, -obj.sweepAngleRadians,
                colorHex, 0.3, Stroke(colorHex, 1.0, opacity = 0.7)
            )
        }

        // Pass 3: Lines/Rays/Segments/Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue

            val colorHex = StyleSettings.colorToHex(meta.color)
            val stroke = Stroke(colorHex, StyleSettings.strokeThicknessPixels(meta.strokeThickness), meta.strokeStyle == "dashed")

            if (obj is LinearLike) {
                val dx = obj.x2 - obj.x1
                val dy = obj.y2 - obj.y1
                val len = hypot(dx, dy)
                if (len == 0.0) continue
                val ux = dx / len
                val uy = dy / len

                when (obj.linearType) {
                    LinearType.SEGMENT -> canvas.line(tx(obj.x1), ty(obj.y1), tx(obj.x2), ty(obj.y2), stroke)
                    LinearType.LINE -> {
                        val clipped = GeometryUtils.clipLineToRect(
                            obj.x1 - ux * LINE_EXTENT, obj.y1 - uy * LINE_EXTENT,
                            obj.x1 + ux * LINE_EXTENT, obj.y1 + uy * LINE_EXTENT,
                            clip[0], clip[1], clip[2], clip[3]
                        ) ?: continue
                        drawClippedLinear(canvas, clipped, tx, ty, stroke, arrowAtStart = true)
                    }
                    LinearType.RAY -> {
                        val clipped = GeometryUtils.clipLineToRect(
                            obj.x1, obj.y1, obj.x1 + ux * LINE_EXTENT, obj.y1 + uy * LINE_EXTENT,
                            clip[0], clip[1], clip[2], clip[3]
                        ) ?: continue
                        drawClippedLinear(canvas, clipped, tx, ty, stroke, arrowAtStart = false)
                    }
                }
            } else if (obj is Circle) {
                val arcs = GeometryUtils.clipCircleToArcs(obj.cx, obj.cy, obj.radius, clip[0], clip[1], clip[2], clip[3])
                val r = obj.radius * scale
                if (arcs.size == 1 && abs(arcs[0][1] - 360.0) < 0.01) {
                    canvas.circle(tx(obj.cx), ty(obj.cy), r, stroke)
                } else {
                    // clipCircleToArcs returns math-convention degrees (counter-clockwise,
                    // y up), which is counter-clockwise on screen too.
                    for (arcDef in arcs) {
                        canvas.arc(tx(obj.cx), ty(obj.cy), r, arcDef[0] * PI / 180.0, arcDef[1] * PI / 180.0, stroke)
                    }
                }
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            canvas.dot(tx(obj.x), ty(obj.y), StyleSettings.pointRadius(meta.size), StyleSettings.colorToHex(meta.color))
        }

        // Pass 5: Labels. See HANDOFF "Export label offsets" for the offset sign and baseline shift.
        val probe = Text("A").apply { font = Font.font(FONT_SIZE) }
        val baselineShift = -(probe.layoutBounds.minY + probe.layoutBounds.maxY)
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            canvas.text(
                tx(anchor.first) + meta.labelOffsetX,
                ty(anchor.second) - meta.labelOffsetY + baselineShift,
                meta.label!!
            )
        }
    }

    private fun drawClippedLinear(
        canvas: ExportCanvas, clipped: DoubleArray,
        tx: (Double) -> Double, ty: (Double) -> Double,
        stroke: Stroke, arrowAtStart: Boolean
    ) {
        val x1 = tx(clipped[0]); val y1 = ty(clipped[1])
        val x2 = tx(clipped[2]); val y2 = ty(clipped[3])
        canvas.line(x1, y1, x2, y2, stroke)
        val adx = x2 - x1
        val ady = y2 - y1
        val alen = hypot(adx, ady)
        if (alen > 0) {
            canvas.arrowhead(GeometryUtils.computeArrowheadVertices(x2, y2, adx / alen, ady / alen, ARROWHEAD_SIZE), stroke.color)
            if (arrowAtStart) {
                canvas.arrowhead(GeometryUtils.computeArrowheadVertices(x1, y1, -adx / alen, -ady / alen, ARROWHEAD_SIZE), stroke.color)
            }
        }
    }

    private fun isExportable(meta: ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked
}
