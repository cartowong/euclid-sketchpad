package euclidsketchpad.ui

import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.UndoManager
import javafx.scene.input.MouseEvent

class HideTool(
    private val manager: GeometricObjectManager,
    private val hitTester: HitTester,
    private val undoManager: UndoManager,
    private val requestRender: () -> Unit
) : Tool {

    override fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {
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
        meta.visible = false
        meta.labelVisible = false
        hitTester.rebuildIntersectionCache()
        undoManager.checkpoint()
        requestRender()
    }
}
