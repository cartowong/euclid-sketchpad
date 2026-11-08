package euclidsketchpad.geometry

class Circle(
    override val id: Int,
    val centerId: Int,
    val radiusPointId: Int
) : GeometricObject {

    override val parentIds: List<Int> = listOf(centerId, radiusPointId)

    var cx: Double = 0.0; private set
    var cy: Double = 0.0; private set
    var radius: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return GeometryUtils.pointToCircleDistance(x, y, cx, cy, radius)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val center = objects[centerId]
        val radiusPt = objects[radiusPointId]
        if (center == null || radiusPt == null || !center.isDefined || !radiusPt.isDefined) {
            isDefined = false
            return
        }
        val c = center as PointLike
        val r = radiusPt as PointLike
        cx = c.x; cy = c.y
        radius = GeometryUtils.euclideanDistance(cx, cy, r.x, r.y)
        isDefined = true
    }
}
