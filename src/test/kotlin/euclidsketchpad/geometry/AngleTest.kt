package euclidsketchpad.geometry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AngleTest {

    private val eps = 1e-6

    private fun buildAngle(ax: Double, ay: Double, vx: Double, vy: Double, cx: Double, cy: Double): Angle {
        val objects = mutableMapOf<Int, GeometricObject>()
        val pA = FreePoint(0, ax, ay)
        val pV = FreePoint(1, vx, vy)
        val pC = FreePoint(2, cx, cy)
        objects[0] = pA; objects[1] = pV; objects[2] = pC
        val angle = Angle(3, 0, 1, 2)
        objects[3] = angle
        angle.recompute(objects)
        return angle
    }

    @Test
    fun `90 degree angle`() {
        // A at (1,0), vertex at origin, C at (0,1)
        // Clockwise from BA (pointing right) to BC (pointing up) = 270 degrees
        // Wait: clockwise in y-up from right to up is 270 (going right→down→left→up)
        // But measuring clockwise from ray (0,0)→(1,0) to ray (0,0)→(0,1):
        // CW means going right → down → left → up = 270°
        val angle = buildAngle(1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        assertTrue(angle.isDefined)
        assertEquals(270.0, angle.measureDegrees(), eps)
    }

    @Test
    fun `90 degree angle other direction`() {
        // A at (0,1), vertex at origin, C at (1,0)
        // Clockwise from BA (pointing up) to BC (pointing right) = 90°
        val angle = buildAngle(0.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        assertTrue(angle.isDefined)
        assertEquals(90.0, angle.measureDegrees(), eps)
    }

    @Test
    fun `180 degree angle`() {
        // A at (1,0), vertex at origin, C at (-1,0)
        // Clockwise from right to left = 180°
        val angle = buildAngle(1.0, 0.0, 0.0, 0.0, -1.0, 0.0)
        assertTrue(angle.isDefined)
        assertEquals(180.0, angle.measureDegrees(), eps)
    }

    @Test
    fun `full 360 maps to near 360`() {
        // A and C are the same direction - should give ~360 (or very close)
        // Actually if they're exactly the same, sweep would be 0 which gets bumped to 2*PI
        val angle = buildAngle(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertTrue(angle.isDefined)
        assertEquals(360.0, angle.measureDegrees(), eps)
    }
}
