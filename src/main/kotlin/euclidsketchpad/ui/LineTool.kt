package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.geometry.GeometryUtils.ARROWHEAD_SIZE
import euclidsketchpad.geometry.GeometryUtils.LINE_EXTENT
import euclidsketchpad.geometry.GeometryUtils.POINT_RADIUS
import euclidsketchpad.geometry.GeometryUtils.STROKE_WIDTH
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ManagerState
import euclidsketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.transform.Affine
import kotlin.math.abs
import kotlin.math.hypot

enum class LineSubMode { LINE, RAY, SEGMENT }

class LineTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val previewGroup: Group,
    private val transform: Affine,
    private val requestRender: () -> Unit,
    private val viewportProvider: () -> DoubleArray? = { null }
) : Tool {

    var subMode: LineSubMode = LineSubMode.SEGMENT

    // First point state
    private var firstPointId: Int? = null
    private var firstX: Double = 0.0
    private var firstY: Double = 0.0
    private var preConstructionSnapshot: ManagerState? = null

    override fun activate() {
        reset()
    }

    override fun deactivate(previewGroup: Group) {
        if (preConstructionSnapshot != null) {
            manager.restore(preConstructionSnapshot!!)
            preConstructionSnapshot = null
            hitTester.rebuildIntersectionCache()
        }
        reset()
        previewGroup.children.clear()
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) = onMouseMoved(e, worldX, worldY)

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()
        val scale = abs(transform.mxx)
        val counterScale = if (scale > 0) 1.0 / scale else 1.0

        // Show ghost at intersection preview
        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val ghost = FxCircle(hit.candidate.x, hit.candidate.y, POINT_RADIUS * counterScale)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        if (firstPointId != null) {
            // Show preview line from first point to cursor
            val targetX = if (hit is HitResult.ConstructedPoint) {
                (manager.getObject(hit.id) as? PointLike)?.x ?: worldX
            } else if (hit is HitResult.IntersectionPreview) {
                hit.candidate.x
            } else worldX

            val targetY = if (hit is HitResult.ConstructedPoint) {
                (manager.getObject(hit.id) as? PointLike)?.y ?: worldY
            } else if (hit is HitResult.IntersectionPreview) {
                hit.candidate.y
            } else worldY

            drawPreviewLine(firstX, firstY, targetX, targetY, counterScale)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        val hit = hitTester.hitTest(e.x, e.y)

        if (firstPointId == null) {
            // First click: set first point
            preConstructionSnapshot = manager.snapshot()
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            val pt = manager.getObject(pointId) as? PointLike ?: return
            firstPointId = pointId
            firstX = pt.x
            firstY = pt.y
        } else {
            // Second click: set second point and create linear object
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val secondPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            if (secondPointId == firstPointId) return // same point, ignore

            when (subMode) {
                LineSubMode.LINE -> manager.addObject { id -> Line(id, firstPointId!!, secondPointId) }
                LineSubMode.RAY -> manager.addObject { id -> Ray(id, firstPointId!!, secondPointId) }
                LineSubMode.SEGMENT -> manager.addObject { id -> Segment(id, firstPointId!!, secondPointId) }
            }
            preConstructionSnapshot = null
            reset()
            hitTester.rebuildIntersectionCache()
            undoManager.checkpoint()
            requestRender()
        }
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

    private fun drawPreviewLine(x1: Double, y1: Double, x2: Double, y2: Double, counterScale: Double) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len == 0.0) return

        // Compute clip rect (viewport with 5% margin) matching GeometryRenderer
        val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }

        when (subMode) {
            LineSubMode.SEGMENT -> {
                val line = FxLine(x1, y1, x2, y2)
                line.strokeWidth = STROKE_WIDTH * counterScale
                line.stroke = Color.BLACK
                line.opacity = 0.4
                line.isMouseTransparent = true
                previewGroup.children.add(line)
            }
            LineSubMode.RAY -> {
                val ux = dx / len; val uy = dy / len
                val ex = x1 + ux * LINE_EXTENT; val ey = y1 + uy * LINE_EXTENT
                val clipped = clipRect?.let { GeometryUtils.clipLineToRect(x1, y1, ex, ey, it[0], it[1], it[2], it[3]) }
                if (clipped != null) {
                    val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                    line.strokeWidth = STROKE_WIDTH * counterScale
                    line.stroke = Color.BLACK
                    line.opacity = 0.4
                    line.isMouseTransparent = true
                    previewGroup.children.add(line)
                    val cdx = clipped[2] - clipped[0]; val cdy = clipped[3] - clipped[1]
                    val clen = hypot(cdx, cdy)
                    if (clen > 0) drawPreviewArrowhead(clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale)
                } else {
                    val line = FxLine(x1, y1, ex, ey)
                    line.strokeWidth = STROKE_WIDTH * counterScale
                    line.stroke = Color.BLACK
                    line.opacity = 0.4
                    line.isMouseTransparent = true
                    previewGroup.children.add(line)
                }
            }
            LineSubMode.LINE -> {
                val ux = dx / len; val uy = dy / len
                val ex1 = x1 - ux * LINE_EXTENT; val ey1 = y1 - uy * LINE_EXTENT
                val ex2 = x1 + ux * LINE_EXTENT; val ey2 = y1 + uy * LINE_EXTENT
                val clipped = clipRect?.let { GeometryUtils.clipLineToRect(ex1, ey1, ex2, ey2, it[0], it[1], it[2], it[3]) }
                if (clipped != null) {
                    val line = FxLine(clipped[0], clipped[1], clipped[2], clipped[3])
                    line.strokeWidth = STROKE_WIDTH * counterScale
                    line.stroke = Color.BLACK
                    line.opacity = 0.4
                    line.isMouseTransparent = true
                    previewGroup.children.add(line)
                    val cdx = clipped[2] - clipped[0]; val cdy = clipped[3] - clipped[1]
                    val clen = hypot(cdx, cdy)
                    if (clen > 0) {
                        drawPreviewArrowhead(clipped[2], clipped[3], cdx / clen, cdy / clen, counterScale)
                        drawPreviewArrowhead(clipped[0], clipped[1], -cdx / clen, -cdy / clen, counterScale)
                    }
                } else {
                    val line = FxLine(ex1, ey1, ex2, ey2)
                    line.strokeWidth = STROKE_WIDTH * counterScale
                    line.stroke = Color.BLACK
                    line.opacity = 0.4
                    line.isMouseTransparent = true
                    previewGroup.children.add(line)
                }
            }
        }

        // Ghost endpoints
        val ghost1 = FxCircle(x1, y1, POINT_RADIUS * counterScale)
        ghost1.fill = Color.BLACK; ghost1.opacity = 0.4; ghost1.isMouseTransparent = true
        val ghost2 = FxCircle(x2, y2, POINT_RADIUS * counterScale)
        ghost2.fill = Color.BLACK; ghost2.opacity = 0.4; ghost2.isMouseTransparent = true
        previewGroup.children.addAll(ghost1, ghost2)
    }

    private fun drawPreviewArrowhead(tipX: Double, tipY: Double, dirX: Double, dirY: Double, counterScale: Double) {
        val v = GeometryUtils.computeArrowheadVertices(tipX, tipY, dirX, dirY, ARROWHEAD_SIZE * counterScale)
        val arrow = javafx.scene.shape.Polygon(v[0], v[1], v[2], v[3], v[4], v[5])
        arrow.fill = Color.BLACK
        arrow.stroke = Color.BLACK
        arrow.opacity = 0.4
        arrow.isMouseTransparent = true
        previewGroup.children.add(arrow)
    }


    private fun reset() {
        firstPointId = null
    }
}
