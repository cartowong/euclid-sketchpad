package euclideansketchpad.geometry

import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object GeometryUtils {

    const val POINT_RADIUS = 4.0
    const val STROKE_WIDTH = 2.0
    const val LINE_EXTENT = 100000.0
    const val ARROWHEAD_SIZE = 8.0
    const val ANGLE_ARC_RADIUS = 25.0
    const val FONT_SIZE = 12.0
    const val VIEWPORT_MARGIN_FRACTION = 0.02

    /**
     * Compute a clip rect inset by [marginFraction] from the given bounds.
     * bounds = [minX, minY, maxX, maxY]
     */
    fun computeClipRect(bounds: DoubleArray, marginFraction: Double = VIEWPORT_MARGIN_FRACTION): DoubleArray {
        val w = bounds[2] - bounds[0]
        val h = bounds[3] - bounds[1]
        val mx = w * marginFraction
        val my = h * marginFraction
        return doubleArrayOf(bounds[0] + mx, bounds[1] + my, bounds[2] - mx, bounds[3] - my)
    }

    /**
     * Compute the 6 vertex coordinates (3 points) of an arrowhead triangle.
     * Returns [tipX, tipY, base1X, base1Y, base2X, base2Y].
     */
    fun computeArrowheadVertices(
        tipX: Double, tipY: Double,
        dirX: Double, dirY: Double,
        size: Double
    ): DoubleArray {
        val perpX = -dirY
        val perpY = dirX
        val baseX = tipX - dirX * size
        val baseY = tipY - dirY * size
        return doubleArrayOf(
            tipX, tipY,
            baseX + perpX * size * 0.4, baseY + perpY * size * 0.4,
            baseX - perpX * size * 0.4, baseY - perpY * size * 0.4
        )
    }

    /**
     * Adaptive grid spacing: target ~50 screen pixels per grid line.
     * [pixelsPerUnit] is the current scale (screen pixels per math unit).
     */
    fun computeAdaptiveGridSpacing(pixelsPerUnit: Double): Double {
        val rawSpacing = 50.0 / pixelsPerUnit
        val magnitude = 10.0.pow(floor(log10(rawSpacing)))
        val normalized = rawSpacing / magnitude
        return when {
            normalized < 2.0 -> magnitude
            normalized < 5.0 -> 2.0 * magnitude
            else -> 5.0 * magnitude
        }
    }

    fun euclideanDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double =
        hypot(x2 - x1, y2 - y1)

    /**
     * Distance from point (px, py) to the infinite line through (x1, y1) and (x2, y2).
     */
    fun pointToLineDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return euclideanDistance(px, py, x1, y1)
        val cross = abs((px - x1) * dy - (py - y1) * dx)
        return cross / sqrt(lenSq)
    }

    /**
     * Distance from point (px, py) to segment from (x1, y1) to (x2, y2).
     */
    fun pointToSegmentDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return euclideanDistance(px, py, x1, y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clamped = t.coerceIn(0.0, 1.0)
        val projX = x1 + clamped * dx
        val projY = y1 + clamped * dy
        return euclideanDistance(px, py, projX, projY)
    }

    /**
     * Distance from point (px, py) to ray starting at (x1, y1) going through (x2, y2).
     */
    fun pointToRayDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return euclideanDistance(px, py, x1, y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clamped = max(0.0, t)
        val projX = x1 + clamped * dx
        val projY = y1 + clamped * dy
        return euclideanDistance(px, py, projX, projY)
    }

    /**
     * Distance from point (px, py) to circle with center (cx, cy) and radius r.
     */
    fun pointToCircleDistance(px: Double, py: Double, cx: Double, cy: Double, r: Double): Double {
        val distToCenter = euclideanDistance(px, py, cx, cy)
        return abs(distToCenter - r)
    }

    /**
     * Closest parameter t on the infinite line through (x1,y1)-(x2,y2) to point (px,py).
     * t=0 at (x1,y1), t=1 at (x2,y2).
     */
    fun closestParameterOnLine(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return 0.0
        return ((px - x1) * dx + (py - y1) * dy) / lenSq
    }

    /**
     * Liang-Barsky line clipping against an axis-aligned rectangle.
     * Returns clipped endpoints [x1, y1, x2, y2] or null if the segment is fully outside.
     */
    fun clipLineToRect(
        x1: Double, y1: Double, x2: Double, y2: Double,
        xmin: Double, ymin: Double, xmax: Double, ymax: Double
    ): DoubleArray? {
        val dx = x2 - x1
        val dy = y2 - y1
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(x1 - xmin, xmax - x1, y1 - ymin, ymax - y1)

        var t0 = 0.0
        var t1 = 1.0

        for (i in 0..3) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0.0) {
                    if (t > t1) return null
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return null
                    if (t < t1) t1 = t
                }
            }
        }

        return doubleArrayOf(
            x1 + t0 * dx, y1 + t0 * dy,
            x1 + t1 * dx, y1 + t1 * dy
        )
    }

    /**
     * Clamp a point (x, y) to the given clip rect [xmin, ymin, xmax, ymax].
     * X and Y are clamped independently.
     */
    fun clampToRect(x: Double, y: Double, clipRect: DoubleArray): DoubleArray {
        return doubleArrayOf(
            x.coerceIn(clipRect[0], clipRect[2]),
            y.coerceIn(clipRect[1], clipRect[3])
        )
    }

    /**
     * Clip a circle to an axis-aligned rectangle, returning visible arc segments.
     *
     * Each arc is represented as [startAngleDegrees, sweepAngleDegrees] in standard
     * math convention (counter-clockwise from positive x-axis). These values are
     * suitable for JavaFX Arc nodes in a y-up coordinate system.
     *
     * Returns an empty list if the circle is fully outside the rect.
     * Returns a single arc with sweep 360 if the circle is fully inside.
     */
    fun clipCircleToArcs(
        cx: Double, cy: Double, radius: Double,
        xmin: Double, ymin: Double, xmax: Double, ymax: Double
    ): List<DoubleArray> {
        if (radius <= 0.0) return emptyList()

        // Quick reject: circle bounding box vs clip rect
        if (cx + radius < xmin || cx - radius > xmax ||
            cy + radius < ymin || cy - radius > ymax) {
            return emptyList()
        }

        // Quick accept: circle fully inside
        if (cx - radius >= xmin && cx + radius <= xmax &&
            cy - radius >= ymin && cy + radius <= ymax) {
            return listOf(doubleArrayOf(0.0, 360.0))
        }

        // Find all intersection angles between circle and the 4 rect edges.
        // Each edge is a line segment; we find where the circle crosses it.
        val angles = mutableListOf<Double>()

        // Left edge: x = xmin, y in [ymin, ymax]
        intersectCircleVerticalEdge(cx, cy, radius, xmin, ymin, ymax, angles)
        // Right edge: x = xmax, y in [ymin, ymax]
        intersectCircleVerticalEdge(cx, cy, radius, xmax, ymin, ymax, angles)
        // Bottom edge: y = ymin, x in [xmin, xmax]
        intersectCircleHorizontalEdge(cx, cy, radius, ymin, xmin, xmax, angles)
        // Top edge: y = ymax, x in [xmin, xmax]
        intersectCircleHorizontalEdge(cx, cy, radius, ymax, xmin, xmax, angles)

        if (angles.isEmpty()) {
            // No intersections — circle is either fully inside or fully outside.
            // We already handled fully inside above, so check if center is inside.
            // (Edge case: circle bigger than rect but no intersections means it encloses the rect,
            //  but some point on the circle could still be outside. Check a sample point.)
            val sampleX = cx + radius
            val sampleY = cy
            return if (sampleX in xmin..xmax && sampleY in ymin..ymax) {
                listOf(doubleArrayOf(0.0, 360.0))
            } else {
                emptyList()
            }
        }

        // Sort angles and deduplicate (angles very close together from corner touches)
        angles.sort()
        val uniqueAngles = mutableListOf(angles[0])
        for (i in 1 until angles.size) {
            if (angles[i] - uniqueAngles.last() > 1e-9) {
                uniqueAngles.add(angles[i])
            }
        }

        // For each arc between consecutive intersection angles, test if its midpoint
        // is inside the rect. If so, include it.
        val result = mutableListOf<DoubleArray>()
        val n = uniqueAngles.size
        for (i in 0 until n) {
            val a1 = uniqueAngles[i]
            val a2 = if (i + 1 < n) uniqueAngles[i + 1] else uniqueAngles[0] + 2.0 * PI
            val midAngle = (a1 + a2) / 2.0
            val mx = cx + radius * cos(midAngle)
            val my = cy + radius * sin(midAngle)
            // Use a small tolerance for the containment test
            val tol = 1e-9
            if (mx >= xmin - tol && mx <= xmax + tol && my >= ymin - tol && my <= ymax + tol) {
                val startDeg = a1 * 180.0 / PI
                val sweepDeg = (a2 - a1) * 180.0 / PI
                result.add(doubleArrayOf(startDeg, sweepDeg))
            }
        }

        return result
    }

    private fun intersectCircleVerticalEdge(
        cx: Double, cy: Double, radius: Double,
        edgeX: Double, ymin: Double, ymax: Double,
        angles: MutableList<Double>
    ) {
        val dx = edgeX - cx
        if (abs(dx) > radius) return
        val dy = sqrt(radius * radius - dx * dx)
        val y1 = cy + dy
        val y2 = cy - dy
        if (y1 in ymin..ymax) angles.add(normalizeAngle(atan2(y1 - cy, dx)))
        if (y2 in ymin..ymax && abs(dy) > 1e-12) angles.add(normalizeAngle(atan2(y2 - cy, dx)))
    }

    private fun intersectCircleHorizontalEdge(
        cx: Double, cy: Double, radius: Double,
        edgeY: Double, xmin: Double, xmax: Double,
        angles: MutableList<Double>
    ) {
        val dy = edgeY - cy
        if (abs(dy) > radius) return
        val dx = sqrt(radius * radius - dy * dy)
        val x1 = cx + dx
        val x2 = cx - dx
        if (x1 in xmin..xmax) angles.add(normalizeAngle(atan2(dy, x1 - cx)))
        if (x2 in xmin..xmax && abs(dx) > 1e-12) angles.add(normalizeAngle(atan2(dy, x2 - cx)))
    }

    /** Normalize angle to [0, 2*PI). */
    private fun normalizeAngle(a: Double): Double {
        var result = a % (2.0 * PI)
        if (result < 0.0) result += 2.0 * PI
        return result
    }
}
