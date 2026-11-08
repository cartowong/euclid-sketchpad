package euclidsketchpad.model

import euclidsketchpad.geometry.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoManagerTest {

    @Test
    fun `undo restores previous state`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint() // empty state
        mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint() // state with 1 point

        assertEquals(1, mgr.objectCount())
        assertTrue(undo.undo())
        assertEquals(0, mgr.objectCount())
    }

    @Test
    fun `redo restores undone state`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint()

        undo.undo()
        assertEquals(0, mgr.objectCount())

        assertTrue(undo.redo())
        assertEquals(1, mgr.objectCount())
    }

    @Test
    fun `new action truncates redo stack`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint()
        mgr.addObject { id -> FreePoint(id, 3.0, 4.0) }
        undo.checkpoint()

        // Undo back to 1 point
        undo.undo()
        assertEquals(1, mgr.objectCount())

        // New action: add a different point
        mgr.addObject { id -> FreePoint(id, 5.0, 6.0) }
        undo.checkpoint()

        // Redo should not be available (truncated)
        assertFalse(undo.canRedo())
    }

    @Test
    fun `undo past beginning does nothing`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        assertFalse(undo.undo())
        assertFalse(undo.canUndo())
    }

    @Test
    fun `redo past end does nothing`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        assertFalse(undo.redo())
        assertFalse(undo.canRedo())
    }

    @Test
    fun `unsaved changes tracking`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        undo.markSaved()
        assertFalse(undo.hasUnsavedChanges())

        mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint()
        assertTrue(undo.hasUnsavedChanges())

        // Undo back to saved state
        undo.undo()
        assertFalse(undo.hasUnsavedChanges())
    }

    @Test
    fun `snapshot preserves object coordinates`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        val p = mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint()

        // Modify point
        mgr.movePoint(p.id, 10.0, 20.0)
        undo.checkpoint()

        // Undo should restore original position
        undo.undo()
        val restored = mgr.getObject(0) as FreePoint
        assertEquals(1.0, restored.x, 1e-9)
        assertEquals(2.0, restored.y, 1e-9)
    }

    @Test
    fun `snapshot preserves metadata`() {
        val mgr = GeometricObjectManager()
        val undo = UndoManager(mgr)

        undo.checkpoint()
        mgr.addObject { id -> FreePoint(id, 1.0, 2.0) }
        undo.checkpoint()

        mgr.getMetadata(0)!!.visible = false
        mgr.getMetadata(0)!!.label = "A"
        undo.checkpoint()

        undo.undo()
        assertTrue(mgr.getMetadata(0)!!.visible)
        assertEquals(null, mgr.getMetadata(0)!!.label)
    }
}
