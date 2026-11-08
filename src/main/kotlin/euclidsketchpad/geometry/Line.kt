package euclidsketchpad.geometry

enum class LinearType { LINE, RAY, SEGMENT }

interface LinearLike {
    val x1: Double
    val y1: Double
    val x2: Double
    val y2: Double
    val linearType: LinearType

    fun linearDistanceFrom(x: Double, y: Double): Double {
        if (this is GeometricObject && !this.isDefined) return Double.MAX_VALUE
        return when (linearType) {
            LinearType.LINE -> GeometryUtils.pointToLineDistance(x, y, x1, y1, x2, y2)
            LinearType.RAY -> GeometryUtils.pointToRayDistance(x, y, x1, y1, x2, y2)
            LinearType.SEGMENT -> GeometryUtils.pointToSegmentDistance(x, y, x1, y1, x2, y2)
        }
    }
}

class Line(
    override val id: Int,
    val point1Id: Int,
    val point2Id: Int
) : GeometricObject, LinearLike {

    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override val linearType = LinearType.LINE

    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double) = linearDistanceFrom(x, y)

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined) {
            isDefined = false
            return
        }
        val fp1 = p1 as PointLike
        val fp2 = p2 as PointLike
        x1 = fp1.x; y1 = fp1.y
        x2 = fp2.x; y2 = fp2.y
        isDefined = true
    }
}

class Ray(
    override val id: Int,
    val endpointId: Int,
    val throughId: Int
) : GeometricObject, LinearLike {

    override val parentIds: List<Int> = listOf(endpointId, throughId)
    override val linearType = LinearType.RAY

    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double) = linearDistanceFrom(x, y)

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[endpointId]
        val p2 = objects[throughId]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined) {
            isDefined = false
            return
        }
        val fp1 = p1 as PointLike
        val fp2 = p2 as PointLike
        x1 = fp1.x; y1 = fp1.y
        x2 = fp2.x; y2 = fp2.y
        isDefined = true
    }
}

class Segment(
    override val id: Int,
    val point1Id: Int,
    val point2Id: Int
) : GeometricObject, LinearLike {

    override val parentIds: List<Int> = listOf(point1Id, point2Id)
    override val linearType = LinearType.SEGMENT

    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double) = linearDistanceFrom(x, y)

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val p1 = objects[point1Id]
        val p2 = objects[point2Id]
        if (p1 == null || p2 == null || !p1.isDefined || !p2.isDefined) {
            isDefined = false
            return
        }
        val fp1 = p1 as PointLike
        val fp2 = p2 as PointLike
        x1 = fp1.x; y1 = fp1.y
        x2 = fp2.x; y2 = fp2.y
        isDefined = true
    }
}

class ParallelLine(
    override val id: Int,
    val pointId: Int,
    val linearId: Int
) : GeometricObject, LinearLike {
    override val parentIds = listOf(pointId, linearId)
    override val linearType = LinearType.LINE
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double) = linearDistanceFrom(x, y)

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
        if (dx == 0.0 && dy == 0.0) { isDefined = false; return }
        x1 = fp.x; y1 = fp.y
        x2 = fp.x + dx; y2 = fp.y + dy
        isDefined = true
    }
}

class PerpendicularLine(
    override val id: Int,
    val pointId: Int,
    val linearId: Int
) : GeometricObject, LinearLike {
    override val parentIds = listOf(pointId, linearId)
    override val linearType = LinearType.LINE
    override var x1: Double = 0.0; private set
    override var y1: Double = 0.0; private set
    override var x2: Double = 0.0; private set
    override var y2: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double) = linearDistanceFrom(x, y)

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
        if (dx == 0.0 && dy == 0.0) { isDefined = false; return }
        x1 = fp.x; y1 = fp.y
        x2 = fp.x - dy; y2 = fp.y + dx  // 90° rotation: (dx,dy) → (-dy,dx)
        isDefined = true
    }
}
