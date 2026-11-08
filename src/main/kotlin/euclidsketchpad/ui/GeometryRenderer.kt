package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.geometry.GeometryUtils.ANGLE_ARC_RADIUS
import euclidsketchpad.geometry.GeometryUtils.ARROWHEAD_SIZE
import euclidsketchpad.geometry.GeometryUtils.LINE_EXTENT
import euclidsketchpad.geometry.GeometryUtils.POINT_RADIUS
import euclidsketchpad.geometry.GeometryUtils.STROKE_WIDTH
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.getObjectAnchor
import javafx.scene.Group
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.Polygon as FxPolygon
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.transform.Affine
import kotlin.math.PI
import kotlin.math.abs

private val SELECTED_COLOR = Color.web("#2196F3")
private val HOVER_COLOR = Color.web("#2196F3")
private val REGION_FILL = Color.web("#FFEB3B", 0.3)
private val ANGLE_FILL = Color.web("#4CAF50", 0.3)
private val PREVIEW_COLOR = Color.web("#FF5722")
private const val PREVIEW_OPACITY = 0.4

object GeometryRenderer {

    private fun isRenderable(meta: euclidsketchpad.model.ObjectMetadata): Boolean =
        meta.visible && !meta.isLocked

    fun renderAll(
        manager: GeometricObjectManager,
        group: Group,
        transform: Affine,
        selectedIds: Set<Int> = emptySet(),
        hoveredId: Int? = null,
        viewportBounds: DoubleArray? = null
    ) {
        group.children.clear()
        val scale = transform.mxx
        val counterScale = if (scale != 0.0) 1.0 / scale else 1.0

        // Clip rect for line endpoints and arrowheads (viewport with 5% margin inset)
        val clipRect = viewportBounds?.let { GeometryUtils.computeClipRect(it) }

        // Pass 1: Regions (bottom)
        for (obj in manager.allObjects()) {
            if (obj !is Region || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId

            if (obj.vertices.size >= 3) {
                val coords = DoubleArray(obj.vertices.size * 2)
                for (i in obj.vertices.indices) {
                    coords[i * 2] = obj.vertices[i].first
                    coords[i * 2 + 1] = obj.vertices[i].second
                }
                val polygon = FxPolygon(*coords)
                polygon.fill = when {
                    isSelected || isHovered -> Color.web("#2196F3", 0.4)
                    else -> REGION_FILL
                }
                polygon.stroke = null
                polygon.strokeWidth = 0.0
                polygon.styleClass.add("region")
                // Color set programmatically; no CSS class needed
                group.children.add(polygon)
            }
        }

        // Pass 2: Angles
        for (obj in manager.allObjects()) {
            if (obj !is Angle || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId

            val arcRadius = ANGLE_ARC_RADIUS * counterScale
            // JavaFX Arc angles are in degrees, measured counter-clockwise from positive x-axis
            // But our world group has y-flipped (myy=-1), so JavaFX CCW becomes CW visually
            // Our startAngleRadians is the angle of ray BA, sweepAngleRadians is the CW sweep
            // In JavaFX coords (with y-flip), CW sweep = negative JavaFX sweep
            val startDeg = -obj.startAngleRadians * 180.0 / PI // negate for y-flip
            val sweepDeg = obj.sweepAngleRadians * 180.0 / PI // positive = CW visual after y-flip

            val arc = Arc(obj.vertexX, obj.vertexY, arcRadius, arcRadius, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = when {
                isSelected || isHovered -> Color.web("#2196F3", 0.5)
                else -> ANGLE_FILL
            }
            arc.stroke = if (isSelected) SELECTED_COLOR else if (isHovered) HOVER_COLOR else Color.web("#4CAF50", 0.7)
            arc.strokeWidth = 1.0 * counterScale
            arc.styleClass.add("angle")
            // Color set programmatically; no CSS class needed
            group.children.add(arc)
        }

        // Pass 3: Lines/Rays/Segments/Circles
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta) || !obj.isDefined) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            val color = when {
                isSelected -> SELECTED_COLOR
                isHovered -> HOVER_COLOR
                else -> Color.BLACK
            }
            val strokeWidth = if (isHovered && !isSelected) STROKE_WIDTH * 1.5 * counterScale else STROKE_WIDTH * counterScale

            if (obj is LinearLike) {
                val dx = obj.x2 - obj.x1
                val dy = obj.y2 - obj.y1
                val len = kotlin.math.hypot(dx, dy)
                if (len == 0.0) continue
                val ux = dx / len
                val uy = dy / len

                when (obj.linearType) {
                    LinearType.SEGMENT -> {
                        val line = FxLine(obj.x1, obj.y1, obj.x2, obj.y2)
                        line.strokeWidth = strokeWidth
                        line.stroke = color
                        line.styleClass.add("segment")
                        group.children.add(line)
                    }
                    LinearType.LINE -> {
                        val ex1 = obj.x1 - ux * LINE_EXTENT
                        val ey1 = obj.y1 - uy * LINE_EXTENT
                        val ex2 = obj.x1 + ux * LINE_EXTENT
                        val ey2 = obj.y1 + uy * LINE_EXTENT
                        val clipped = clipRect?.let { GeometryUtils.clipLineToRect(ex1, ey1, ex2, ey2, it[0], it[1], it[2], it[3]) }
                        if (clipped != null) {
                            val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.styleClass.add("line")
                            group.children.add(line)
                            val cdx = clipped[2] - clipped[0]
                            val cdy = clipped[3] - clipped[1]
                            val clen = kotlin.math.hypot(cdx, cdy)
                            if (clen > 0) {
                                drawArrowhead(group, clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale, color)
                                drawArrowhead(group, clipped[0], clipped[1], -cdx / clen, -cdy / clen, counterScale, color)
                            }
                        } else {
                            val line = FxLine(ex1, ey1, ex2, ey2)
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.styleClass.add("line")
                            group.children.add(line)
                            if (clipRect == null) {
                                drawArrowhead(group, ex2, ey2, ux, uy, counterScale, color)
                                drawArrowhead(group, ex1, ey1, -ux, -uy, counterScale, color)
                            }
                        }
                    }
                    LinearType.RAY -> {
                        val ex = obj.x1 + ux * LINE_EXTENT
                        val ey = obj.y1 + uy * LINE_EXTENT
                        val clipped = clipRect?.let { GeometryUtils.clipLineToRect(obj.x1, obj.y1, ex, ey, it[0], it[1], it[2], it[3]) }
                        if (clipped != null) {
                            val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.styleClass.add("ray")
                            group.children.add(line)
                            val cdx = clipped[2] - clipped[0]
                            val cdy = clipped[3] - clipped[1]
                            val clen = kotlin.math.hypot(cdx, cdy)
                            if (clen > 0) {
                                drawArrowhead(group, clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale, color)
                            }
                        } else {
                            val line = FxLine(obj.x1, obj.y1, ex, ey)
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.styleClass.add("ray")
                            group.children.add(line)
                            if (clipRect == null) {
                                drawArrowhead(group, ex, ey, ux, uy, counterScale, color)
                            }
                        }
                    }
                }
            } else if (obj is Circle) {
                addClippedCircle(group, obj.cx, obj.cy, obj.radius, strokeWidth, color, clipRect)
            }
        }

        // Pass 4: Points on top
        for (obj in manager.allObjects()) {
            if (obj !is PointLike || !obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta)) continue
            val isSelected = obj.id in selectedIds
            val isHovered = obj.id == hoveredId
            val color = when {
                isSelected -> SELECTED_COLOR
                isHovered -> HOVER_COLOR
                else -> Color.BLACK
            }
            val radius = if (isHovered && !isSelected) POINT_RADIUS * 1.3 * counterScale else POINT_RADIUS * counterScale
            val ptCircle = FxCircle(obj.x, obj.y, radius)
            ptCircle.fill = color
            ptCircle.styleClass.add("point")
            group.children.add(ptCircle)
        }

        // Pass 5: Labels
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!isRenderable(meta) || !meta.labelVisible || meta.label == null) continue
            if (!obj.isDefined) continue

            val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: continue
            val text = Text(meta.label)
            text.font = Font.font(GeometryUtils.FONT_SIZE * counterScale)
            // Position with pixel offset converted to world coords
            text.x = anchorX + meta.labelOffsetX * counterScale
            text.y = anchorY + meta.labelOffsetY * counterScale
            // Counter the y-flip so text isn't mirrored
            text.scaleY = -1.0
            text.fill = Color.BLACK
            text.styleClass.add("label")
            group.children.add(text)
        }
    }

    fun renderPreviewObjects(
        objects: List<GeometricObject>,
        group: Group,
        transform: Affine,
        viewportBounds: DoubleArray? = null
    ) {
        val scale = transform.mxx
        val counterScale = if (scale != 0.0) 1.0 / scale else 1.0
        val strokeWidth = STROKE_WIDTH * counterScale
        val color = PREVIEW_COLOR

        val clipRect = viewportBounds?.let { GeometryUtils.computeClipRect(it) }

        // Pass 1: Regions (bottom)
        for (obj in objects) {
            if (obj !is Region || !obj.isDefined) continue
            if (obj.vertices.size >= 3) {
                val coords = DoubleArray(obj.vertices.size * 2)
                for (i in obj.vertices.indices) {
                    coords[i * 2] = obj.vertices[i].first
                    coords[i * 2 + 1] = obj.vertices[i].second
                }
                val polygon = FxPolygon(*coords)
                polygon.fill = Color.web("#FF5722", 0.2)
                polygon.stroke = null
                polygon.opacity = PREVIEW_OPACITY
                polygon.isMouseTransparent = true
                group.children.add(polygon)
            }
        }

        // Pass 2: Angles
        for (obj in objects) {
            if (obj !is Angle || !obj.isDefined) continue
            val arcRadius = ANGLE_ARC_RADIUS * counterScale
            val startDeg = -obj.startAngleRadians * 180.0 / PI
            val sweepDeg = obj.sweepAngleRadians * 180.0 / PI
            val arc = Arc(obj.vertexX, obj.vertexY, arcRadius, arcRadius, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = Color.web("#FF5722", 0.2)
            arc.stroke = color
            arc.strokeWidth = 1.0 * counterScale
            arc.opacity = PREVIEW_OPACITY
            arc.isMouseTransparent = true
            group.children.add(arc)
        }

        // Pass 3: Lines/Rays/Segments/Circles
        for (obj in objects) {
            if (!obj.isDefined) continue
            if (obj is LinearLike) {
                val dx = obj.x2 - obj.x1
                val dy = obj.y2 - obj.y1
                val len = kotlin.math.hypot(dx, dy)
                if (len == 0.0) continue
                val ux = dx / len
                val uy = dy / len

                when (obj.linearType) {
                    LinearType.SEGMENT -> {
                        val line = FxLine(obj.x1, obj.y1, obj.x2, obj.y2)
                        line.strokeWidth = strokeWidth
                        line.stroke = color
                        line.opacity = PREVIEW_OPACITY
                        line.isMouseTransparent = true
                        group.children.add(line)
                    }
                    LinearType.LINE -> {
                        val ex1 = obj.x1 - ux * LINE_EXTENT
                        val ey1 = obj.y1 - uy * LINE_EXTENT
                        val ex2 = obj.x1 + ux * LINE_EXTENT
                        val ey2 = obj.y1 + uy * LINE_EXTENT
                        val clipped = clipRect?.let { GeometryUtils.clipLineToRect(ex1, ey1, ex2, ey2, it[0], it[1], it[2], it[3]) }
                        if (clipped != null) {
                            val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.opacity = PREVIEW_OPACITY
                            line.isMouseTransparent = true
                            group.children.add(line)
                            val cdx = clipped[2] - clipped[0]
                            val cdy = clipped[3] - clipped[1]
                            val clen = kotlin.math.hypot(cdx, cdy)
                            if (clen > 0) {
                                drawArrowhead(group, clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale, color, PREVIEW_OPACITY)
                                drawArrowhead(group, clipped[0], clipped[1], -cdx / clen, -cdy / clen, counterScale, color, PREVIEW_OPACITY)
                            }
                        } else {
                            val line = FxLine(ex1, ey1, ex2, ey2)
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.opacity = PREVIEW_OPACITY
                            line.isMouseTransparent = true
                            group.children.add(line)
                            if (clipRect == null) {
                                drawArrowhead(group, ex2, ey2, ux, uy, counterScale, color, PREVIEW_OPACITY)
                                drawArrowhead(group, ex1, ey1, -ux, -uy, counterScale, color, PREVIEW_OPACITY)
                            }
                        }
                    }
                    LinearType.RAY -> {
                        val ex = obj.x1 + ux * LINE_EXTENT
                        val ey = obj.y1 + uy * LINE_EXTENT
                        val clipped = clipRect?.let { GeometryUtils.clipLineToRect(obj.x1, obj.y1, ex, ey, it[0], it[1], it[2], it[3]) }
                        if (clipped != null) {
                            val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.opacity = PREVIEW_OPACITY
                            line.isMouseTransparent = true
                            group.children.add(line)
                            val cdx = clipped[2] - clipped[0]
                            val cdy = clipped[3] - clipped[1]
                            val clen = kotlin.math.hypot(cdx, cdy)
                            if (clen > 0) {
                                drawArrowhead(group, clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale, color, PREVIEW_OPACITY)
                            }
                        } else {
                            val line = FxLine(obj.x1, obj.y1, ex, ey)
                            line.strokeWidth = strokeWidth
                            line.stroke = color
                            line.opacity = PREVIEW_OPACITY
                            line.isMouseTransparent = true
                            group.children.add(line)
                            if (clipRect == null) {
                                drawArrowhead(group, ex, ey, ux, uy, counterScale, color, PREVIEW_OPACITY)
                            }
                        }
                    }
                }
            } else if (obj is Circle) {
                addClippedCircle(group, obj.cx, obj.cy, obj.radius, strokeWidth, color, clipRect, PREVIEW_OPACITY, true)
            }
        }

        // Pass 4: Points on top
        for (obj in objects) {
            if (obj !is PointLike || !obj.isDefined) continue
            val radius = POINT_RADIUS * counterScale
            val ptCircle = FxCircle(obj.x, obj.y, radius)
            ptCircle.fill = color
            ptCircle.opacity = PREVIEW_OPACITY
            ptCircle.isMouseTransparent = true
            group.children.add(ptCircle)
        }
    }

    /**
     * Render a circle clipped to the given clip rect. If no clip rect, renders full circle.
     * Uses JavaFX Arc nodes for partial arcs.
     */
    private fun addClippedCircle(
        group: Group,
        cx: Double, cy: Double, radius: Double,
        strokeWidth: Double, color: Color,
        clipRect: DoubleArray?,
        opacity: Double = 1.0,
        mouseTransparent: Boolean = false
    ) {
        if (clipRect != null) {
            val arcs = GeometryUtils.clipCircleToArcs(cx, cy, radius, clipRect[0], clipRect[1], clipRect[2], clipRect[3])
            if (arcs.isEmpty()) return
            // Check if it's a full circle (single arc with 360 sweep)
            if (arcs.size == 1 && abs(arcs[0][1] - 360.0) < 0.01) {
                val circle = FxCircle(cx, cy, radius)
                circle.fill = Color.TRANSPARENT
                circle.strokeWidth = strokeWidth
                circle.stroke = color
                if (opacity < 1.0) circle.opacity = opacity
                if (mouseTransparent) circle.isMouseTransparent = true
                group.children.add(circle)
            } else {
                for (arcDef in arcs) {
                    // clipCircleToArcs returns angles in math convention (CCW from +x).
                    // In the y-flipped world group, JavaFX Arc angles need negation.
                    val arc = Arc(cx, cy, radius, radius, -arcDef[0], -arcDef[1])
                    arc.type = ArcType.OPEN
                    arc.fill = Color.TRANSPARENT
                    arc.strokeWidth = strokeWidth
                    arc.stroke = color
                    if (opacity < 1.0) arc.opacity = opacity
                    if (mouseTransparent) arc.isMouseTransparent = true
                    group.children.add(arc)
                }
            }
        } else {
            val circle = FxCircle(cx, cy, radius)
            circle.fill = Color.TRANSPARENT
            circle.strokeWidth = strokeWidth
            circle.stroke = color
            if (opacity < 1.0) circle.opacity = opacity
            if (mouseTransparent) circle.isMouseTransparent = true
            group.children.add(circle)
        }
    }

    private fun drawArrowhead(
        group: Group,
        tipX: Double, tipY: Double,
        dirX: Double, dirY: Double,
        counterScale: Double, color: Color,
        opacity: Double = 1.0
    ) {
        val v = GeometryUtils.computeArrowheadVertices(tipX, tipY, dirX, dirY, ARROWHEAD_SIZE * counterScale)
        val arrow = FxPolygon(v[0], v[1], v[2], v[3], v[4], v[5])
        arrow.fill = color
        arrow.stroke = color
        arrow.isMouseTransparent = true
        if (opacity < 1.0) arrow.opacity = opacity
        group.children.add(arrow)
    }

}
