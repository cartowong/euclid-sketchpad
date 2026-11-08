package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineTest {

    private val eps = 1e-9

    private fun makePoints(): Map<Int, GeometricObject> {
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 3.0, 4.0)
        return mapOf(0 to p1, 1 to p2)
    }

    @Test
    fun `line recompute derives endpoints from parents`() {
        val objects = makePoints()
        val line = Line(2, 0, 1)
        line.recompute(objects)
        assertTrue(line.isDefined)
        assertEquals(0.0, line.x1, eps)
        assertEquals(0.0, line.y1, eps)
        assertEquals(3.0, line.x2, eps)
        assertEquals(4.0, line.y2, eps)
    }

    @Test
    fun `line distance from point`() {
        val objects = makePoints()
        val line = Line(2, 0, 1)
        line.recompute(objects)
        // Distance from (4, 0) to line through (0,0)-(3,4)
        // Line direction (3,4), point (4,0): cross = |4*4 - 0*3| = 16, len = 5 → 16/5 = 3.2
        assertEquals(16.0 / 5.0, line.distanceFrom(4.0, 0.0), eps)
    }

    @Test
    fun `segment distance clamps to endpoints`() {
        val objects = makePoints()
        val seg = Segment(2, 0, 1)
        seg.recompute(objects)
        assertTrue(seg.isDefined)
        // Distance from (6, 8) to segment (0,0)-(3,4): closest is (3,4), dist = 5
        assertEquals(5.0, seg.distanceFrom(6.0, 8.0), eps)
    }

    @Test
    fun `ray distance clamps behind endpoint`() {
        val objects = makePoints()
        val ray = Ray(2, 0, 1)
        ray.recompute(objects)
        assertTrue(ray.isDefined)
        // Distance from (-3, -4) to ray from (0,0) through (3,4): nearest is (0,0), dist = 5
        assertEquals(5.0, ray.distanceFrom(-3.0, -4.0), eps)
    }

    @Test
    fun `undefined when parent missing`() {
        val line = Line(2, 0, 1)
        line.recompute(emptyMap())
        assertFalse(line.isDefined)
        assertEquals(Double.MAX_VALUE, line.distanceFrom(0.0, 0.0))
    }
}
