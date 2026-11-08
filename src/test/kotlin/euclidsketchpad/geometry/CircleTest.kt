package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircleTest {

    private val eps = 1e-9

    @Test
    fun `circle recompute derives center and radius`() {
        val center = FreePoint(0, 1.0, 2.0)
        val radiusPt = FreePoint(1, 4.0, 6.0)
        val objects = mapOf<Int, GeometricObject>(0 to center, 1 to radiusPt)

        val circle = Circle(2, 0, 1)
        circle.recompute(objects)
        assertTrue(circle.isDefined)
        assertEquals(1.0, circle.cx, eps)
        assertEquals(2.0, circle.cy, eps)
        assertEquals(5.0, circle.radius, eps)
    }

    @Test
    fun `circle distance from point`() {
        val center = FreePoint(0, 0.0, 0.0)
        val radiusPt = FreePoint(1, 5.0, 0.0)
        val objects = mapOf<Int, GeometricObject>(0 to center, 1 to radiusPt)

        val circle = Circle(2, 0, 1)
        circle.recompute(objects)
        // Point on circle
        assertEquals(0.0, circle.distanceFrom(0.0, 5.0), eps)
        // Point inside
        assertEquals(2.0, circle.distanceFrom(3.0, 0.0), eps)
        // Point outside
        assertEquals(5.0, circle.distanceFrom(10.0, 0.0), eps)
    }

    @Test
    fun `undefined when parent missing`() {
        val circle = Circle(2, 0, 1)
        circle.recompute(emptyMap())
        assertFalse(circle.isDefined)
    }
}
