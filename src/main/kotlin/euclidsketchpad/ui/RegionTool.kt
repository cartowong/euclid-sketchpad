package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ManagerState
import euclidsketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.shape.Polygon as FxPolygon
import javafx.scene.transform.Affine
import kotlin.math.abs
import kotlin.math.hypot

class RegionTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val transform: Affine,
    private val requestRender: () -> Unit,
    private val viewportProvider: () -> DoubleArray? = { null }
) : Tool {

    private companion object {
        const val POINT_RADIUS = 4.0
        const val STROKE_WIDTH = 2.0
    }

    private val vertexIds = mutableListOf<Int>()
    private val vertexPositions = mutableListOf<Pair<Double, Double>>()
    private var preConstructionSnapshot: ManagerState? = null

    override fun activate() {
        reset()
    }

    override fun deactivate(previewGroup: Group) {
        reset()
        previewGroup.children.clear()
    }

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()
        if (vertexIds.isEmpty()) return

        val scale = abs(transform.mxx)
        val counterScale = if (scale > 0) 1.0 / scale else 1.0
        val snap = if (scale > 0) 10.0 / scale else 10.0

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val ghost = FxCircle(hit.candidate.x, hit.candidate.y, POINT_RADIUS * counterScale)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        val targetX = resolveTargetX(hit, worldX)
        val targetY = resolveTargetY(hit, worldY)

        // Check if near first point (closing)
        val firstPos = vertexPositions[0]
        val distToFirst = hypot(targetX - firstPos.first, targetY - firstPos.second)
        val isClosing = vertexIds.size >= 3 && distToFirst < snap

        // Draw edges
        for (i in 0 until vertexPositions.size - 1) {
            val (x1, y1) = vertexPositions[i]
            val (x2, y2) = vertexPositions[i + 1]
            val line = FxLine(x1, y1, x2, y2)
            line.strokeWidth = STROKE_WIDTH * counterScale
            line.stroke = Color.web("#2196F3")
            line.opacity = 0.4
            line.isMouseTransparent = true
            previewGroup.children.add(line)
        }

        // Preview edge to cursor or closing point
        val lastPos = vertexPositions.last()
        val endX = if (isClosing) firstPos.first else targetX
        val endY = if (isClosing) firstPos.second else targetY
        val previewLine = FxLine(lastPos.first, lastPos.second, endX, endY)
        previewLine.strokeWidth = STROKE_WIDTH * counterScale
        previewLine.stroke = Color.web("#2196F3")
        previewLine.opacity = 0.4
        previewLine.isMouseTransparent = true
        previewGroup.children.add(previewLine)

        // Highlight region and first point if closing
        if (isClosing) {
            val coords = DoubleArray(vertexPositions.size * 2)
            for (i in vertexPositions.indices) {
                coords[i * 2] = vertexPositions[i].first
                coords[i * 2 + 1] = vertexPositions[i].second
            }
            val polygon = FxPolygon(*coords)
            polygon.fill = Color.web("#FFEB3B", 0.3)
            polygon.stroke = null
            polygon.isMouseTransparent = true
            previewGroup.children.add(polygon)

            val highlight = FxCircle(firstPos.first, firstPos.second, POINT_RADIUS * 1.5 * counterScale)
            highlight.fill = Color.TRANSPARENT
            highlight.stroke = Color.web("#2196F3")
            highlight.strokeWidth = 2.0 * counterScale
            highlight.isMouseTransparent = true
            previewGroup.children.add(highlight)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        val scale = abs(transform.mxx)
        val snap = if (scale > 0) 10.0 / scale else 10.0
        val hit = hitTester.hitTest(e.x, e.y)

        val targetX = resolveTargetX(hit, worldX)
        val targetY = resolveTargetY(hit, worldY)

        // Check for closing
        if (vertexIds.size >= 3) {
            val firstPos = vertexPositions[0]
            val distToFirst = hypot(targetX - firstPos.first, targetY - firstPos.second)
            if (distToFirst < snap) {
                // Close polygon: append first point ID to create closed polygon
                val pointIds = vertexIds.toMutableList()
                pointIds.add(vertexIds[0])
                manager.addObject { id -> Region(id, pointIds) }
                preConstructionSnapshot = null
                reset()
                hitTester.rebuildIntersectionCache()
                undoManager.checkpoint()
                requestRender()
                previewGroup.children.clear()
                return
            }
        }

        if (vertexIds.isEmpty()) {
            preConstructionSnapshot = manager.snapshot()
        }

        val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
        val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
        val pt = manager.getObject(pointId) as? PointLike ?: return
        vertexIds.add(pointId)
        vertexPositions.add(Pair(pt.x, pt.y))
        previewGroup.children.clear()
    }

    override fun onEscapePressed(previewGroup: Group): Boolean {
        val hadWork = preConstructionSnapshot != null
        if (hadWork) {
            manager.restore(preConstructionSnapshot!!)
            preConstructionSnapshot = null
            hitTester.rebuildIntersectionCache()
        }
        reset()
        previewGroup.children.clear()
        requestRender()
        return hadWork
    }

    private fun resolveTargetX(hit: HitResult?, worldX: Double): Double = when (hit) {
        is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.x ?: worldX
        is HitResult.IntersectionPreview -> hit.candidate.x
        else -> worldX
    }

    private fun resolveTargetY(hit: HitResult?, worldY: Double): Double = when (hit) {
        is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.y ?: worldY
        is HitResult.IntersectionPreview -> hit.candidate.y
        else -> worldY
    }

    private fun reset() {
        vertexIds.clear()
        vertexPositions.clear()
    }
}
