package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeasurementUtilsTest {

    private val eps = 1e-6

    @Test
    fun `segment length`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 3.0, 4.0)
        val seg = Segment(2, 0, 1)
        objects[2] = seg
        seg.recompute(objects)

        assertEquals(5.0, MeasurementUtils.segmentLength(seg), eps)
    }

    @Test
    fun `angle degrees`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 1.0) // A (up)
        objects[1] = FreePoint(1, 0.0, 0.0) // vertex
        objects[2] = FreePoint(2, 1.0, 0.0) // C (right)
        val angle = Angle(3, 0, 1, 2)
        objects[3] = angle
        angle.recompute(objects)

        // Clockwise from up to right = 90 degrees
        assertEquals(90.0, MeasurementUtils.angleDegrees(angle), eps)
    }

    @Test
    fun `region area`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 2.0, 0.0)
        objects[2] = FreePoint(2, 2.0, 2.0)
        objects[3] = FreePoint(3, 0.0, 2.0)
        val region = Region(4, listOf(0, 1, 2, 3, 0))
        objects[4] = region
        region.recompute(objects)

        assertEquals(4.0, MeasurementUtils.regionArea(region), eps)
    }

    @Test
    fun `measure returns null for non-measurable types`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 1.0, 1.0)
        val line = Line(2, 0, 1)
        objects[2] = line
        line.recompute(objects)

        assertNull(MeasurementUtils.measure(line))
    }

    @Test
    fun `measure label format`() {
        val objects = mutableMapOf<Int, GeometricObject>()
        objects[0] = FreePoint(0, 0.0, 0.0)
        objects[1] = FreePoint(1, 3.0, 4.0)
        val seg = Segment(2, 0, 1)
        objects[2] = seg
        seg.recompute(objects)

        assertEquals("5.00", MeasurementUtils.measureLabel(seg))
    }
}
