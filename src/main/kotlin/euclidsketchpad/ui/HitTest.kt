package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.StyleSettings
import javafx.geometry.Point2D
import javafx.scene.transform.Affine
import kotlin.math.abs

data class IntersectionCandidate(
    val x: Double,
    val y: Double,
    val parent1Id: Int,
    val parent2Id: Int,
    val sign: Int
)

sealed class HitResult {
    data class ConstructedPoint(val id: Int) : HitResult()
    data class OnAngle(val id: Int) : HitResult()
    data class IntersectionPreview(val candidate: IntersectionCandidate) : HitResult()
    data class OnLinear(val id: Int) : HitResult()
    data class OnCircle(val id: Int) : HitResult()
    data class OnRegion(val id: Int) : HitResult()
}

class HitTester(
    private val manager: GeometricObjectManager,
    private val transform: Affine
) {
    private var intersectionCache: List<IntersectionCandidate> = emptyList()

    fun screenToWorld(screenX: Double, screenY: Double): Point2D {
        val inv = transform.createInverse()
        return inv.transform(screenX, screenY)
    }

    private fun snapRadiusInWorld(): Double {
        val scale = abs(transform.mxx)
        return if (scale > 0) 10.0 / scale else 10.0
    }

    fun rebuildIntersectionCache() {
        val candidates = mutableListOf<IntersectionCandidate>()
        val objects = manager.allObjects().filter {
            it.isDefined && manager.getMetadata(it.id)?.let { m -> m.visible && !m.isLocked } == true
        }
        val linears = objects.filter { it is LinearLike }
        val circles = objects.filter { it is Circle }

        // linear-linear
        for (i in linears.indices) {
            for (j in i + 1 until linears.size) {
                val l1 = IntersectionUtils.extractLinear(linears[i]) ?: continue
                val l2 = IntersectionUtils.extractLinear(linears[j]) ?: continue
                val pt = IntersectionUtils.lineLineIntersection(
                    l1.x1, l1.y1, l1.x2, l1.y2, l2.x1, l2.y1, l2.x2, l2.y2
                ) ?: continue
                val t1 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l1.x1, l1.y1, l1.x2, l1.y2)
                val t2 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l2.x1, l2.y1, l2.x2, l2.y2)
                if (IntersectionUtils.isInBounds(t1, l1.type) && IntersectionUtils.isInBounds(t2, l2.type)) {
                    candidates.add(IntersectionCandidate(pt.first, pt.second, linears[i].id, linears[j].id, 0))
                }
            }
        }

        // linear-circle
        for (lin in linears) {
            for (cir in circles) {
                val ld = IntersectionUtils.extractLinear(lin) ?: continue
                val c = cir as Circle
                val pts = IntersectionUtils.lineCircleIntersections(
                    ld.x1, ld.y1, ld.x2, ld.y2, c.cx, c.cy, c.radius
                )
                val refDx = ld.x2 - ld.x1
                val refDy = ld.y2 - ld.y1
                for ((ix, iy) in pts) {
                    val t = IntersectionUtils.parameterOnLine(ix, iy, ld.x1, ld.y1, ld.x2, ld.y2)
                    if (IntersectionUtils.isInBounds(t, ld.type)) {
                        val sign = IntersectionUtils.assignSign(ix, iy, c.cx, c.cy, -refDy, refDx)
                        candidates.add(IntersectionCandidate(ix, iy, lin.id, cir.id, sign))
                    }
                }
            }
        }

        // circle-circle
        for (i in circles.indices) {
            for (j in i + 1 until circles.size) {
                val c1 = circles[i] as Circle
                val c2 = circles[j] as Circle
                val pts = IntersectionUtils.circleCircleIntersections(
                    c1.cx, c1.cy, c1.radius, c2.cx, c2.cy, c2.radius
                )
                val refDx = c2.cx - c1.cx
                val refDy = c2.cy - c1.cy
                for ((ix, iy) in pts) {
                    val sign = IntersectionUtils.assignSign(ix, iy, c1.cx, c1.cy, refDx, refDy)
                    candidates.add(IntersectionCandidate(ix, iy, c1.id, c2.id, sign))
                }
            }
        }

        intersectionCache = candidates
    }

    fun hitTest(screenX: Double, screenY: Double): HitResult? {
        val world = screenToWorld(screenX, screenY)
        val wx = world.x
        val wy = world.y
        val snap = snapRadiusInWorld()

        // Tier 1: Constructed points (PointLike objects already in the manager)
        var bestPointDist = Double.MAX_VALUE
        var bestPointId = -1
        for (obj in manager.allObjects()) {
            if (obj is PointLike && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val d = obj.distanceFrom(wx, wy)
                if (d < snap && d < bestPointDist) {
                    bestPointDist = d
                    bestPointId = obj.id
                }
            }
        }
        if (bestPointId >= 0) return HitResult.ConstructedPoint(bestPointId)

        // Tier 2: Angle objects (point inside arc sector)
        val scale = abs(transform.mxx)
        for (obj in manager.allObjects()) {
            if (obj is Angle && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val arcPx = StyleSettings.angleArcRadius(meta?.size ?: "medium")
                val arcRadiusWorld = if (scale > 0) arcPx / scale else arcPx
                if (obj.containsPoint(wx, wy, arcRadiusWorld)) return HitResult.OnAngle(obj.id)
            }
        }

        // Tier 3: Intersection previews from cache
        var bestIxDist = Double.MAX_VALUE
        var bestIx: IntersectionCandidate? = null
        for (c in intersectionCache) {
            val d = kotlin.math.hypot(c.x - wx, c.y - wy)
            if (d < snap && d < bestIxDist) {
                bestIxDist = d
                bestIx = c
            }
        }
        if (bestIx != null) return HitResult.IntersectionPreview(bestIx)

        // Tier 4: Linear objects (segments, rays, lines)
        var bestLinDist = Double.MAX_VALUE
        var bestLinId = -1
        for (obj in manager.allObjects()) {
            if (obj is LinearLike && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val d = obj.distanceFrom(wx, wy)
                if (d < snap && d < bestLinDist) {
                    bestLinDist = d
                    bestLinId = obj.id
                }
            }
        }
        if (bestLinId >= 0) return HitResult.OnLinear(bestLinId)

        // Tier 5: Circle objects
        var bestCirDist = Double.MAX_VALUE
        var bestCirId = -1
        for (obj in manager.allObjects()) {
            if (obj is Circle && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                val d = obj.distanceFrom(wx, wy)
                if (d < snap && d < bestCirDist) {
                    bestCirDist = d
                    bestCirId = obj.id
                }
            }
        }
        if (bestCirId >= 0) return HitResult.OnCircle(bestCirId)

        // Tier 6: Region objects (point inside polygon)
        for (obj in manager.allObjects()) {
            if (obj is Region && obj.isDefined) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null && (!meta.visible || meta.isLocked)) continue
                if (obj.contains(wx, wy)) return HitResult.OnRegion(obj.id)
            }
        }

        return null
    }
}
