package euclidsketchpad.model

import euclidsketchpad.geometry.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstructionRecorderTest {

    @Test
    fun `start recording marks visible objects as input and locks others`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 0.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }
        val angle = mgr.addObject { id -> Angle(id, p1.id, p2.id, p1.id) }

        val recorder = ConstructionRecorder(mgr)
        recorder.startRecording()

        assertTrue(recorder.isRecording)
        // Points and segment are visible parent-capable -> isInput
        assertTrue(mgr.getMetadata(p1.id)!!.isInput)
        assertTrue(mgr.getMetadata(p2.id)!!.isInput)
        assertTrue(mgr.getMetadata(seg.id)!!.isInput)
        // Angle is not parent-capable -> isLocked
        assertTrue(mgr.getMetadata(angle.id)!!.isLocked)
    }

    @Test
    fun `cancel recording restores flags and removes new objects`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 0.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        val recorder = ConstructionRecorder(mgr)
        recorder.startRecording()

        // Create a new object during recording
        mgr.addObject { id -> FreePoint(id, 0.5, 0.5) }
        assertEquals(4, mgr.objectCount())

        recorder.cancelRecording()

        assertFalse(recorder.isRecording)
        assertEquals(3, mgr.objectCount()) // new point removed
        assertFalse(mgr.getMetadata(p1.id)!!.isInput) // cleared
        assertFalse(mgr.getMetadata(seg.id)!!.isInput) // cleared
    }

    @Test
    fun `end recording clears flags`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 0.0) }

        val recorder = ConstructionRecorder(mgr)
        recorder.startRecording()

        // Build during recording
        mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        recorder.endRecording()

        assertFalse(recorder.isRecording)
        assertFalse(mgr.getMetadata(p1.id)!!.isInput)
        assertFalse(mgr.getMetadata(p2.id)!!.isInput)
    }
}
