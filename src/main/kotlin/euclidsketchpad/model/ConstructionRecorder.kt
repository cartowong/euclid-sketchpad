package euclidsketchpad.model

import euclidsketchpad.geometry.*

class ConstructionRecorder(private val manager: GeometricObjectManager) {

    var isRecording: Boolean = false; private set

    private var baselineNextId: Int = 0

    fun startRecording() {
        isRecording = true
        baselineNextId = manager.getNextId()

        // Mark visible parent-capable objects (not Angle/Region) as isInput
        // Mark everything else as isLocked
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            val isParentCapable = obj is PointLike || obj is LinearLike || obj is Circle
            if (isParentCapable && meta.visible) {
                meta.isInput = true
            } else {
                meta.isLocked = true
            }
        }
    }

    fun cancelRecording() {
        if (!isRecording) return
        // Remove all objects created during recording
        val toRemove = manager.allObjects()
            .filter { it.id >= baselineNextId }
            .map { it.id }
            .sortedDescending()
        for (id in toRemove) {
            manager.removeWithDescendants(id)
        }
        // Clear isInput/isLocked on all remaining objects
        clearFlags()
        isRecording = false
    }

    fun endRecording() {
        if (!isRecording) return
        // Clear isInput/isLocked on all objects
        clearFlags()
        isRecording = false
    }

    private fun clearFlags() {
        for (obj in manager.allObjects()) {
            val meta = manager.getMetadata(obj.id) ?: continue
            meta.isInput = false
            meta.isLocked = false
        }
    }
}
