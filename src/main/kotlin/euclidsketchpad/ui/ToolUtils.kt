package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager

object ToolUtils {

    fun constructIntersection(
        candidate: IntersectionCandidate,
        manager: GeometricObjectManager,
        hitTester: HitTester,
        requestRender: () -> Unit
    ): Int {
        val obj1 = manager.getObject(candidate.parent1Id)
        val obj2 = manager.getObject(candidate.parent2Id)
        val isLinear1 = obj1 is LinearLike
        val isLinear2 = obj2 is LinearLike
        val isCircle1 = obj1 is Circle
        val isCircle2 = obj2 is Circle

        val ix = when {
            isLinear1 && isLinear2 ->
                manager.addObject { id -> LinearLinearIntersection(id, candidate.parent1Id, candidate.parent2Id) }
            isLinear1 && isCircle2 ->
                manager.addObject { id -> LinearCircleIntersection(id, candidate.parent1Id, candidate.parent2Id, candidate.sign) }
            isCircle1 && isLinear2 ->
                manager.addObject { id -> LinearCircleIntersection(id, candidate.parent2Id, candidate.parent1Id, candidate.sign) }
            isCircle1 && isCircle2 ->
                manager.addObject { id -> CircleCircleIntersection(id, candidate.parent1Id, candidate.parent2Id, candidate.sign) }
            else -> manager.addObject { id -> FreePoint(id, candidate.x, candidate.y) }
        }
        hitTester.rebuildIntersectionCache()
        requestRender()
        return ix.id
    }

    fun resolveOrCreatePoint(
        hit: HitResult?,
        worldX: Double,
        worldY: Double,
        manager: GeometricObjectManager,
        hitTester: HitTester,
        requestRender: () -> Unit,
        clipRect: DoubleArray? = null
    ): Int {
        return when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.IntersectionPreview -> constructIntersection(hit.candidate, manager, hitTester, requestRender)
            else -> {
                val px: Double
                val py: Double
                if (clipRect != null) {
                    val clamped = GeometryUtils.clampToRect(worldX, worldY, clipRect)
                    px = clamped[0]; py = clamped[1]
                } else {
                    px = worldX; py = worldY
                }
                val pt = manager.addObject { id -> FreePoint(id, px, py) }
                hitTester.rebuildIntersectionCache()
                requestRender()
                pt.id
            }
        }
    }
}
