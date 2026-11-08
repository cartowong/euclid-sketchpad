package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.transform.Affine
import kotlin.math.abs
import kotlin.math.atan2

class PointTool(
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
    }

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()
        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val scale = abs(transform.mxx)
            val counterScale = if (scale > 0) 1.0 / scale else 1.0
            val ghost = FxCircle(hit.candidate.x, hit.candidate.y, POINT_RADIUS * counterScale)
            ghost.fill = Color.BLACK
            ghost.opacity = 0.4
            ghost.styleClass.add("point")
            ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()
        val hit = hitTester.hitTest(e.x, e.y)
        when (hit) {
            is HitResult.ConstructedPoint -> {
                // Already a point here, no-op
            }
            is HitResult.IntersectionPreview -> {
                ToolUtils.constructIntersection(hit.candidate, manager, hitTester, requestRender)
            }
            is HitResult.OnLinear -> {
                createSemiFreePontOnLinear(hit.id, worldX, worldY)
            }
            is HitResult.OnCircle -> {
                createSemiFreePointOnCircle(hit.id, worldX, worldY)
            }
            else -> {
                val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
                val px: Double
                val py: Double
                if (clipRect != null) {
                    val clamped = GeometryUtils.clampToRect(worldX, worldY, clipRect)
                    px = clamped[0]; py = clamped[1]
                } else {
                    px = worldX; py = worldY
                }
                manager.addObject { id -> FreePoint(id, px, py) }
            }
        }
        hitTester.rebuildIntersectionCache()
        undoManager.checkpoint()
        requestRender()
    }

    private fun createSemiFreePontOnLinear(linearId: Int, worldX: Double, worldY: Double) {
        val obj = manager.getObject(linearId) ?: return
        val lin = obj as? LinearLike ?: return
        val data = IntersectionUtils.extractLinear(obj) ?: return
        val t = GeometryUtils.closestParameterOnLine(worldX, worldY, data.x1, data.y1, data.x2, data.y2)
        when (lin.linearType) {
            LinearType.LINE -> manager.addObject { id -> FreePointOnLine(id, linearId, t) }
            LinearType.RAY -> manager.addObject { id -> FreePointOnRay(id, linearId, t.coerceAtLeast(0.0)) }
            LinearType.SEGMENT -> manager.addObject { id -> FreePointOnSegment(id, linearId, t.coerceIn(0.0, 1.0)) }
        }
    }

    private fun createSemiFreePointOnCircle(circleId: Int, worldX: Double, worldY: Double) {
        val cir = manager.getObject(circleId) as? Circle ?: return
        val theta = atan2(worldY - cir.cy, worldX - cir.cx)
        manager.addObject { id -> FreePointOnCircle(id, circleId, theta) }
    }
}
