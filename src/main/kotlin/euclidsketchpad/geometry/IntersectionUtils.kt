package euclidsketchpad.geometry

import kotlin.math.abs
import kotlin.math.sqrt

data class LinearData(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val type: LinearType)

object IntersectionUtils {

    private const val EPS = 1e-5

    fun extractLinear(obj: GeometricObject): LinearData? {
        if (obj !is LinearLike || !obj.isDefined) return null
        return LinearData(obj.x1, obj.y1, obj.x2, obj.y2, obj.linearType)
    }

    fun lineLineIntersection(
        x1: Double, y1: Double, x2: Double, y2: Double,
        x3: Double, y3: Double, x4: Double, y4: Double
    ): Pair<Double, Double>? {
        val dx1 = x2 - x1; val dy1 = y2 - y1
        val dx2 = x4 - x3; val dy2 = y4 - y3
        val denom = dx1 * dy2 - dy1 * dx2
        if (abs(denom) < EPS) return null // parallel or coincident
        val t = ((x3 - x1) * dy2 - (y3 - y1) * dx2) / denom
        return Pair(x1 + t * dx1, y1 + t * dy1)
    }

    fun parameterOnLine(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1; val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < EPS * EPS) return 0.0
        return ((px - x1) * dx + (py - y1) * dy) / lenSq
    }

    fun isInBounds(t: Double, type: LinearType): Boolean = when (type) {
        LinearType.LINE -> true
        LinearType.RAY -> t >= -EPS
        LinearType.SEGMENT -> t >= -EPS && t <= 1.0 + EPS
    }

    fun lineCircleIntersections(
        x1: Double, y1: Double, x2: Double, y2: Double,
        cx: Double, cy: Double, r: Double
    ): List<Pair<Double, Double>> {
        val dx = x2 - x1; val dy = y2 - y1
        val fx = x1 - cx; val fy = y1 - cy
        val a = dx * dx + dy * dy
        if (a < EPS * EPS) return emptyList()
        val b = 2.0 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - r * r
        val disc = b * b - 4.0 * a * c
        if (disc < -EPS) return emptyList()
        val sqrtDisc = sqrt(disc.coerceAtLeast(0.0))
        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)
        val results = mutableListOf<Pair<Double, Double>>()
        results.add(Pair(x1 + t1 * dx, y1 + t1 * dy))
        if (abs(t1 - t2) > EPS) {
            results.add(Pair(x1 + t2 * dx, y1 + t2 * dy))
        }
        return results
    }

    fun circleCircleIntersections(
        cx1: Double, cy1: Double, r1: Double,
        cx2: Double, cy2: Double, r2: Double
    ): List<Pair<Double, Double>> {
        val dx = cx2 - cx1; val dy = cy2 - cy1
        val d = sqrt(dx * dx + dy * dy)
        if (d < EPS) return emptyList() // concentric
        if (d > r1 + r2 + EPS) return emptyList() // too far apart
        if (d < abs(r1 - r2) - EPS) return emptyList() // one inside the other
        val a = (r1 * r1 - r2 * r2 + d * d) / (2.0 * d)
        val hSq = r1 * r1 - a * a
        val h = sqrt(hSq.coerceAtLeast(0.0))
        val mx = cx1 + a * dx / d
        val my = cy1 + a * dy / d
        if (h < EPS) return listOf(Pair(mx, my)) // tangent
        val px = -dy / d * h
        val py = dx / d * h
        return listOf(Pair(mx + px, my + py), Pair(mx - px, my - py))
    }

    /**
     * Assigns a deterministic sign (+1 or -1) to a candidate intersection point
     * based on cross product with a reference direction.
     */
    fun assignSign(ix: Double, iy: Double, refX: Double, refY: Double, refDx: Double, refDy: Double): Int {
        val cross = (ix - refX) * refDy - (iy - refY) * refDx
        return if (cross >= 0.0) 1 else -1
    }
}
