package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltinConstructionTest {

    private val eps = 1e-9

    // --- Midpoint ---

    @Test
    fun `midpoint coordinates are average of parents`() {
        val p1 = FreePoint(0, 2.0, 4.0)
        val p2 = FreePoint(1, 6.0, 10.0)
        val mid = Midpoint(2, 0, 1)
        mid.recompute(mapOf(0 to p1, 1 to p2))
        assertTrue(mid.isDefined)
        assertEquals(4.0, mid.x, eps)
        assertEquals(7.0, mid.y, eps)
    }

    @Test
    fun `midpoint at origin`() {
        val p1 = FreePoint(0, -3.0, -5.0)
        val p2 = FreePoint(1, 3.0, 5.0)
        val mid = Midpoint(2, 0, 1)
        mid.recompute(mapOf(0 to p1, 1 to p2))
        assertTrue(mid.isDefined)
        assertEquals(0.0, mid.x, eps)
        assertEquals(0.0, mid.y, eps)
    }

    @Test
    fun `midpoint undefined when parent missing`() {
        val mid = Midpoint(2, 0, 1)
        mid.recompute(emptyMap())
        assertFalse(mid.isDefined)
        assertEquals(Double.MAX_VALUE, mid.distanceFrom(0.0, 0.0))
    }

    @Test
    fun `midpoint distanceFrom works when defined`() {
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 4.0, 0.0)
        val mid = Midpoint(2, 0, 1)
        mid.recompute(mapOf(0 to p1, 1 to p2))
        // Midpoint is at (2, 0). Distance from (5, 0) should be 3.
        assertEquals(3.0, mid.distanceFrom(5.0, 0.0), eps)
    }

    // --- ReflectionPoint ---

    @Test
    fun `reflection across horizontal line`() {
        val p = FreePoint(0, 3.0, 5.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(mapOf(0 to p, 3 to line))
        assertTrue(ref.isDefined)
        assertEquals(3.0, ref.x, eps)
        assertEquals(-5.0, ref.y, eps)
    }

    @Test
    fun `reflection across vertical line`() {
        val p = FreePoint(0, 3.0, 5.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 0.0, 1.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(mapOf(0 to p, 3 to line))
        assertTrue(ref.isDefined)
        assertEquals(-3.0, ref.x, eps)
        assertEquals(5.0, ref.y, eps)
    }

    @Test
    fun `reflection across diagonal line y=x`() {
        val p = FreePoint(0, 3.0, 1.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 1.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(mapOf(0 to p, 3 to line))
        assertTrue(ref.isDefined)
        // Reflecting (3, 1) across y=x gives (1, 3)
        assertEquals(1.0, ref.x, eps)
        assertEquals(3.0, ref.y, eps)
    }

    @Test
    fun `reflection uses infinite line direction from ray parent`() {
        // Ray from (0,0) through (1,0) — a horizontal ray
        // Point at (-2, 3) — behind the ray's endpoint
        // Reflection should still use infinite line direction
        val p = FreePoint(0, -2.0, 3.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val ray = Ray(3, 1, 2)
        ray.recompute(mapOf(1 to a, 2 to b))
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(mapOf(0 to p, 3 to ray))
        assertTrue(ref.isDefined)
        assertEquals(-2.0, ref.x, eps)
        assertEquals(-3.0, ref.y, eps)
    }

    @Test
    fun `reflection uses infinite line direction from segment parent`() {
        // Segment from (0,0) to (1,0)
        // Point at (5, 4) — far from segment
        // Reflection should use infinite line direction
        val p = FreePoint(0, 5.0, 4.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val seg = Segment(3, 1, 2)
        seg.recompute(mapOf(1 to a, 2 to b))
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(mapOf(0 to p, 3 to seg))
        assertTrue(ref.isDefined)
        assertEquals(5.0, ref.x, eps)
        assertEquals(-4.0, ref.y, eps)
    }

    @Test
    fun `reflection undefined when parent missing`() {
        val ref = ReflectionPoint(4, 0, 3)
        ref.recompute(emptyMap())
        assertFalse(ref.isDefined)
    }

    // --- ParallelLine ---

    @Test
    fun `parallel line direction matches parent line`() {
        val p = FreePoint(0, 0.0, 5.0)
        val a = FreePoint(1, 1.0, 0.0)
        val b = FreePoint(2, 4.0, 3.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val par = ParallelLine(4, 0, 3)
        par.recompute(mapOf(0 to p, 3 to line))
        assertTrue(par.isDefined)
        // Should pass through (0, 5) with direction (3, 3)
        assertEquals(0.0, par.x1, eps)
        assertEquals(5.0, par.y1, eps)
        assertEquals(3.0, par.x2, eps)
        assertEquals(8.0, par.y2, eps)
    }

    @Test
    fun `parallel line through point passes through that point`() {
        val p = FreePoint(0, 7.0, -2.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val seg = Segment(3, 1, 2)
        seg.recompute(mapOf(1 to a, 2 to b))
        val par = ParallelLine(4, 0, 3)
        par.recompute(mapOf(0 to p, 3 to seg))
        assertTrue(par.isDefined)
        assertEquals(7.0, par.x1, eps)
        assertEquals(-2.0, par.y1, eps)
        // Distance from the parallel line to the given point should be 0
        assertEquals(0.0, par.distanceFrom(7.0, -2.0), eps)
    }

    @Test
    fun `parallel line works with ray parent`() {
        val p = FreePoint(0, 3.0, 4.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 0.0, 1.0)
        val ray = Ray(3, 1, 2)
        ray.recompute(mapOf(1 to a, 2 to b))
        val par = ParallelLine(4, 0, 3)
        par.recompute(mapOf(0 to p, 3 to ray))
        assertTrue(par.isDefined)
        // Direction should be (0, 1), passing through (3, 4)
        assertEquals(3.0, par.x1, eps)
        assertEquals(4.0, par.y1, eps)
        assertEquals(3.0, par.x2, eps)
        assertEquals(5.0, par.y2, eps)
    }

    @Test
    fun `parallel line has linearType LINE`() {
        val par = ParallelLine(4, 0, 3)
        assertEquals(LinearType.LINE, par.linearType)
    }

    @Test
    fun `parallel line undefined when parent missing`() {
        val par = ParallelLine(4, 0, 3)
        par.recompute(emptyMap())
        assertFalse(par.isDefined)
        assertEquals(Double.MAX_VALUE, par.distanceFrom(0.0, 0.0))
    }

    // --- PerpendicularLine ---

    @Test
    fun `perpendicular line direction is perpendicular to parent`() {
        val p = FreePoint(0, 0.0, 0.0)
        val a = FreePoint(1, 1.0, 0.0)
        val b = FreePoint(2, 4.0, 3.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val perp = PerpendicularLine(4, 0, 3)
        perp.recompute(mapOf(0 to p, 3 to line))
        assertTrue(perp.isDefined)
        // Line direction is (3, 3). Perp direction should be (-3, 3).
        // Dot product should be 0.
        val parentDx = line.x2 - line.x1
        val parentDy = line.y2 - line.y1
        val perpDx = perp.x2 - perp.x1
        val perpDy = perp.y2 - perp.y1
        val dot = parentDx * perpDx + parentDy * perpDy
        assertEquals(0.0, dot, eps, "Directions should be perpendicular (dot product = 0)")
    }

    @Test
    fun `perpendicular line passes through given point`() {
        val p = FreePoint(0, 5.0, 3.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val perp = PerpendicularLine(4, 0, 3)
        perp.recompute(mapOf(0 to p, 3 to line))
        assertTrue(perp.isDefined)
        assertEquals(5.0, perp.x1, eps)
        assertEquals(3.0, perp.y1, eps)
        assertEquals(0.0, perp.distanceFrom(5.0, 3.0), eps)
    }

    @Test
    fun `perpendicular line has linearType LINE`() {
        val perp = PerpendicularLine(4, 0, 3)
        assertEquals(LinearType.LINE, perp.linearType)
    }

    @Test
    fun `perpendicular line undefined when parent missing`() {
        val perp = PerpendicularLine(4, 0, 3)
        perp.recompute(emptyMap())
        assertFalse(perp.isDefined)
    }

    @Test
    fun `perpendicular to horizontal line is vertical`() {
        val p = FreePoint(0, 3.0, 2.0)
        val a = FreePoint(1, 0.0, 0.0)
        val b = FreePoint(2, 1.0, 0.0)
        val line = Line(3, 1, 2)
        line.recompute(mapOf(1 to a, 2 to b))
        val perp = PerpendicularLine(4, 0, 3)
        perp.recompute(mapOf(0 to p, 3 to line))
        assertTrue(perp.isDefined)
        // Horizontal direction (1, 0) → perpendicular direction (0, 1)
        assertEquals(3.0, perp.x1, eps)
        assertEquals(2.0, perp.y1, eps)
        assertEquals(3.0, perp.x2, eps)
        assertEquals(3.0, perp.y2, eps)
    }

    // --- LinearLike.linearDistanceFrom ---

    @Test
    fun `linearDistanceFrom works for LINE type`() {
        val a = FreePoint(0, 0.0, 0.0)
        val b = FreePoint(1, 1.0, 0.0)
        val seg = Segment(2, 0, 1)
        seg.recompute(mapOf(0 to a, 1 to b))
        val par = ParallelLine(3, 0, 2)
        par.recompute(mapOf(0 to a, 2 to seg))
        // After recompute, par goes through (0,0) with direction (1,0) — horizontal line at y=0
        assertTrue(par.isDefined)
        // Distance from (5, 3) to horizontal line through origin = 3
        assertEquals(3.0, par.distanceFrom(5.0, 3.0), eps)
    }
}
