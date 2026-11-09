package euclidsketchpad.export

import euclidsketchpad.geometry.*
import euclidsketchpad.geometry.GeometryUtils.ARROWHEAD_SIZE
import euclidsketchpad.geometry.GeometryUtils.FONT_SIZE
import euclidsketchpad.geometry.GeometryUtils.LINE_EXTENT
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ObjectMetadata
import euclidsketchpad.model.StyleSettings
import euclidsketchpad.model.getObjectAnchor
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.Group
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.Polygon as FxPolygon
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.Text
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

enum class ExportMethod { CANVAS, CUSTOM, AUTO_FIT }
enum class ExportFormat { PNG, SVG }

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

    fun export(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        when (settings.format) {
            ExportFormat.PNG -> exportPng(settings, manager, file)
            ExportFormat.SVG -> exportSvg(settings, manager, file)
        }
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

    // --- PNG Export ---

    private fun exportPng(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        val w = settings.outputWidth.toDouble()
        val h = settings.outputHeight.toDouble()
        val group = Group()

        // Background
        if (!settings.transparentBackground) {
            group.children.add(Rectangle(w, h).apply { fill = Color.WHITE })
        }

        val scaleX = w / (settings.boundsMaxX - settings.boundsMinX)
        val scaleY = h / (settings.boundsMaxY - settings.boundsMinY)
        val scale = min(scaleX, scaleY)

        val tx = { mx: Double -> (mx - settings.boundsMinX) * scale }
        val ty = { my: Double -> (settings.boundsMaxY - my) * scale }

        if (settings.includeGrid) {
            renderGridPng(group, settings, scale, tx, ty)
        }

        renderObjectsPng(group, manager, settings, scale, tx, ty)

        // Snapshot
        val params = SnapshotParameters()
        params.fill = if (settings.transparentBackground) Color.TRANSPARENT else Color.WHITE
        params.viewport = Rectangle2D(0.0, 0.0, w, h)

        val image = group.snapshot(params, null)
        val buffered = SwingFXUtils.fromFXImage(image, null)
        ImageIO.write(buffered, "png", file)
    }

    private fun renderGridPng(group: Group, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        val bMinX = settings.boundsMinX
        val bMinY = settings.boundsMinY
        val bMaxX = settings.boundsMaxX
        val bMaxY = settings.boundsMaxY
        val w = settings.outputWidth.toDouble()
        val h = settings.outputHeight.toDouble()

        val spacing = GeometryUtils.computeAdaptiveGridSpacing(scale)

        val gridColor = Color.gray(0.85)
        val axisColor = Color.gray(0.5)

        // Vertical grid lines
        var x = floor(bMinX / spacing) * spacing
        while (x <= bMaxX) {
            val px = tx(x)
            if (px in 0.0..w) {
                val line = FxLine(px, 0.0, px, h)
                line.stroke = if (abs(x) < spacing * 0.01) axisColor else gridColor
                line.strokeWidth = if (abs(x) < spacing * 0.01) 1.0 else 0.5
                group.children.add(line)
            }
            x += spacing
        }

        // Horizontal grid lines
        var y = floor(bMinY / spacing) * spacing
        while (y <= bMaxY) {
            val py = ty(y)
            if (py in 0.0..h) {
                val line = FxLine(0.0, py, w, py)
                line.stroke = if (abs(y) < spacing * 0.01) axisColor else gridColor
                line.strokeWidth = if (abs(y) < spacing * 0.01) 1.0 else 0.5
                group.children.add(line)
            }
            y += spacing
        }
    }

    private fun renderObjectsPng(group: Group, manager: GeometricObjectManager, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        // Clip lines/rays in math coordinates with 5% inset from the export bounds.
        val mathClipRect = GeometryUtils.computeClipRect(
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
                val polygon = FxPolygon(*coords)
                polygon.fill = Color.web(StyleSettings.colorToHex(meta.color), 0.3)
                polygon.stroke = null
                group.children.add(polygon)
            }
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue

            val arcRadius = StyleSettings.angleArcRadius(meta.size)
            val cx = tx(obj.vertexX)
            val cy = ty(obj.vertexY)
            val colorHex = StyleSettings.colorToHex(meta.color)

            val startDeg = obj.startAngleRadians * 180.0 / PI
            val sweepDeg = -obj.sweepAngleRadians * 180.0 / PI

            val arc = Arc(cx, cy, arcRadius, arcRadius, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = Color.web(colorHex, 0.3)
            arc.stroke = Color.web(colorHex, 0.7)
            arc.strokeWidth = 1.0
            group.children.add(arc)
        }

        // Pass 3: Lines/Rays/Segments/Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue

            val objColor = Color.web(StyleSettings.colorToHex(meta.color))
            val objStrokeWidth = StyleSettings.strokeThicknessPixels(meta.strokeThickness)
            val isDashed = meta.strokeStyle == "dashed"

            if (obj is LinearLike) {
                val dx = obj.x2 - obj.x1
                val dy = obj.y2 - obj.y1
                val len = hypot(dx, dy)
                if (len == 0.0) continue
                val ux = dx / len
                val uy = dy / len

                when (obj.linearType) {
                    LinearType.SEGMENT -> {
                        val line = FxLine(tx(obj.x1), ty(obj.y1), tx(obj.x2), ty(obj.y2))
                        line.strokeWidth = objStrokeWidth
                        line.stroke = objColor
                        if (isDashed) line.strokeDashArray.addAll(6.0, 4.0)
                        group.children.add(line)
                    }
                    LinearType.LINE -> {
                        val ex1 = obj.x1 - ux * LINE_EXTENT
                        val ey1 = obj.y1 - uy * LINE_EXTENT
                        val ex2 = obj.x1 + ux * LINE_EXTENT
                        val ey2 = obj.y1 + uy * LINE_EXTENT
                        val clipped = GeometryUtils.clipLineToRect(ex1, ey1, ex2, ey2, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
                        if (clipped != null) {
                            val line = FxLine(tx(clipped[0]), ty(clipped[1]), tx(clipped[2]), ty(clipped[3]))
                            line.strokeWidth = objStrokeWidth
                            line.stroke = objColor
                            if (isDashed) line.strokeDashArray.addAll(6.0, 4.0)
                            group.children.add(line)
                            val adx = tx(clipped[2]) - tx(clipped[0])
                            val ady = ty(clipped[3]) - ty(clipped[1])
                            val alen = hypot(adx, ady)
                            if (alen > 0) {
                                addArrowheadPng(group, tx(clipped[2]), ty(clipped[3]), adx / alen, ady / alen, objColor)
                                addArrowheadPng(group, tx(clipped[0]), ty(clipped[1]), -adx / alen, -ady / alen, objColor)
                            }
                        }
                    }
                    LinearType.RAY -> {
                        val ex = obj.x1 + ux * LINE_EXTENT
                        val ey = obj.y1 + uy * LINE_EXTENT
                        val clipped = GeometryUtils.clipLineToRect(obj.x1, obj.y1, ex, ey, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
                        if (clipped != null) {
                            val line = FxLine(tx(clipped[0]), ty(clipped[1]), tx(clipped[2]), ty(clipped[3]))
                            line.strokeWidth = objStrokeWidth
                            line.stroke = objColor
                            if (isDashed) line.strokeDashArray.addAll(6.0, 4.0)
                            group.children.add(line)
                            val adx = tx(clipped[2]) - tx(clipped[0])
                            val ady = ty(clipped[3]) - ty(clipped[1])
                            val alen = hypot(adx, ady)
                            if (alen > 0) {
                                addArrowheadPng(group, tx(clipped[2]), ty(clipped[3]), adx / alen, ady / alen, objColor)
                            }
                        }
                    }
                }
            } else if (obj is Circle) {
                addClippedCirclePng(group, obj.cx, obj.cy, obj.radius, mathClipRect, scale, tx, ty, objColor, objStrokeWidth, meta.strokeStyle)
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            val circle = FxCircle(tx(obj.x), ty(obj.y), StyleSettings.pointRadius(meta.size))
            circle.fill = Color.web(StyleSettings.colorToHex(meta.color))
            group.children.add(circle)
        }

        // Pass 5: Labels
        val probe = Text("A").apply { font = Font.font(FONT_SIZE) }
        val baselineShift = -(probe.layoutBounds.minY + probe.layoutBounds.maxY)
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            val text = Text(meta.label)
            text.font = Font.font(FONT_SIZE)
            text.x = tx(anchor.first) + meta.labelOffsetX
            text.y = ty(anchor.second) - meta.labelOffsetY + baselineShift
            text.fill = Color.BLACK
            group.children.add(text)
        }
    }

    private fun addArrowheadPng(group: Group, tipX: Double, tipY: Double, dirX: Double, dirY: Double, color: Color = Color.BLACK) {
        val v = GeometryUtils.computeArrowheadVertices(tipX, tipY, dirX, dirY, ARROWHEAD_SIZE)
        val arrow = FxPolygon(v[0], v[1], v[2], v[3], v[4], v[5])
        arrow.fill = color
        arrow.stroke = color
        group.children.add(arrow)
    }

    // --- SVG Export ---

    private fun exportSvg(settings: ExportSettings, manager: GeometricObjectManager, file: File) {
        val w = settings.outputWidth
        val h = settings.outputHeight
        val scaleX = w.toDouble() / (settings.boundsMaxX - settings.boundsMinX)
        val scaleY = h.toDouble() / (settings.boundsMaxY - settings.boundsMinY)
        val scale = min(scaleX, scaleY)

        val tx = { mx: Double -> (mx - settings.boundsMinX) * scale }
        val ty = { my: Double -> (settings.boundsMaxY - my) * scale }

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")

        // Background
        if (!settings.transparentBackground) {
            sb.appendLine("""  <rect width="$w" height="$h" fill="white"/>""")
        }

        if (settings.includeGrid) {
            renderGridSvg(sb, settings, scale, tx, ty)
        }

        renderObjectsSvg(sb, manager, settings, scale, tx, ty)

        sb.appendLine("</svg>")
        file.writeText(sb.toString())
    }

    private fun renderGridSvg(sb: StringBuilder, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        val bMinX = settings.boundsMinX
        val bMinY = settings.boundsMinY
        val bMaxX = settings.boundsMaxX
        val bMaxY = settings.boundsMaxY
        val w = settings.outputWidth.toDouble()
        val h = settings.outputHeight.toDouble()

        val spacing = GeometryUtils.computeAdaptiveGridSpacing(scale)

        // Vertical lines
        var x = floor(bMinX / spacing) * spacing
        while (x <= bMaxX) {
            val px = tx(x)
            if (px in 0.0..w) {
                val isAxis = abs(x) < spacing * 0.01
                val color = if (isAxis) "#808080" else "#d9d9d9"
                val sw = if (isAxis) 1.0 else 0.5
                sb.appendLine("""  <line x1="${fmt(px)}" y1="0" x2="${fmt(px)}" y2="$h" stroke="$color" stroke-width="$sw"/>""")
            }
            x += spacing
        }

        // Horizontal lines
        var y = floor(bMinY / spacing) * spacing
        while (y <= bMaxY) {
            val py = ty(y)
            if (py in 0.0..h) {
                val isAxis = abs(y) < spacing * 0.01
                val color = if (isAxis) "#808080" else "#d9d9d9"
                val sw = if (isAxis) 1.0 else 0.5
                sb.appendLine("""  <line x1="0" y1="${fmt(py)}" x2="$w" y2="${fmt(py)}" stroke="$color" stroke-width="$sw"/>""")
            }
            y += spacing
        }
    }

    private fun renderObjectsSvg(sb: StringBuilder, manager: GeometricObjectManager, settings: ExportSettings, scale: Double, tx: (Double) -> Double, ty: (Double) -> Double) {
        // Clip lines/rays in math coordinates with 5% inset from the export bounds.
        val mathClipRect = GeometryUtils.computeClipRect(
            doubleArrayOf(settings.boundsMinX, settings.boundsMinY, settings.boundsMaxX, settings.boundsMaxY)
        )

        // Pass 1: Regions
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            if (obj.vertices.size >= 3) {
                val points = obj.vertices.joinToString(" ") { (vx, vy) ->
                    "${fmt(tx(vx))},${fmt(ty(vy))}"
                }
                val colorHex = StyleSettings.colorToHex(meta.color)
                sb.appendLine("""  <polygon points="$points" fill="$colorHex" fill-opacity="0.3" stroke="none"/>""")
            }
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue

            val cx = tx(obj.vertexX)
            val cy = ty(obj.vertexY)
            val r = StyleSettings.angleArcRadius(meta.size)
            val colorHex = StyleSettings.colorToHex(meta.color)

            val startAngle = -obj.startAngleRadians
            val endAngle = startAngle + obj.sweepAngleRadians

            val x1 = cx + r * cos(startAngle)
            val y1 = cy + r * sin(startAngle)
            val x2 = cx + r * cos(endAngle)
            val y2 = cy + r * sin(endAngle)

            val largeArc = if (obj.sweepAngleRadians > PI) 1 else 0
            val sweepFlag = 1

            sb.appendLine("""  <path d="M ${fmt(cx)} ${fmt(cy)} L ${fmt(x1)} ${fmt(y1)} A $r $r 0 $largeArc $sweepFlag ${fmt(x2)} ${fmt(y2)} Z" fill="$colorHex" fill-opacity="0.3" stroke="$colorHex" stroke-opacity="0.7" stroke-width="1"/>""")
        }

        // Pass 3: Lines/Rays/Segments/Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !obj.isDefined) continue

            val colorHex = StyleSettings.colorToHex(meta.color)
            val sw = StyleSettings.strokeThicknessPixels(meta.strokeThickness)
            val dashAttr = if (meta.strokeStyle == "dashed") """ stroke-dasharray="6 4"""" else ""

            if (obj is LinearLike) {
                val dx = obj.x2 - obj.x1
                val dy = obj.y2 - obj.y1
                val len = hypot(dx, dy)
                if (len == 0.0) continue
                val ux = dx / len
                val uy = dy / len

                when (obj.linearType) {
                    LinearType.SEGMENT -> {
                        sb.appendLine("""  <line x1="${fmt(tx(obj.x1))}" y1="${fmt(ty(obj.y1))}" x2="${fmt(tx(obj.x2))}" y2="${fmt(ty(obj.y2))}" stroke="$colorHex" stroke-width="$sw"$dashAttr/>""")
                    }
                    LinearType.LINE -> {
                        val ex1 = obj.x1 - ux * LINE_EXTENT
                        val ey1 = obj.y1 - uy * LINE_EXTENT
                        val ex2 = obj.x1 + ux * LINE_EXTENT
                        val ey2 = obj.y1 + uy * LINE_EXTENT
                        val clipped = GeometryUtils.clipLineToRect(ex1, ey1, ex2, ey2, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
                        if (clipped != null) {
                            sb.appendLine("""  <line x1="${fmt(tx(clipped[0]))}" y1="${fmt(ty(clipped[1]))}" x2="${fmt(tx(clipped[2]))}" y2="${fmt(ty(clipped[3]))}" stroke="$colorHex" stroke-width="$sw"$dashAttr/>""")
                            val adx = tx(clipped[2]) - tx(clipped[0])
                            val ady = ty(clipped[3]) - ty(clipped[1])
                            val alen = hypot(adx, ady)
                            if (alen > 0) {
                                addArrowheadSvg(sb, tx(clipped[2]), ty(clipped[3]), adx / alen, ady / alen, colorHex)
                                addArrowheadSvg(sb, tx(clipped[0]), ty(clipped[1]), -adx / alen, -ady / alen, colorHex)
                            }
                        }
                    }
                    LinearType.RAY -> {
                        val ex = obj.x1 + ux * LINE_EXTENT
                        val ey = obj.y1 + uy * LINE_EXTENT
                        val clipped = GeometryUtils.clipLineToRect(obj.x1, obj.y1, ex, ey, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
                        if (clipped != null) {
                            sb.appendLine("""  <line x1="${fmt(tx(clipped[0]))}" y1="${fmt(ty(clipped[1]))}" x2="${fmt(tx(clipped[2]))}" y2="${fmt(ty(clipped[3]))}" stroke="$colorHex" stroke-width="$sw"$dashAttr/>""")
                            val adx = tx(clipped[2]) - tx(clipped[0])
                            val ady = ty(clipped[3]) - ty(clipped[1])
                            val alen = hypot(adx, ady)
                            if (alen > 0) {
                                addArrowheadSvg(sb, tx(clipped[2]), ty(clipped[3]), adx / alen, ady / alen, colorHex)
                            }
                        }
                    }
                }
            } else if (obj is Circle) {
                addClippedCircleSvg(sb, obj.cx, obj.cy, obj.radius, mathClipRect, scale, tx, ty, colorHex, sw, meta.strokeStyle)
            }
        }

        // Pass 4: Points
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta)) continue
            val r = StyleSettings.pointRadius(meta.size)
            val colorHex = StyleSettings.colorToHex(meta.color)
            sb.appendLine("""  <circle cx="${fmt(tx(obj.x))}" cy="${fmt(ty(obj.y))}" r="$r" fill="$colorHex"/>""")
        }

        // Pass 5: Labels (same baseline shift as PNG — see comment there)
        val probe = Text("A").apply { font = Font.font(FONT_SIZE) }
        val baselineShift = -(probe.layoutBounds.minY + probe.layoutBounds.maxY)
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isExportable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val anchor = getObjectAnchor(obj, meta) ?: continue
            val lx = tx(anchor.first) + meta.labelOffsetX
            val ly = ty(anchor.second) - meta.labelOffsetY + baselineShift
            val escaped = meta.label!!.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.appendLine("""  <text x="${fmt(lx)}" y="${fmt(ly)}" font-size="$FONT_SIZE" fill="black">$escaped</text>""")
        }
    }

    private fun addArrowheadSvg(sb: StringBuilder, tipX: Double, tipY: Double, dirX: Double, dirY: Double, color: String = "black") {
        val v = GeometryUtils.computeArrowheadVertices(tipX, tipY, dirX, dirY, ARROWHEAD_SIZE)
        sb.appendLine("""  <polygon points="${fmt(v[0])},${fmt(v[1])} ${fmt(v[2])},${fmt(v[3])} ${fmt(v[4])},${fmt(v[5])}" fill="$color"/>""")
    }

    // --- Circle clipping helpers ---

    private fun addClippedCirclePng(
        group: Group,
        cx: Double, cy: Double, radius: Double,
        mathClipRect: DoubleArray,
        scale: Double,
        tx: (Double) -> Double, ty: (Double) -> Double,
        color: Color = Color.BLACK,
        strokeWidth: Double = GeometryUtils.STROKE_WIDTH,
        strokeStyle: String = "solid"
    ) {
        val arcs = GeometryUtils.clipCircleToArcs(cx, cy, radius, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
        if (arcs.isEmpty()) return

        val pixelRadius = radius * scale
        val pcx = tx(cx)
        val pcy = ty(cy)
        val isDashed = strokeStyle == "dashed"

        if (arcs.size == 1 && kotlin.math.abs(arcs[0][1] - 360.0) < 0.01) {
            val circle = FxCircle(pcx, pcy, pixelRadius)
            circle.fill = Color.TRANSPARENT
            circle.strokeWidth = strokeWidth
            circle.stroke = color
            if (isDashed) circle.strokeDashArray.addAll(6.0, 4.0)
            group.children.add(circle)
        } else {
            for (arcDef in arcs) {
                val arc = Arc(pcx, pcy, pixelRadius, pixelRadius, -arcDef[0], -arcDef[1])
                arc.type = ArcType.OPEN
                arc.fill = Color.TRANSPARENT
                arc.strokeWidth = strokeWidth
                arc.stroke = color
                if (isDashed) arc.strokeDashArray.addAll(6.0, 4.0)
                group.children.add(arc)
            }
        }
    }

    private fun addClippedCircleSvg(
        sb: StringBuilder,
        cx: Double, cy: Double, radius: Double,
        mathClipRect: DoubleArray,
        scale: Double,
        tx: (Double) -> Double, ty: (Double) -> Double,
        color: String = "black",
        strokeWidth: Double = GeometryUtils.STROKE_WIDTH,
        strokeStyle: String = "solid"
    ) {
        val arcs = GeometryUtils.clipCircleToArcs(cx, cy, radius, mathClipRect[0], mathClipRect[1], mathClipRect[2], mathClipRect[3])
        if (arcs.isEmpty()) return

        val cr = radius * scale
        val pcx = tx(cx)
        val pcy = ty(cy)
        val dashAttr = if (strokeStyle == "dashed") """ stroke-dasharray="6 4"""" else ""

        if (arcs.size == 1 && kotlin.math.abs(arcs[0][1] - 360.0) < 0.01) {
            sb.appendLine("""  <circle cx="${fmt(pcx)}" cy="${fmt(pcy)}" r="${fmt(cr)}" fill="none" stroke="$color" stroke-width="$strokeWidth"$dashAttr/>""")
        } else {
            for (arcDef in arcs) {
                val startRad = arcDef[0] * PI / 180.0
                val sweepRad = arcDef[1] * PI / 180.0
                val sx = cx + radius * cos(startRad)
                val sy = cy + radius * sin(startRad)
                val ex = cx + radius * cos(startRad + sweepRad)
                val ey = cy + radius * sin(startRad + sweepRad)
                val psx = tx(sx); val psy = ty(sy)
                val pex = tx(ex); val pey = ty(ey)
                val largeArc = if (arcDef[1] > 180.0) 1 else 0
                val sweepFlag = 0
                sb.appendLine("""  <path d="M ${fmt(psx)} ${fmt(psy)} A ${fmt(cr)} ${fmt(cr)} 0 $largeArc $sweepFlag ${fmt(pex)} ${fmt(pey)}" fill="none" stroke="$color" stroke-width="$strokeWidth"$dashAttr/>""")
            }
        }
    }

    // --- Shared helpers ---

    private fun isExportable(meta: ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked

    private fun fmt(v: Double): String = "%.2f".format(v)
}
