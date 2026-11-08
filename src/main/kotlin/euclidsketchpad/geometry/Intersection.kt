package euclidsketchpad.geometry

import kotlin.math.hypot

class LinearLinearIntersection(
    override val id: Int,
    val linear1Id: Int,
    val linear2Id: Int
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(linear1Id, linear2Id)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        isDefined = false
        val obj1 = objects[linear1Id] ?: return
        val obj2 = objects[linear2Id] ?: return
        if (!obj1.isDefined || !obj2.isDefined) return
        val l1 = IntersectionUtils.extractLinear(obj1) ?: return
        val l2 = IntersectionUtils.extractLinear(obj2) ?: return
        val pt = IntersectionUtils.lineLineIntersection(l1.x1, l1.y1, l1.x2, l1.y2, l2.x1, l2.y1, l2.x2, l2.y2) ?: return
        val t1 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l1.x1, l1.y1, l1.x2, l1.y2)
        val t2 = IntersectionUtils.parameterOnLine(pt.first, pt.second, l2.x1, l2.y1, l2.x2, l2.y2)
        if (!IntersectionUtils.isInBounds(t1, l1.type) || !IntersectionUtils.isInBounds(t2, l2.type)) return
        x = pt.first
        y = pt.second
        isDefined = true
    }
}

class LinearCircleIntersection(
    override val id: Int,
    val linearId: Int,
    val circleId: Int,
    val sign: Int
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(linearId, circleId)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        isDefined = false
        val linObj = objects[linearId] ?: return
        val cirObj = objects[circleId] ?: return
        if (!linObj.isDefined || !cirObj.isDefined) return
        val lin = IntersectionUtils.extractLinear(linObj) ?: return
        val cir = cirObj as? Circle ?: return
        val candidates = IntersectionUtils.lineCircleIntersections(lin.x1, lin.y1, lin.x2, lin.y2, cir.cx, cir.cy, cir.radius)
        if (candidates.isEmpty()) return
        // Filter to in-bounds candidates
        val inBounds = candidates.filter { (ix, iy) ->
            val t = IntersectionUtils.parameterOnLine(ix, iy, lin.x1, lin.y1, lin.x2, lin.y2)
            IntersectionUtils.isInBounds(t, lin.type)
        }
        if (inBounds.isEmpty()) return
        if (inBounds.size == 1) {
            x = inBounds[0].first; y = inBounds[0].second; isDefined = true; return
        }
        // Use perpendicular to line direction for deterministic sign assignment
        val refDx = lin.x2 - lin.x1
        val refDy = lin.y2 - lin.y1
        val match = inBounds.find { (ix, iy) ->
            IntersectionUtils.assignSign(ix, iy, cir.cx, cir.cy, -refDy, refDx) == sign
        } ?: return
        x = match.first
        y = match.second
        isDefined = true
    }
}

class CircleCircleIntersection(
    override val id: Int,
    val circle1Id: Int,
    val circle2Id: Int,
    val sign: Int
) : GeometricObject, PointLike {

    override val parentIds: List<Int> = listOf(circle1Id, circle2Id)
    override var x: Double = 0.0; private set
    override var y: Double = 0.0; private set
    override var isDefined: Boolean = false; private set

    override fun distanceFrom(x: Double, y: Double): Double {
        if (!isDefined) return Double.MAX_VALUE
        return hypot(this.x - x, this.y - y)
    }

    override fun recompute(objects: Map<Int, GeometricObject>) {
        isDefined = false
        val c1Obj = objects[circle1Id] ?: return
        val c2Obj = objects[circle2Id] ?: return
        if (!c1Obj.isDefined || !c2Obj.isDefined) return
        val c1 = c1Obj as? Circle ?: return
        val c2 = c2Obj as? Circle ?: return
        val candidates = IntersectionUtils.circleCircleIntersections(c1.cx, c1.cy, c1.radius, c2.cx, c2.cy, c2.radius)
        if (candidates.isEmpty()) return
        if (candidates.size == 1) {
            x = candidates[0].first
            y = candidates[0].second
            isDefined = true
            return
        }
        // Use line between centers as reference direction for sign assignment
        val refDx = c2.cx - c1.cx
        val refDy = c2.cy - c1.cy
        val match = candidates.find { (ix, iy) ->
            IntersectionUtils.assignSign(ix, iy, c1.cx, c1.cy, refDx, refDy) == sign
        } ?: return
        x = match.first
        y = match.second
        isDefined = true
    }
}
