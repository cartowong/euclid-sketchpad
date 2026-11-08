package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryUtilsTest {

    private val eps = 1e-9

    @Test
    fun `euclidean distance between two points`() {
        assertEquals(5.0, GeometryUtils.euclideanDistance(0.0, 0.0, 3.0, 4.0), eps)
        assertEquals(0.0, GeometryUtils.euclideanDistance(1.0, 1.0, 1.0, 1.0), eps)
    }

    @Test
    fun `point to line distance`() {
        // Point (0,1) to line through (0,0)-(1,0) = 1
        assertEquals(1.0, GeometryUtils.pointToLineDistance(0.0, 1.0, 0.0, 0.0, 1.0, 0.0), eps)
        // Point on the line
        assertEquals(0.0, GeometryUtils.pointToLineDistance(0.5, 0.0, 0.0, 0.0, 1.0, 0.0), eps)
        // Point to diagonal line y=x: (1,0) to line (0,0)-(1,1) = 1/sqrt(2)
        assertEquals(1.0 / sqrt(2.0), GeometryUtils.pointToLineDistance(1.0, 0.0, 0.0, 0.0, 1.0, 1.0), eps)
    }

    @Test
    fun `point to segment distance - projection within segment`() {
        assertEquals(1.0, GeometryUtils.pointToSegmentDistance(0.5, 1.0, 0.0, 0.0, 1.0, 0.0), eps)
    }

    @Test
    fun `point to segment distance - projection outside segment clamps to endpoint`() {
        // Point (2, 0) to segment (0,0)-(1,0): nearest is (1,0), distance = 1
        assertEquals(1.0, GeometryUtils.pointToSegmentDistance(2.0, 0.0, 0.0, 0.0, 1.0, 0.0), eps)
        // Point (-1, 0) to segment (0,0)-(1,0): nearest is (0,0), distance = 1
        assertEquals(1.0, GeometryUtils.pointToSegmentDistance(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0), eps)
    }

    @Test
    fun `point to ray distance - behind endpoint clamps`() {
        // Point (-1, 0) to ray from (0,0) through (1,0): nearest is (0,0), distance = 1
        assertEquals(1.0, GeometryUtils.pointToRayDistance(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0), eps)
    }

    @Test
    fun `point to ray distance - forward projects normally`() {
        // Point (5, 1) to ray from (0,0) through (1,0): nearest is (5,0), distance = 1
        assertEquals(1.0, GeometryUtils.pointToRayDistance(5.0, 1.0, 0.0, 0.0, 1.0, 0.0), eps)
    }

    @Test
    fun `point to circle distance`() {
        // Point on the circle
        assertEquals(0.0, GeometryUtils.pointToCircleDistance(5.0, 0.0, 0.0, 0.0, 5.0), eps)
        // Point inside the circle
        assertEquals(2.0, GeometryUtils.pointToCircleDistance(3.0, 0.0, 0.0, 0.0, 5.0), eps)
        // Point outside the circle
        assertEquals(3.0, GeometryUtils.pointToCircleDistance(8.0, 0.0, 0.0, 0.0, 5.0), eps)
    }

    @Test
    fun `closest parameter on line`() {
        // Midpoint of (0,0)-(2,0) is at t=0.5
        assertEquals(0.5, GeometryUtils.closestParameterOnLine(1.0, 5.0, 0.0, 0.0, 2.0, 0.0), eps)
        // Before start
        assertEquals(-0.5, GeometryUtils.closestParameterOnLine(-1.0, 0.0, 0.0, 0.0, 2.0, 0.0), eps)
    }

    // --- clampToRect tests ---

    @Test
    fun `clampToRect - point inside rect unchanged`() {
        val r = GeometryUtils.clampToRect(5.0, 5.0, doubleArrayOf(0.0, 0.0, 10.0, 10.0))
        assertEquals(5.0, r[0], eps)
        assertEquals(5.0, r[1], eps)
    }

    @Test
    fun `clampToRect - point outside rect is clamped`() {
        val r = GeometryUtils.clampToRect(-5.0, 15.0, doubleArrayOf(0.0, 0.0, 10.0, 10.0))
        assertEquals(0.0, r[0], eps)
        assertEquals(10.0, r[1], eps)
    }

    @Test
    fun `clampToRect - point on edge unchanged`() {
        val r = GeometryUtils.clampToRect(0.0, 10.0, doubleArrayOf(0.0, 0.0, 10.0, 10.0))
        assertEquals(0.0, r[0], eps)
        assertEquals(10.0, r[1], eps)
    }

    @Test
    fun `clampToRect - x and y clamped independently`() {
        val r = GeometryUtils.clampToRect(-1.0, 5.0, doubleArrayOf(0.0, 0.0, 10.0, 10.0))
        assertEquals(0.0, r[0], eps)
        assertEquals(5.0, r[1], eps)
    }

    // --- clipCircleToArcs tests ---

    @Test
    fun `clipCircleToArcs - circle fully inside rect returns full circle`() {
        val arcs = GeometryUtils.clipCircleToArcs(5.0, 5.0, 2.0, 0.0, 0.0, 10.0, 10.0)
        assertEquals(1, arcs.size)
        assertEquals(0.0, arcs[0][0], eps)
        assertEquals(360.0, arcs[0][1], eps)
    }

    @Test
    fun `clipCircleToArcs - circle fully outside rect returns empty`() {
        val arcs = GeometryUtils.clipCircleToArcs(50.0, 50.0, 2.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isEmpty())
    }

    @Test
    fun `clipCircleToArcs - circle partially overlapping right edge`() {
        // Circle centered at (9, 5) with radius 3: extends from x=6 to x=12.
        // Rect is [0, 0, 10, 10]. Circle crosses right edge at x=10.
        val arcs = GeometryUtils.clipCircleToArcs(9.0, 5.0, 3.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isNotEmpty())
        // Total sweep should be less than 360 (something got clipped)
        val totalSweep = arcs.sumOf { it[1] }
        assertTrue(totalSweep < 360.0)
        assertTrue(totalSweep > 0.0)
    }

    @Test
    fun `clipCircleToArcs - circle extends past two edges`() {
        // Circle centered at (9, 9) with radius 3: extends past right and top edges
        val arcs = GeometryUtils.clipCircleToArcs(9.0, 9.0, 3.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isNotEmpty())
        val totalSweep = arcs.sumOf { it[1] }
        assertTrue(totalSweep < 360.0)
    }

    @Test
    fun `clipCircleToArcs - very large circle enclosing rect`() {
        // Circle centered at (5, 5) with radius 1000 — encloses the entire rect
        // but most of the circle is outside. No intersections with edges because
        // the edges are fully inside the circle. Sample point (1005, 5) is outside the rect.
        val arcs = GeometryUtils.clipCircleToArcs(5.0, 5.0, 1000.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isEmpty())
    }

    @Test
    fun `clipCircleToArcs - circle tangent to edge`() {
        // Circle centered at (5, 5) with radius 5: tangent to x=0 at (0, 5)
        val arcs = GeometryUtils.clipCircleToArcs(5.0, 5.0, 5.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isNotEmpty())
    }

    @Test
    fun `clipCircleToArcs - zero radius returns empty`() {
        val arcs = GeometryUtils.clipCircleToArcs(5.0, 5.0, 0.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isEmpty())
    }

    @Test
    fun `clipCircleToArcs - circle crosses all four edges`() {
        // Circle centered at (5, 5) with radius 6 in rect [0, 0, 10, 10]
        // Crosses all 4 edges
        val arcs = GeometryUtils.clipCircleToArcs(5.0, 5.0, 6.0, 0.0, 0.0, 10.0, 10.0)
        assertTrue(arcs.isNotEmpty())
        val totalSweep = arcs.sumOf { it[1] }
        assertTrue(totalSweep > 0.0)
        assertTrue(totalSweep < 360.0)
    }
}
