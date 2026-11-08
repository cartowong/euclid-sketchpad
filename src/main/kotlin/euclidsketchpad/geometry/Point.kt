package euclidsketchpad.geometry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

interface PointLike {
    val x: Double
    val y: Double
}

class FreePoint(
    override val id: Int,
    override var x: Double,
    override var y: Double
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = emptyList()
    override val isDefined: Boolean = true

    override fun distanceFrom(x: Double, y: Double): Double = hypot(this.x - x, this.y - y)

    override fun recompute(objects: Map<Int, GeometricObject>) {
        // Free points have no parents; nothing to recompute.
    }
}

class FreePointOnLine(
    override val id: Int,
    val lineId: Int,
    var t: Double
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(lineId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val lin = objects[lineId]
        if (lin == null || !lin.isDefined) { isDefined = false; return }
        val data = IntersectionUtils.extractLinear(lin) ?: run { isDefined = false; return }
        x = data.x1 + t * (data.x2 - data.x1)
        y = data.y1 + t * (data.y2 - data.y1)
        isDefined = true
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val lin = objects[lineId] ?: return
        val data = IntersectionUtils.extractLinear(lin) ?: return
        t = GeometryUtils.closestParameterOnLine(mx, my, data.x1, data.y1, data.x2, data.y2)
    }
}

class FreePointOnRay(
    override val id: Int,
    val rayId: Int,
    var t: Double
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(rayId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val ray = objects[rayId]
        if (ray == null || !ray.isDefined) { isDefined = false; return }
        val data = IntersectionUtils.extractLinear(ray) ?: run { isDefined = false; return }
        val clamped = t.coerceAtLeast(0.0)
        x = data.x1 + clamped * (data.x2 - data.x1)
        y = data.y1 + clamped * (data.y2 - data.y1)
        isDefined = true
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val ray = objects[rayId] ?: return
        val data = IntersectionUtils.extractLinear(ray) ?: return
        t = GeometryUtils.closestParameterOnLine(mx, my, data.x1, data.y1, data.x2, data.y2).coerceAtLeast(0.0)
    }
}

class FreePointOnSegment(
    override val id: Int,
    val segmentId: Int,
    var t: Double
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(segmentId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val seg = objects[segmentId]
        if (seg == null || !seg.isDefined) { isDefined = false; return }
        val data = IntersectionUtils.extractLinear(seg) ?: run { isDefined = false; return }
        val clamped = t.coerceIn(0.0, 1.0)
        x = data.x1 + clamped * (data.x2 - data.x1)
        y = data.y1 + clamped * (data.y2 - data.y1)
        isDefined = true
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val seg = objects[segmentId] ?: return
        val data = IntersectionUtils.extractLinear(seg) ?: return
        t = GeometryUtils.closestParameterOnLine(mx, my, data.x1, data.y1, data.x2, data.y2).coerceIn(0.0, 1.0)
    }
}

class FreePointOnCircle(
    override val id: Int,
    val circleId: Int,
    var theta: Double
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(circleId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val cir = objects[circleId] as? Circle
        if (cir == null || !cir.isDefined) { isDefined = false; return }
        x = cir.cx + cir.radius * cos(theta)
        y = cir.cy + cir.radius * sin(theta)
        isDefined = true
    }

    fun updateParameterFromPosition(mx: Double, my: Double, objects: Map<Int, GeometricObject>) {
        val cir = objects[circleId] as? Circle ?: return
        theta = atan2(my - cir.cy, mx - cir.cx)
    }
}

class Midpoint(
    override val id: Int,
    val point1Id: Int,
    val point2Id: Int
) : GeometricObject, PointLike {
    override val parentIds = listOf(point1Id, point2Id)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined) {
            isDefined = false; return
        }
        val fp1 = p1 as PointLike
        val fp2 = p2 as PointLike
        x = (fp1.x + fp2.x) / 2.0
        y = (fp1.y + fp2.y) / 2.0
        isDefined = true
    }
}

class ReflectionPoint(
    override val id: Int,
    val pointId: Int,
    val linearId: Int
) : GeometricObject, PointLike {
    override val parentIds = listOf(pointId, linearId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p = objects[pointId]
        val lin = objects[linearId]
        if (p == null || lin == null || !p.isDefined || !lin.isDefined) {
            isDefined = false; return
        }
        val fp = p as PointLike
        val data = IntersectionUtils.extractLinear(lin as GeometricObject) ?: run { isDefined = false; return }
        val dx = data.x2 - data.x1
        val dy = data.y2 - data.y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) { isDefined = false; return }
        val t = ((fp.x - data.x1) * dx + (fp.y - data.y1) * dy) / lenSq
        val projX = data.x1 + t * dx
        val projY = data.y1 + t * dy
        x = 2.0 * projX - fp.x
        y = 2.0 * projY - fp.y
        isDefined = true
    }
}
