package euclideansketchpad.persistence

import euclideansketchpad.APP_NAME
import euclideansketchpad.APP_VERSION
import euclideansketchpad.geometry.*
import euclideansketchpad.model.GeometricObjectManager
import java.io.File

data class LoadedConstruction(
    val fileName: String,
    val diagram: SerializedDiagram,
    val inputTypes: List<String>,
    val inputIds: List<Int>
)

object ConstructionSerializer {

    fun loadFromFile(file: File): LoadedConstruction {
        val diagram = DiagramSerializer.parseDiagram(file.readText())
        DiagramSerializer.validateAppName(diagram.appName)
        DiagramSerializer.validateVersion(diagram.version)

        // Find input objects (isInput=true in metadata), sorted by ID
        val inputIds = diagram.metadata.entries
            .filter { it.value.isInput }
            .map { it.key }
            .sorted()

        // Map types to display names (all point-likes -> "Point")
        val pointTypes = setOf(
            "FreePoint", "FreePointOnLine", "FreePointOnRay",
            "FreePointOnSegment", "FreePointOnCircle",
            "LinearLinearIntersection", "LinearCircleIntersection", "CircleCircleIntersection",
            "Midpoint", "ReflectionPoint"
        )
        val objectsById = diagram.objects.associateBy { it.id }
        val inputTypes = inputIds.map { id ->
            val type = objectsById[id]?.type ?: "Point"
            if (type in pointTypes) "Point" else type
        }

        return LoadedConstruction(
            fileName = file.nameWithoutExtension,
            diagram = diagram,
            inputTypes = inputTypes,
            inputIds = inputIds
        )
    }

    fun saveConstruction(manager: GeometricObjectManager, file: File) {
        // Serialize the diagram but null out all label fields
        val objects = manager.allObjects().map { serializeObject(it) }
        val metadata = manager.allObjects().associate { obj ->
            val meta = manager.getMetadata(obj.id)!!
            obj.id to SerializedMetadata(
                visible = meta.visible,
                label = null,
                labelVisible = false,
                labelOffsetX = 10.0,
                labelOffsetY = -10.0,
                inMeasurementTable = false,
                labelAnchorAngle = null,
                labelAnchorT = null,
                isInput = meta.isInput,
                isLocked = meta.isLocked
            )
        }
        val diagram = SerializedDiagram(APP_NAME, APP_VERSION, objects, metadata)
        file.writeText(DiagramSerializer.encodeDiagram(diagram))
    }

    private fun serializeObject(obj: GeometricObject): SerializedObject = when (obj) {
        is FreePoint -> SerializedObject(obj.id, "FreePoint", x = obj.x, y = obj.y)
        is FreePointOnLine -> SerializedObject(obj.id, "FreePointOnLine", parentIds = listOf(obj.lineId), t = obj.t)
        is FreePointOnRay -> SerializedObject(obj.id, "FreePointOnRay", parentIds = listOf(obj.rayId), t = obj.t)
        is FreePointOnSegment -> SerializedObject(obj.id, "FreePointOnSegment", parentIds = listOf(obj.segmentId), t = obj.t)
        is FreePointOnCircle -> SerializedObject(obj.id, "FreePointOnCircle", parentIds = listOf(obj.circleId), theta = obj.theta)
        is LinearLinearIntersection -> SerializedObject(obj.id, "LinearLinearIntersection", parentIds = listOf(obj.linear1Id, obj.linear2Id))
        is LinearCircleIntersection -> SerializedObject(obj.id, "LinearCircleIntersection", parentIds = listOf(obj.linearId, obj.circleId), sign = obj.sign)
        is CircleCircleIntersection -> SerializedObject(obj.id, "CircleCircleIntersection", parentIds = listOf(obj.circle1Id, obj.circle2Id), sign = obj.sign)
        is Line -> SerializedObject(obj.id, "Line", parentIds = listOf(obj.point1Id, obj.point2Id))
        is Ray -> SerializedObject(obj.id, "Ray", parentIds = listOf(obj.endpointId, obj.throughId))
        is Segment -> SerializedObject(obj.id, "Segment", parentIds = listOf(obj.point1Id, obj.point2Id))
        is Circle -> SerializedObject(obj.id, "Circle", parentIds = listOf(obj.centerId, obj.radiusPointId))
        is Angle -> SerializedObject(obj.id, "Angle", parentIds = listOf(obj.pointAId, obj.vertexId, obj.pointCId))
        is Region -> SerializedObject(obj.id, "Region", pointIds = obj.pointIds)
        is Midpoint -> SerializedObject(obj.id, "Midpoint", parentIds = listOf(obj.point1Id, obj.point2Id))
        is ReflectionPoint -> SerializedObject(obj.id, "ReflectionPoint", parentIds = listOf(obj.pointId, obj.linearId))
        is ParallelLine -> SerializedObject(obj.id, "ParallelLine", parentIds = listOf(obj.pointId, obj.linearId))
        is PerpendicularLine -> SerializedObject(obj.id, "PerpendicularLine", parentIds = listOf(obj.pointId, obj.linearId))
    }
}
