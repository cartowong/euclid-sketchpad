package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionTest {

    private val eps = 1e-9

    @Test
    fun `unit square area`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 1.0, 0.0)
        objects[2] = FreePoint(2, 1.0, 1.0)
        objects[3] = FreePoint(3, 0.0, 1.0)
        // Closed polygon: first and last point same
        val region = Region(4, listOf(0, 1, 2, 3, 0))
        objects[4] = region
        region.recompute(objects)

        assertTrue(region.isDefined)
        assertEquals(1.0, region.measureArea(), eps)
    }

    @Test
    fun `triangle area`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 4.0, 0.0)
        objects[2] = FreePoint(2, 0.0, 3.0)
        val region = Region(3, listOf(0, 1, 2, 0))
        objects[3] = region
        region.recompute(objects)

        assertTrue(region.isDefined)
        assertEquals(6.0, region.measureArea(), eps) // 0.5 * 4 * 3 = 6
    }

    @Test
    fun `rectangle area`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 5.0, 0.0)
        objects[2] = FreePoint(2, 5.0, 3.0)
        objects[3] = FreePoint(3, 0.0, 3.0)
        val region = Region(4, listOf(0, 1, 2, 3, 0))
        objects[4] = region
        region.recompute(objects)

        assertTrue(region.isDefined)
        assertEquals(15.0, region.measureArea(), eps)
    }

    @Test
    fun `vertices computed correctly`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 1.0, 2.0)
        objects[1] = FreePoint(1, 3.0, 4.0)
        objects[2] = FreePoint(2, 5.0, 6.0)
        val region = Region(3, listOf(0, 1, 2))
        objects[3] = region
        region.recompute(objects)

        assertTrue(region.isDefined)
        assertEquals(3, region.vertices.size)
        assertEquals(1.0, region.vertices[0].first, eps)
        assertEquals(2.0, region.vertices[0].second, eps)
    }
}
