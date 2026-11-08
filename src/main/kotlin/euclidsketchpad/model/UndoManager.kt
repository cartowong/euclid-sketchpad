package euclidsketchpad.model

class UndoManager(private val manager: GeometricObjectManager) {

    private val undoStack = mutableListOf<ManagerState>()
    private var currentIndex = -1
    private var savedIndex = -1
    private var minIndex = 0

    fun checkpoint() {
        // Truncate any redo states
        while (undoStack.size > currentIndex + 1) {
            undoStack.removeAt(undoStack.size - 1)
        }
        undoStack.add(manager.snapshot())
        currentIndex = undoStack.size - 1
    }

    fun currentIndex(): Int = currentIndex

    fun setMinIndex(index: Int?) {
        minIndex = index ?: 0
    }

    fun undo(): Boolean {
        if (currentIndex <= minIndex) return false
        currentIndex--
        manager.restore(undoStack[currentIndex])
        return true
    }

    fun redo(): Boolean {
        if (currentIndex >= undoStack.size - 1) return false
        currentIndex++
        manager.restore(undoStack[currentIndex])
        return true
    }

    fun canUndo(): Boolean = currentIndex > 0

    fun canRedo(): Boolean = currentIndex < undoStack.size - 1

    fun hasUnsavedChanges(): Boolean = currentIndex != savedIndex

    fun markSaved() {
        savedIndex = currentIndex
    }

    fun clear() {
        undoStack.clear()
        currentIndex = -1
        savedIndex = -1
    }
}
