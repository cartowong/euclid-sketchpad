package euclidsketchpad.ui

import euclidsketchpad.geometry.FreePoint
import euclidsketchpad.geometry.GeometryUtils
import euclidsketchpad.geometry.PointLike
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.input.MouseEvent

class SelectionTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val requestRender: () -> Unit,
    private val viewportProvider: () -> DoubleArray? = { null },
    private val allObjectsSelected: () -> Boolean = { false },
    private val panViewport: (screenDx: Double, screenDy: Double) -> Unit = { _, _ -> }
) : Tool {

    val selectedIds = mutableSetOf<Int>()

    private var isDragging = false
    private var isPanningDrag = false
    private var lastDragWorldX = 0.0
    private var lastDragWorldY = 0.0
    private var lastDragScreenX = 0.0
    private var lastDragScreenY = 0.0

    private var pressHitId: Int? = null
    private var wasAlreadySelected = false

    override fun activate() {
        selectedIds.clear()
    }

    override fun deactivate(previewGroup: Group) {
        selectedIds.clear()
        previewGroup.children.clear()
        requestRender()
    }

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
        isDragging = false
        isPanningDrag = false
        lastDragWorldX = worldX
        lastDragWorldY = worldY
        lastDragScreenX = e.screenX
        lastDragScreenY = e.screenY

        val hit = hitTester.hitTest(e.x, e.y)
        if (hit != null) {
            val hitId = when (hit) {
                is HitResult.ConstructedPoint -> hit.id
                is HitResult.OnAngle -> hit.id
                is HitResult.OnLinear -> hit.id
                is HitResult.OnCircle -> hit.id
                is HitResult.OnRegion -> hit.id
                is HitResult.IntersectionPreview -> null
            }
            pressHitId = hitId
            if (hitId != null) {
                if (e.isShortcutDown) {
                    // Modifier+click: toggle without affecting others
                    if (selectedIds.contains(hitId)) {
                        selectedIds.remove(hitId)
                    } else {
                        selectedIds.add(hitId)
                    }
                    wasAlreadySelected = false
                } else {
                    // Plain click: deselect others, select this one.
                    // If it was already the only selected, defer deselect to release.
                    wasAlreadySelected = selectedIds.contains(hitId) && selectedIds.size == 1
                    selectedIds.clear()
                    selectedIds.add(hitId)
                }
                requestRender()
            }
        } else {
            pressHitId = null
            wasAlreadySelected = false
            if (selectedIds.isNotEmpty()) {
                selectedIds.clear()
                requestRender()
            }
        }
    }

    override fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) {
        if (selectedIds.isEmpty()) return

        if (allObjectsSelected()) {
            panViewport(e.screenX - lastDragScreenX, e.screenY - lastDragScreenY)
            lastDragScreenX = e.screenX
            lastDragScreenY = e.screenY
            isPanningDrag = true
            isDragging = true
            return
        }

        // Only drag if all selected objects are movable
        if (!selectedIds.all { manager.isMovable(it) }) return

        val dx = worldX - lastDragWorldX
        val dy = worldY - lastDragWorldY
        isDragging = true

        val clipRect = viewportProvider()?.let { GeometryUtils.computeClipRect(it) }

        for (id in selectedIds) {
            val obj = manager.getObject(id) ?: continue
            if (obj is PointLike) {
                var newX = obj.x + dx
                var newY = obj.y + dy
                // Only clamp free points — derived/semi-free points are mathematically determined
                if (obj is FreePoint && clipRect != null) {
                    val clamped = GeometryUtils.clampToRect(newX, newY, clipRect)
                    newX = clamped[0]; newY = clamped[1]
                }
                manager.movePoint(id, newX, newY)
            }
        }
        lastDragWorldX = worldX
        lastDragWorldY = worldY
        hitTester.rebuildIntersectionCache()
        requestRender()
    }

    override fun onMouseReleased(e: MouseEvent, worldX: Double, worldY: Double) {
        // If clicked (not dragged) on an already-selected object, deselect it now
        if (!isDragging && !isPanningDrag && wasAlreadySelected && pressHitId != null) {
            selectedIds.remove(pressHitId)
            requestRender()
        }
        if (isDragging && !isPanningDrag) {
            undoManager.checkpoint()
        }
        isDragging = false
        isPanningDrag = false
        pressHitId = null
        wasAlreadySelected = false
    }

}
