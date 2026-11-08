package euclidsketchpad.geometry

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class Angle(
    override val id: Int,
    val pointAId: Int,
    val vertexId: Int,
    val pointCId: Int
) : GeometricObject {

    override val parentIds: List<Int> = listOf(pointAId, vertexId, pointCId)
    override var isDefined: Boolean = false; private set

    var vertexX: Double = 0.0; private set
    var vertexY: Double = 0.0; private set
    var startAngleRadians: Double = 0.0; private set
    var sweepAngleRadians: Double = 0.0; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        val arcRadius = 20.0 // visual arc radius in world coords
        val dist = hypot(x - vertexX, y - vertexY)
        // Check if point is within the arc wedge
        if (dist > arcRadius * 1.5) return Double.MAX_VALUE
        val angle = normalizeAngle(atan2(y - vertexY, x - vertexX))
        val start = normalizeAngle(startAngleRadians)
        val sweep = sweepAngleRadians
        if (isAngleInSweep(angle, start, sweep)) {
            return kotlin.math.abs(dist - arcRadius * 0.5)
        }
        return Double.MAX_VALUE
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val a = objects[pointAId]
        val v = objects[vertexId]
        val c = objects[pointCId]
        if (a == null || v == null || c == null || !a.isDefined || !v.isDefined || !c.isDefined) {
            isDefined = false
            return
        }
        val pa = a as PointLike
        val pv = v as PointLike
        val pc = c as PointLike

        vertexX = pv.x
        vertexY = pv.y

        val angleBA = atan2(pa.y - pv.y, pa.x - pv.x)
        val angleBC = atan2(pc.y - pv.y, pc.x - pv.x)

        // Clockwise sweep from BA to BC (in math coords with y-up, clockwise = negative direction)
        startAngleRadians = angleBA
        var sweep = angleBC - angleBA
        // Normalize sweep to be in (0, 2*PI] for clockwise direction measured as positive
        // "Clockwise from BA to BC" in y-up means going in the negative angular direction
        // We want the sweep going clockwise, which in standard math is the negative direction
        // But we store it as a positive value representing the clockwise sweep
        sweep = -sweep // negate to get clockwise
        if (sweep <= 0) sweep += 2 * PI
        sweepAngleRadians = sweep

        isDefined = true
    }

    fun containsPoint(x: Double, y: Double, arcRadiusWorld: Double): Boolean {
        if (!isDefined) return false
        val dist = hypot(x - vertexX, y - vertexY)
        if (dist > arcRadiusWorld) return false
        val angle = normalizeAngle(atan2(y - vertexY, x - vertexX))
        return isAngleInSweep(angle, normalizeAngle(startAngleRadians), sweepAngleRadians)
    }

    fun measureDegrees(): Double {
        return sweepAngleRadians * 180.0 / PI
    }

    private fun normalizeAngle(a: Double): Double {
        var result = a % (2 * PI)
        if (result < 0) result += 2 * PI
        return result
    }

    private fun isAngleInSweep(angle: Double, start: Double, sweep: Double): Boolean {
        // Clockwise sweep: from start going in negative direction
        val normAngle = normalizeAngle(angle)
        val normStart = normalizeAngle(start)
        val diff = normalizeAngle(normStart - normAngle)
        return diff <= sweep
    }
}
