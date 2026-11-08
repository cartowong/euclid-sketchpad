package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ObjectMetadata
import euclidsketchpad.model.UndoManager
import javafx.scene.input.MouseEvent
import javafx.scene.transform.Affine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val MAX_LABEL_OFFSET = 30.0

class LabelTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val transform: Affine,
    private val canvasPane: CanvasPane,
    private val requestRender: () -> Unit
) : Tool {

    private var dragObjectId: Int? = null
    private var grabOffsetX = 0.0
    private var grabOffsetY = 0.0

    override fun deactivate(previewGroup: javafx.scene.Group) {
        canvasPane.suppressHover = false
        super.deactivate(previewGroup)
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        // First, check if clicking on an existing label
        val labelHit = hitTestLabel(e.x, e.y)
        if (labelHit != null) {
            val obj = manager.getObject(labelHit) ?: return
            val meta = manager.getMetadata(labelHit) ?: return
            val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: return
            val anchorScreen = transform.transform(anchorX, anchorY)
            val labelScreenX = anchorScreen.x + meta.labelOffsetX
            val labelScreenY = anchorScreen.y - meta.labelOffsetY

            dragObjectId = labelHit
            grabOffsetX = e.x - labelScreenX
            grabOffsetY = e.y - labelScreenY
            return
        }

        // Fall through to existing toggle logic
        val hit = hitTester.hitTest(e.x, e.y) ?: return
        val hitId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnLinear -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            is HitResult.IntersectionPreview -> return
        }

        val meta = manager.getMetadata(hitId) ?: return

        if (meta.label == null) {
            // Assign default label
            meta.label = manager.nextDefaultLabel()
            meta.labelVisible = true
            // For circles, store the angle from center to click point
            val obj = manager.getObject(hitId)
            if (obj is Circle && obj.isDefined) {
                meta.labelAnchorAngle = atan2(worldY - obj.cy, worldX - obj.cx)
            }
        } else if (meta.labelVisible) {
            // Hide label
            meta.labelVisible = false
        } else {
            // Re-show hidden label
            meta.labelVisible = true
        }
        undoManager.checkpoint()
        requestRender()
    }

    override fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {
        // Suppress geometric hover when cursor is near a label
        canvasPane.suppressHover = hitTestLabel(e.x, e.y) != null
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) {
        val id = dragObjectId ?: return
        val meta = manager.getMetadata(id) ?: return
        val obj = manager.getObject(id) ?: return

        // For sliding objects, update anchor to follow cursor
        updateAnchor(obj, meta, worldX, worldY)

        // Compute offset from anchor screen position to target label position
        val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: return
        val anchorScreen = transform.transform(anchorX, anchorY)
        val targetLabelX = e.x - grabOffsetX
        val targetLabelY = e.y - grabOffsetY
        meta.labelOffsetX = (targetLabelX - anchorScreen.x).coerceIn(-MAX_LABEL_OFFSET, MAX_LABEL_OFFSET)
        meta.labelOffsetY = (anchorScreen.y - targetLabelY).coerceIn(-MAX_LABEL_OFFSET, MAX_LABEL_OFFSET)

        canvasPane.suppressHover = true
        requestRender()
    }

    override fun onMouseReleased(e: MouseEvent, worldX: Double, worldY: Double) {
        if (dragObjectId != null) {
            undoManager.checkpoint()
        }
        dragObjectId = null
    }

    private fun updateAnchor(obj: GeometricObject, meta: ObjectMetadata, worldX: Double, worldY: Double) {
        if (obj is Circle) {
            meta.labelAnchorAngle = atan2(worldY - obj.cy, worldX - obj.cx)
        } else if (obj is LinearLike) {
            val (clampLow, clampHigh) = when (obj.linearType) {
                LinearType.LINE -> Pair(false, false)
                LinearType.RAY -> Pair(true, false)
                LinearType.SEGMENT -> Pair(true, true)
            }
            updateLinearAnchor(obj.x1, obj.y1, obj.x2, obj.y2, meta, worldX, worldY, clampLow, clampHigh)
        }
    }

    private fun updateLinearAnchor(
        x1: Double, y1: Double, x2: Double, y2: Double,
        meta: ObjectMetadata, worldX: Double, worldY: Double,
        clampLow: Boolean, clampHigh: Boolean
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return
        var t = ((worldX - x1) * dx + (worldY - y1) * dy) / lenSq

        if (clampLow || clampHigh) {
            val screenP1 = transform.transform(x1, y1)
            val screenP2 = transform.transform(x2, y2)
            val segScreenLen = hypot(screenP2.x - screenP1.x, screenP2.y - screenP1.y)
            val marginT = if (segScreenLen > 1e-6) MAX_LABEL_OFFSET / segScreenLen else 0.0
            if (clampLow) t = t.coerceAtLeast(-marginT)
            if (clampHigh) t = t.coerceAtMost(1.0 + marginT)
        }

        meta.labelAnchorT = t
    }

    private fun hitTestLabel(screenX: Double, screenY: Double): Int? {
        for (obj in manager.allObjects()) {
            if (!obj.isDefined) continue
            val meta = manager.getMetadata(obj.id) ?: continue
            if (!meta.visible || !meta.labelVisible || meta.label == null) continue

            val (anchorX, anchorY) = getObjectAnchor(obj, meta) ?: continue

            // Transform anchor to screen coordinates
            val screenPt = transform.transform(anchorX, anchorY)
            // Label screen position (matching renderer's offset math)
            val labelX = screenPt.x + meta.labelOffsetX
            val labelY = screenPt.y - meta.labelOffsetY

            // Hit box: generous padding so labels are easy to grab over overlapping objects.
            // The renderer uses scaleY=-1 (pivoted around layout-bounds center) to un-mirror
            // text in the y-flipped world group, which shifts the visual baseline ~7px below
            // the computed labelY. Extend the box downward to cover the actual visual area.
            val textWidth = meta.label!!.length * 8.0
            val padding = 8.0
            if (screenX >= labelX - padding && screenX <= labelX + textWidth + padding &&
                screenY >= labelY - 10.0 && screenY <= labelY + 18.0) {
                return obj.id
            }
        }
        return null
    }

    private fun getObjectAnchor(obj: GeometricObject, meta: ObjectMetadata): Pair<Double, Double>? {
        if (obj is PointLike) return Pair(obj.x, obj.y)
        if (obj is LinearLike) {
            val t = meta.labelAnchorT ?: if (obj.linearType == LinearType.RAY) 0.0 else 0.5
            return Pair(obj.x1 + t * (obj.x2 - obj.x1), obj.y1 + t * (obj.y2 - obj.y1))
        }
        return when (obj) {
            is Circle -> {
                val angle = meta.labelAnchorAngle ?: (PI / 2)
                Pair(obj.cx + obj.radius * cos(angle), obj.cy + obj.radius * sin(angle))
            }
            is Angle -> Pair(obj.vertexX, obj.vertexY)
            is Region -> {
                if (obj.vertices.isNotEmpty()) {
                    val cx = obj.vertices.sumOf { it.first } / obj.vertices.size
                    val cy = obj.vertices.sumOf { it.second } / obj.vertices.size
                    Pair(cx, cy)
                } else null
            }
            else -> null
        }
    }
}
