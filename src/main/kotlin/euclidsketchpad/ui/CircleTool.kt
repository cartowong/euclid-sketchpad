package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ManagerState
import euclidsketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.transform.Affine
import kotlin.math.abs
import kotlin.math.hypot

class CircleTool(
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

    private var centerPointId: Int? = null
    private var centerX: Double = 0.0
    private var centerY: Double = 0.0
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

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val ghost = FxCircle(hit.candidate.x, hit.candidate.y, POINT_RADIUS * counterScale)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        if (centerPointId != null) {
            val targetX = when (hit) {
                is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.x ?: worldX
                is HitResult.IntersectionPreview -> hit.candidate.x
                else -> worldX
            }
            val targetY = when (hit) {
                is HitResult.ConstructedPoint -> (manager.getObject(hit.id) as? PointLike)?.y ?: worldY
                is HitResult.IntersectionPreview -> hit.candidate.y
                else -> worldY
            }
            val radius = hypot(targetX - centerX, targetY - centerY)
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            if (clipRect != null) {
                val arcs = GeometryUtils.clipCircleToArcs(centerX, centerY, radius, clipRect[0], clipRect[1], clipRect[2], clipRect[3])
                if (arcs.size == 1 && kotlin.math.abs(arcs[0][1] - 360.0) < 0.01) {
                    val circle = FxCircle(centerX, centerY, radius)
                    circle.fill = Color.TRANSPARENT
                    circle.strokeWidth = STROKE_WIDTH * counterScale
                    circle.stroke = Color.BLACK
                    circle.opacity = 0.4
                    circle.isMouseTransparent = true
                    previewGroup.children.add(circle)
                } else {
                    for (arcDef in arcs) {
                        val arc = Arc(centerX, centerY, radius, radius, -arcDef[0], -arcDef[1])
                        arc.type = ArcType.OPEN
                        arc.fill = Color.TRANSPARENT
                        arc.strokeWidth = STROKE_WIDTH * counterScale
                        arc.stroke = Color.BLACK
                        arc.opacity = 0.4
                        arc.isMouseTransparent = true
                        previewGroup.children.add(arc)
                    }
                }
            } else {
                val circle = FxCircle(centerX, centerY, radius)
                circle.fill = Color.TRANSPARENT
                circle.strokeWidth = STROKE_WIDTH * counterScale
                circle.stroke = Color.BLACK
                circle.opacity = 0.4
                circle.isMouseTransparent = true
                previewGroup.children.add(circle)
            }

            // Ghost center and radius points
            val ghostCenter = FxCircle(centerX, centerY, POINT_RADIUS * counterScale)
            ghostCenter.fill = Color.BLACK; ghostCenter.opacity = 0.4; ghostCenter.isMouseTransparent = true
            val ghostRadius = FxCircle(targetX, targetY, POINT_RADIUS * counterScale)
            ghostRadius.fill = Color.BLACK; ghostRadius.opacity = 0.4; ghostRadius.isMouseTransparent = true
            previewGroup.children.addAll(ghostCenter, ghostRadius)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        val hit = hitTester.hitTest(e.x, e.y)

        if (centerPointId == null) {
            preConstructionSnapshot = manager.snapshot()
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val pointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            val pt = manager.getObject(pointId) as? PointLike ?: return
            centerPointId = pointId
            centerX = pt.x
            centerY = pt.y
        } else {
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val radiusPointId = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            if (radiusPointId == centerPointId) return
            manager.addObject { id -> Circle(id, centerPointId!!, radiusPointId) }
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

    private fun reset() {
        centerPointId = null
    }
}
