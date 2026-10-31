package euclideansketchpad.ui

import euclideansketchpad.geometry.*
import euclideansketchpad.geometry.GeometryUtils.ANGLE_ARC_RADIUS
import euclideansketchpad.model.GeometricObjectManager
import euclideansketchpad.model.ManagerState
import euclideansketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.ArcType
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.transform.Affine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

class AngleTool(
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

    private var pointAId: Int? = null
    private var pointAX: Double = 0.0
    private var pointAY: Double = 0.0

    private var vertexId: Int? = null
    private var vertexX: Double = 0.0
    private var vertexY: Double = 0.0

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

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        previewGroup.children.clear()
        val scale = abs(transform.mxx)
        val counterScale = if (scale > 0) 1.0 / scale else 1.0

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit is HitResult.IntersectionPreview) {
            val ghost = FxCircle(hit.candidate.x, hit.candidate.y, POINT_RADIUS * counterScale)
            ghost.fill = Color.BLACK; ghost.opacity = 0.4; ghost.isMouseTransparent = true
            previewGroup.children.add(ghost)
        }

        if (vertexId != null) {
            // Show ghost arc from vertex
            val targetX = resolveTargetX(hit, worldX)
            val targetY = resolveTargetY(hit, worldY)

            val arcRadius = ANGLE_ARC_RADIUS * counterScale
            val angleBA = atan2(pointAY - vertexY, pointAX - vertexX)
            val angleBC = atan2(targetY - vertexY, targetX - vertexX)
            val startDeg = -angleBA * 180.0 / PI // negate for y-flip
            var sweep = -(angleBC - angleBA) // clockwise
            if (sweep <= 0) sweep += 2 * PI
            val sweepDeg = sweep * 180.0 / PI // positive = CW visual after y-flip

            val arc = Arc(vertexX, vertexY, arcRadius, arcRadius, startDeg, sweepDeg)
            arc.type = ArcType.ROUND
            arc.fill = Color.web("#4CAF50", 0.2)
            arc.stroke = Color.web("#4CAF50", 0.5)
            arc.strokeWidth = 1.0 * counterScale
            arc.isMouseTransparent = true
            previewGroup.children.add(arc)
        }
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        val hit = hitTester.hitTest(e.x, e.y)

        if (pointAId == null) {
            preConstructionSnapshot = manager.snapshot()
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            val pt = manager.getObject(id) as? PointLike ?: return
            pointAId = id
            pointAX = pt.x
            pointAY = pt.y
        } else if (vertexId == null) {
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            if (id == pointAId) return
            val pt = manager.getObject(id) as? PointLike ?: return
            vertexId = id
            vertexX = pt.x
            vertexY = pt.y
        } else {
            val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }
            val id = ToolUtils.resolveOrCreatePoint(hit, worldX, worldY, manager, hitTester, requestRender, clipRect)
            if (id == vertexId || id == pointAId) return
            manager.addObject { oid -> Angle(oid, pointAId!!, vertexId!!, id) }
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
        pointAId = null
        vertexId = null
    }
}
