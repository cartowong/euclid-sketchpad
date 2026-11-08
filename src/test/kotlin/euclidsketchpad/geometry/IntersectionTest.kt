package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntersectionTest {

    private val eps = 1e-9

    // --- IntersectionUtils pure math tests ---

    @Test
    fun `perpendicular lines intersect at origin`() {
        val pt = IntersectionUtils.lineLineIntersection(
            -1.0, 0.0, 1.0, 0.0,  // horizontal line
            0.0, -1.0, 0.0, 1.0   // vertical line
        )
        assertNotNull(pt)
        assertEquals(0.0, pt.first, eps)
        assertEquals(0.0, pt.second, eps)
    }

    @Test
    fun `parallel lines return null`() {
        val pt = IntersectionUtils.lineLineIntersection(
            0.0, 0.0, 1.0, 0.0,
            0.0, 1.0, 1.0, 1.0
        )
        assertEquals(null, pt)
    }

    @Test
    fun `line-segment intersection inside segment is in bounds`() {
        val t = IntersectionUtils.parameterOnLine(0.5, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertTrue(IntersectionUtils.isInBounds(t, LinearType.SEGMENT))
    }

    @Test
    fun `line-segment intersection outside segment is out of bounds`() {
        val t = IntersectionUtils.parameterOnLine(2.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertFalse(IntersectionUtils.isInBounds(t, LinearType.SEGMENT))
    }

    @Test
    fun `ray bounds - behind ray is out of bounds`() {
        val t = IntersectionUtils.parameterOnLine(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertFalse(IntersectionUtils.isInBounds(t, LinearType.RAY))
    }

    @Test
    fun `ray bounds - ahead of ray is in bounds`() {
        val t = IntersectionUtils.parameterOnLine(5.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertTrue(IntersectionUtils.isInBounds(t, LinearType.RAY))
    }

    @Test
    fun `line secant to circle gives two points`() {
        val pts = IntersectionUtils.lineCircleIntersections(
            -2.0, 0.0, 2.0, 0.0,  // horizontal line
            0.0, 0.0, 1.0          // unit circle at origin
        )
        assertEquals(2, pts.size)
        // Points should be (-1, 0) and (1, 0)
        assertEquals(-1.0, pts[0].first, eps)
        assertEquals(0.0, pts[0].second, eps)
        assertEquals(1.0, pts[1].first, eps)
        assertEquals(0.0, pts[1].second, eps)
    }

    @Test
    fun `line tangent to circle gives one point`() {
        val pts = IntersectionUtils.lineCircleIntersections(
            -2.0, 1.0, 2.0, 1.0,  // horizontal line at y=1
            0.0, 0.0, 1.0          // unit circle at origin
        )
        assertEquals(1, pts.size)
        assertEquals(0.0, pts[0].first, eps)
        assertEquals(1.0, pts[0].second, eps)
    }

    @Test
    fun `line missing circle gives no points`() {
        val pts = IntersectionUtils.lineCircleIntersections(
            -2.0, 2.0, 2.0, 2.0,  // horizontal line at y=2
            0.0, 0.0, 1.0          // unit circle at origin
        )
        assertEquals(0, pts.size)
    }

    @Test
    fun `two circles intersecting`() {
        // Circle at (0,0) r=2 and circle at (2,0) r=2
        val pts = IntersectionUtils.circleCircleIntersections(0.0, 0.0, 2.0, 2.0, 0.0, 2.0)
        assertEquals(2, pts.size)
        // Intersection points at (1, sqrt(3)) and (1, -sqrt(3))
        val sorted = pts.sortedByDescending { it.second }
        assertEquals(1.0, sorted[0].first, eps)
        assertEquals(sqrt(3.0), sorted[0].second, eps)
        assertEquals(1.0, sorted[1].first, eps)
        assertEquals(-sqrt(3.0), sorted[1].second, eps)
    }

    @Test
    fun `two circles tangent`() {
        val pts = IntersectionUtils.circleCircleIntersections(0.0, 0.0, 1.0, 2.0, 0.0, 1.0)
        assertEquals(1, pts.size)
        assertEquals(1.0, pts[0].first, eps)
        assertEquals(0.0, pts[0].second, eps)
    }

    @Test
    fun `two circles non-intersecting`() {
        val pts = IntersectionUtils.circleCircleIntersections(0.0, 0.0, 1.0, 5.0, 0.0, 1.0)
        assertEquals(0, pts.size)
    }

    // --- Intersection GeometricObject tests ---

    @Test
    fun `LinearLinearIntersection computes correctly`() {
        val p1 = FreePoint(0, -1.0, 0.0)
        val p2 = FreePoint(1, 1.0, 0.0)
        val p3 = FreePoint(2, 0.0, -1.0)
        val p4 = FreePoint(3, 0.0, 1.0)
        val line1 = Line(4, 0, 1)
        val line2 = Line(5, 2, 3)
        val ix = LinearLinearIntersection(6, 4, 5)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to p3, 3 to p4, 4 to line1, 5 to line2, 6 to ix
        )
        line1.recompute(objects)
        line2.recompute(objects)
        ix.recompute(objects)
        assertTrue(ix.isDefined)
        assertEquals(0.0, ix.x, eps)
        assertEquals(0.0, ix.y, eps)
    }

    @Test
    fun `LinearLinearIntersection - segment misses ray`() {
        // Segment from (0,0) to (1,0), Ray from (3,0) upward through (3,1)
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 1.0, 0.0)
        val p3 = FreePoint(2, 3.0, 0.0)
        val p4 = FreePoint(3, 3.0, 1.0)
        val seg = Segment(4, 0, 1)
        val ray = Ray(5, 2, 3)
        val ix = LinearLinearIntersection(6, 4, 5)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to p3, 3 to p4, 4 to seg, 5 to ray, 6 to ix
        )
        seg.recompute(objects)
        ray.recompute(objects)
        ix.recompute(objects)
        // Lines are parallel (both horizontal/vertical) — actually seg is horizontal, ray is vertical
        // Extended lines would intersect at (3, 0), but t on segment = 3.0, which is > 1, so out of bounds
        assertFalse(ix.isDefined)
    }

    @Test
    fun `LinearCircleIntersection sign selection`() {
        // Vertical line x=0 through unit circle at origin: intersects at (0,1) and (0,-1)
        val p1 = FreePoint(0, 0.0, -2.0)
        val p2 = FreePoint(1, 0.0, 2.0)
        val center = FreePoint(2, 0.0, 0.0)
        val radPt = FreePoint(3, 1.0, 0.0)
        val line = Line(4, 0, 1)
        val circle = Circle(5, 2, 3)
        val objects = mutableMapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to center, 3 to radPt, 4 to line, 5 to circle
        )
        line.recompute(objects)
        circle.recompute(objects)

        val ix1 = LinearCircleIntersection(6, 4, 5, 1)
        val ix2 = LinearCircleIntersection(7, 4, 5, -1)
        objects[6] = ix1
        objects[7] = ix2
        ix1.recompute(objects)
        ix2.recompute(objects)
        assertTrue(ix1.isDefined)
        assertTrue(ix2.isDefined)
        // The two points should be different
        assertTrue(
            (ix1.x != ix2.x || ix1.y != ix2.y),
            "Sign +1 and -1 should produce different intersection points"
        )
    }

    @Test
    fun `LinearCircleIntersection undefined when line misses circle`() {
        val p1 = FreePoint(0, 0.0, 5.0)
        val p2 = FreePoint(1, 1.0, 5.0)
        val center = FreePoint(2, 0.0, 0.0)
        val radPt = FreePoint(3, 1.0, 0.0)
        val line = Line(4, 0, 1)
        val circle = Circle(5, 2, 3)
        val ix = LinearCircleIntersection(6, 4, 5, 1)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to center, 3 to radPt, 4 to line, 5 to circle, 6 to ix
        )
        line.recompute(objects)
        circle.recompute(objects)
        ix.recompute(objects)
        assertFalse(ix.isDefined)
    }

    @Test
    fun `CircleCircleIntersection sign selection`() {
        val c1Center = FreePoint(0, 0.0, 0.0)
        val c1Rad = FreePoint(1, 2.0, 0.0)
        val c2Center = FreePoint(2, 2.0, 0.0)
        val c2Rad = FreePoint(3, 4.0, 0.0)
        val circle1 = Circle(4, 0, 1)
        val circle2 = Circle(5, 2, 3)
        val objects = mutableMapOf<Int, GeometricObject>(
            0 to c1Center, 1 to c1Rad, 2 to c2Center, 3 to c2Rad, 4 to circle1, 5 to circle2
        )
        circle1.recompute(objects)
        circle2.recompute(objects)

        val ix1 = CircleCircleIntersection(6, 4, 5, 1)
        val ix2 = CircleCircleIntersection(7, 4, 5, -1)
        objects[6] = ix1
        objects[7] = ix2
        ix1.recompute(objects)
        ix2.recompute(objects)
        assertTrue(ix1.isDefined)
        assertTrue(ix2.isDefined)
        // Two different points
        assertTrue(
            (ix1.x != ix2.x || ix1.y != ix2.y),
            "Sign +1 and -1 should produce different intersection points"
        )
        // x coordinate should be 1.0 for both (by symmetry)
        assertEquals(1.0, ix1.x, eps)
        assertEquals(1.0, ix2.x, eps)
        // y coordinates should be +sqrt(3) and -sqrt(3)
        assertEquals(sqrt(3.0), maxOf(ix1.y, ix2.y), eps)
        assertEquals(-sqrt(3.0), minOf(ix1.y, ix2.y), eps)
    }

    @Test
    fun `CircleCircleIntersection undefined when non-intersecting`() {
        val c1Center = FreePoint(0, 0.0, 0.0)
        val c1Rad = FreePoint(1, 1.0, 0.0)
        val c2Center = FreePoint(2, 5.0, 0.0)
        val c2Rad = FreePoint(3, 6.0, 0.0)
        val circle1 = Circle(4, 0, 1)
        val circle2 = Circle(5, 2, 3)
        val ix = CircleCircleIntersection(6, 4, 5, 1)
        val objects = mapOf<Int, GeometricObject>(
            0 to c1Center, 1 to c1Rad, 2 to c2Center, 3 to c2Rad, 4 to circle1, 5 to circle2, 6 to ix
        )
        circle1.recompute(objects)
        circle2.recompute(objects)
        ix.recompute(objects)
        assertFalse(ix.isDefined)
    }

    // --- Ray-Ray intersection tests ---

    @Test
    fun `two rays intersecting in their valid regions`() {
        // Ray 1: from (0,0) through (1,1) — extends up-right
        // Ray 2: from (2,0) through (1,1) — extends up-left
        // Intersection at (1,1) with t=1 for both rays
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 1.0, 1.0)
        val p3 = FreePoint(2, 2.0, 0.0)
        val ray1 = Ray(3, 0, 1)
        val ray2 = Ray(4, 2, 1)
        val ix = LinearLinearIntersection(5, 3, 4)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to p3, 3 to ray1, 4 to ray2, 5 to ix
        )
        ray1.recompute(objects)
        ray2.recompute(objects)
        ix.recompute(objects)
        assertTrue(ix.isDefined)
        assertEquals(1.0, ix.x, eps)
        assertEquals(1.0, ix.y, eps)
    }

    @Test
    fun `two rays intersecting beyond their through points`() {
        // Ray 1: from (0,0) through (1,0) — extends to the right
        // Ray 2: from (5,5) through (5,1) — extends downward
        // Infinite lines: y=0 and x=5, intersection at (5,0)
        // Ray 1 t at (5,0): t=5 (valid, >= 0)
        // Ray 2 t at (5,0): t at (5,0) on ray from (5,5) through (5,1): direction (0,-4), t = ((5-5)*0 + (0-5)*(-4))/(0+16) = 20/16 = 1.25 (valid)
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 1.0, 0.0)
        val p3 = FreePoint(2, 5.0, 5.0)
        val p4 = FreePoint(3, 5.0, 1.0)
        val ray1 = Ray(4, 0, 1)
        val ray2 = Ray(5, 2, 3)
        val ix = LinearLinearIntersection(6, 4, 5)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to p3, 3 to p4, 4 to ray1, 5 to ray2, 6 to ix
        )
        ray1.recompute(objects)
        ray2.recompute(objects)
        ix.recompute(objects)
        assertTrue(ix.isDefined)
        assertEquals(5.0, ix.x, eps)
        assertEquals(0.0, ix.y, eps)
    }

    @Test
    fun `two rays not intersecting - intersection behind one ray`() {
        // Ray 1: from (0,0) through (1,0) — extends to the right
        // Ray 2: from (0,1) through (1,2) — extends up-right
        // Infinite lines: y=0 and y=x+1, intersection at (-1,0) — behind ray 1 (t=-1)
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 1.0, 0.0)
        val p3 = FreePoint(2, 0.0, 1.0)
        val p4 = FreePoint(3, 1.0, 2.0)
        val ray1 = Ray(4, 0, 1)
        val ray2 = Ray(5, 2, 3)
        val ix = LinearLinearIntersection(6, 4, 5)
        val objects = mapOf<Int, GeometricObject>(
            0 to p1, 1 to p2, 2 to p3, 3 to p4, 4 to ray1, 5 to ray2, 6 to ix
        )
        ray1.recompute(objects)
        ray2.recompute(objects)
        ix.recompute(objects)
        assertFalse(ix.isDefined)
    }

    @Test
    fun `ray-ray intersection bounds checked by cache logic`() {
        // Simulate what rebuildIntersectionCache does for two rays
        // Ray 1: from (0,5) through (5,0)
        // Ray 2: from (5,5) through (0,0)
        // Should intersect at (2.5, 2.5)
        val l1 = LinearData(0.0, 5.0, 5.0, 0.0, LinearType.RAY)
        val l2 = LinearData(5.0, 5.0, 0.0, 0.0, LinearType.RAY)
        val pt = IntersectionUtils.lineLineIntersection(l1.x1, l1.y1, l1.x2, l1.y2, l2.x1, l2.y1, l2.x2, l2.y2)
        assertNotNull(pt)
        val t1 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l1.x1, l1.y1, l1.x2, l1.y2)
        val t2 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l2.x1, l2.y1, l2.x2, l2.y2)
        assertTrue(IntersectionUtils.isInBounds(t1, l1.type), "t1=$t1 should be in bounds for RAY")
        assertTrue(IntersectionUtils.isInBounds(t2, l2.type), "t2=$t2 should be in bounds for RAY")
        assertEquals(2.5, pt.first, eps)
        assertEquals(2.5, pt.second, eps)
    }
}
