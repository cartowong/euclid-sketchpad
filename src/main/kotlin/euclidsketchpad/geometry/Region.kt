package euclidsketchpad.geometry

import kotlin.math.abs

class Region(
    override val id: Int,
    val pointIds: List<Int>
) : GeometricObject {

    override val parentIds: List<Int> = pointIds
    override var isDefined: Boolean = false; private set

    var vertices: List<Pair<Double, Double>> = emptyList(); private set

    fun contains(x: Double, y: Double): Boolean {
        if (!isDefined || vertices.size < 3) return false
        // Ray casting algorithm
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val (xi, yi) = vertices[i]
            val (xj, yj) = vertices[j]
            if ((yi > y) != (yj > y) &&
                x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined || vertices.size < 3) return Double.MAX_VALUE
        // Distance to nearest edge
        var minDist = Double.MAX_VALUE
        for (i in 0 until vertices.size - 1) {
            val (x1, y1) = vertices[i]
            val (x2, y2) = vertices[i + 1]
            val d = GeometryUtils.pointToSegmentDistance(x, y, x1, y1, x2, y2)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        val pts = pointIds.map { pid ->
            val obj = objects[pid]
            if (obj == null || !obj.isDefined) {
                isDefined = false
                vertices = emptyList()
                return
            }
            val p = obj as PointLike
            Pair(p.x, p.y)
        }
        vertices = pts
        isDefined = true
    }

    fun measureArea(): Double {
        if (!isDefined || vertices.size < 3) return 0.0
        // Shoelace formula (unsigned)
        var area = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += vertices[i].first * vertices[j].second
            area -= vertices[j].first * vertices[i].second
        }
        return abs(area) / 2.0
    }
}
