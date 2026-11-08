package euclidsketchpad.model

import euclidsketchpad.geometry.*

data class ManagerState(
    val objects: LinkedHashMap<Int, GeometricObject>,
    val metadata: LinkedHashMap<Int, ObjectMetadata>,
    val nextId: Int
)

class GeometricObjectManager {

    private val objects = linkedMapOf<Int, GeometricObject>()
    private val metadata = linkedMapOf<Int, ObjectMetadata>()
    private var nextId = 0

    fun addObject(factory: (id: Int) -> GeometricObject): GeometricObject {
        val id = nextId++
        val obj = factory(id)
        objects[id] = obj
        metadata[id] = ObjectMetadata()
        obj.recompute(objects)
        return obj
    }

    fun addObjectWithId(id: Int, obj: GeometricObject) {
        objects[id] = obj
        metadata[id] = ObjectMetadata()
        obj.recompute(objects)
        if (id >= nextId) {
            nextId = id + 1
        }
    }

    fun clear() {
        objects.clear()
        metadata.clear()
        nextId = 0
    }

    fun allObjects(): List<GeometricObject> = objects.values.toList()

    fun getObject(id: Int): GeometricObject? = objects[id]

    fun getMetadata(id: Int): ObjectMetadata? = metadata[id]

    fun objectCount(): Int = objects.size

    fun getNextId(): Int = nextId

    fun objectMap(): Map<Int, GeometricObject> = objects

    fun allMetadata(): Map<Int, ObjectMetadata> = metadata

    fun propagateFrom(startId: Int) {
        for ((id, obj) in objects) {
            if (id >= startId) {
                obj.recompute(objects)
            }
        }
    }

    fun isMovable(id: Int): Boolean {
        return when (objects[id]) {
            is FreePoint, is FreePointOnLine, is FreePointOnRay,
            is FreePointOnSegment, is FreePointOnCircle -> true
            else -> false
        }
    }

    fun movePoint(id: Int, newX: Double, newY: Double) {
        val obj = objects[id] ?: return
        when (obj) {
            is FreePoint -> {
                obj.x = newX
                obj.y = newY
                propagateFrom(id + 1)
            }
            is FreePointOnLine -> {
                obj.updateParameterFromPosition(newX, newY, objects)
                obj.recompute(objects)
                propagateFrom(id + 1)
            }
            is FreePointOnRay -> {
                obj.updateParameterFromPosition(newX, newY, objects)
                obj.recompute(objects)
                propagateFrom(id + 1)
            }
            is FreePointOnSegment -> {
                obj.updateParameterFromPosition(newX, newY, objects)
                obj.recompute(objects)
                propagateFrom(id + 1)
            }
            is FreePointOnCircle -> {
                obj.updateParameterFromPosition(newX, newY, objects)
                obj.recompute(objects)
                propagateFrom(id + 1)
            }
            else -> {}
        }
    }

    fun removeWithDescendants(id: Int): List<Int> {
        val toRemove = mutableSetOf(id)
        // BFS to find all descendants
        val queue = ArrayDeque<Int>()
        queue.add(id)
        while (queue.isNotEmpty()) {
            queue.removeFirst()
            for ((objId, obj) in objects) {
                if (objId !in toRemove && obj.parentIds.any { it in toRemove }) {
                    toRemove.add(objId)
                    queue.add(objId)
                }
            }
        }
        // Remove in reverse ID order to avoid issues
        val sorted = toRemove.sortedDescending()
        for (removeId in sorted) {
            objects.remove(removeId)
            metadata.remove(removeId)
        }
        return sorted
    }

    fun nextDefaultLabel(): String {
        val usedLabels = metadata.values.mapNotNull { it.label }.toSet()
        var n = 0
        while (true) {
            val label = if (n < 26) ('A' + n).toString()
            else {
                val sb = StringBuilder()
                var remaining = n
                while (remaining >= 0) {
                    sb.insert(0, ('A' + remaining % 26))
                    remaining = remaining / 26 - 1
                }
                sb.toString()
            }
            if (label !in usedLabels) return label
            n++
        }
    }

    fun snapshot(): ManagerState {
        val objsCopy = linkedMapOf<Int, GeometricObject>()
        for ((id, obj) in objects) {
            objsCopy[id] = deepCopy(obj)
        }
        val metaCopy = linkedMapOf<Int, ObjectMetadata>()
        for ((id, meta) in metadata) {
            metaCopy[id] = meta.copy()
        }
        return ManagerState(objsCopy, metaCopy, nextId)
    }

    fun restore(state: ManagerState) {
        objects.clear()
        metadata.clear()
        for ((id, obj) in state.objects) {
            objects[id] = deepCopy(obj)
        }
        for ((id, meta) in state.metadata) {
            metadata[id] = meta.copy()
        }
        nextId = state.nextId
        propagateFrom(0)
    }

    private fun deepCopy(obj: GeometricObject): GeometricObject = when (obj) {
        is FreePoint -> FreePoint(obj.id, obj.x, obj.y)
        is FreePointOnLine -> FreePointOnLine(obj.id, obj.lineId, obj.t)
        is FreePointOnRay -> FreePointOnRay(obj.id, obj.rayId, obj.t)
        is FreePointOnSegment -> FreePointOnSegment(obj.id, obj.segmentId, obj.t)
        is FreePointOnCircle -> FreePointOnCircle(obj.id, obj.circleId, obj.theta)
        is LinearLinearIntersection -> LinearLinearIntersection(obj.id, obj.linear1Id, obj.linear2Id)
        is LinearCircleIntersection -> LinearCircleIntersection(obj.id, obj.linearId, obj.circleId, obj.sign)
        is CircleCircleIntersection -> CircleCircleIntersection(obj.id, obj.circle1Id, obj.circle2Id, obj.sign)
        is Line -> Line(obj.id, obj.point1Id, obj.point2Id)
        is Ray -> Ray(obj.id, obj.endpointId, obj.throughId)
        is Segment -> Segment(obj.id, obj.point1Id, obj.point2Id)
        is Circle -> Circle(obj.id, obj.centerId, obj.radiusPointId)
        is Angle -> Angle(obj.id, obj.pointAId, obj.vertexId, obj.pointCId)
        is Region -> Region(obj.id, obj.pointIds.toList())
        is Midpoint -> Midpoint(obj.id, obj.point1Id, obj.point2Id)
        is ReflectionPoint -> ReflectionPoint(obj.id, obj.pointId, obj.linearId)
        is ParallelLine -> ParallelLine(obj.id, obj.pointId, obj.linearId)
        is PerpendicularLine -> PerpendicularLine(obj.id, obj.pointId, obj.linearId)
    }
}
