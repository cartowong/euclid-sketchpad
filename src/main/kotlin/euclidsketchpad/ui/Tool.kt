package euclidsketchpad.ui

import javafx.scene.Group
import javafx.scene.input.MouseEvent

interface Tool {
    fun activate() {}
    fun deactivate(previewGroup: Group) { previewGroup.children.clear() }
    fun onMousePressed(e: MouseEvent, worldX: Double, worldY: Double) {}
    fun onMouseMoved(e: MouseEvent, worldX: Double, worldY: Double) {}
    fun onMouseDragged(e: MouseEvent, worldX: Double, worldY: Double) {}
    fun onMouseReleased(e: MouseEvent, worldX: Double, worldY: Double) {}
    /** Returns true if an in-progress operation was cancelled. */
    fun onEscapePressed(previewGroup: Group): Boolean { previewGroup.children.clear(); return false }
}
