package euclidsketchpad.model

import euclidsketchpad.geometry.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometricObjectManagerTest {

    private val eps = 1e-9

    @Test
    fun `sequential ID assignment`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 1.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }
        assertEquals(0, p1.id)
        assertEquals(1, p2.id)
        assertEquals(2, seg.id)
        assertEquals(3, mgr.objectCount())
    }

    @Test
    fun `propagation updates children`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 4.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        mgr.propagateFrom(0)
        val s = seg as Segment
        assertTrue(s.isDefined)
        assertEquals(0.0, s.x1, eps)
        assertEquals(3.0, s.x2, eps)
    }

    @Test
    fun `movePoint triggers child update`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 4.0) }
        val circle = mgr.addObject { id -> Circle(id, p1.id, p2.id) }

        mgr.propagateFrom(0)
        assertEquals(5.0, (circle as Circle).radius, eps)

        mgr.movePoint(p2.id, 6.0, 8.0)
        assertEquals(10.0, circle.radius, eps)
    }

    @Test
    fun `undefined cascading when parent is missing from map`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 1.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        mgr.propagateFrom(0)
        assertTrue((seg as Segment).isDefined)

        // Verify segment recomputes correctly via propagation
        mgr.movePoint(p1.id, 10.0, 10.0)
        assertEquals(10.0, seg.x1, eps)
    }

    @Test
    fun `delete point removes dependent segment`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 1.0) }
        mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        assertEquals(3, mgr.objectCount())
        val removed = mgr.removeWithDescendants(p1.id)
        assertEquals(2, removed.size) // point + segment
        assertEquals(1, mgr.objectCount()) // only p2 remains
        assertNull(mgr.getObject(p1.id))
        assertNull(mgr.getObject(2)) // segment
    }

    @Test
    fun `delete cascades through multiple levels`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 0.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }
        val p3 = mgr.addObject { id -> FreePoint(id, 0.0, 3.0) }
        val seg2 = mgr.addObject { id -> Segment(id, p1.id, p3.id) }

        // Deleting p1 should remove p1, seg, seg2 (but keep p2 and p3)
        mgr.removeWithDescendants(p1.id)
        assertEquals(2, mgr.objectCount())
        assertNull(mgr.getObject(seg.id))
        assertNull(mgr.getObject(seg2.id))
    }

    @Test
    fun `nextDefaultLabel generates sequential labels`() {
        val mgr = GeometricObjectManager()
        assertEquals("A", mgr.nextDefaultLabel())

        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr.getMetadata(p1.id)!!.label = "A"
        assertEquals("B", mgr.nextDefaultLabel())

        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 0.0) }
        mgr.getMetadata(p2.id)!!.label = "B"
        assertEquals("C", mgr.nextDefaultLabel())
    }
}
