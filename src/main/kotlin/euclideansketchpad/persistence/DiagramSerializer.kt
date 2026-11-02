package euclideansketchpad.persistence

import euclideansketchpad.APP_NAME
import euclideansketchpad.APP_VERSION
import euclideansketchpad.geometry.*
import euclideansketchpad.model.GeometricObjectManager
import euclideansketchpad.model.ObjectMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SerializedDiagram(
    val appName: String,
    val version: String,
    val objects: List<SerializedObject>,
    val metadata: Map<Int, SerializedMetadata>,
    val viewportPanX: Double = 0.0,
    val viewportPanY: Double = 0.0,
    val viewportZoom: Double = 1.0
)

@Serializable
data class SerializedMetadata(
    val visible: Boolean = true,
    val label: String? = null,
    val labelVisible: Boolean = false,
    val labelOffsetX: Double = 10.0,
    val labelOffsetY: Double = -10.0,
    val inMeasurementTable: Boolean = false,
    val labelAnchorAngle: Double? = null,
    val labelAnchorT: Double? = null,
    val isInput: Boolean = false,
    val isLocked: Boolean = false
)

@Serializable
data class SerializedObject(
    val id: Int,
    val type: String,
    val parentIds: List<Int> = emptyList(),
    val x: Double? = null,
    val y: Double? = null,
    val t: Double? = null,
    val theta: Double? = null,
    val sign: Int? = null,
    val pointIds: List<Int>? = null
)

class IncompatibleAppNameException(message: String) : Exception(message)

class IncompatibleVersionException(message: String) : Exception(message)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

object DiagramSerializer {

    fun parseDiagram(jsonStr: String): SerializedDiagram =
        json.decodeFromString<SerializedDiagram>(jsonStr)

    fun encodeDiagram(diagram: SerializedDiagram): String =
        json.encodeToString(diagram)

    fun serialize(manager: GeometricObjectManager): String {
        val objects = manager.allObjects().map { serializeObject(it) }
        val metadata = manager.allMetadata().mapValues { (_, meta) ->
            SerializedMetadata(
                visible = meta.visible,
                label = meta.label,
                labelVisible = meta.labelVisible,
                labelOffsetX = meta.labelOffsetX,
                labelOffsetY = meta.labelOffsetY,
                inMeasurementTable = meta.inMeasurementTable,
                labelAnchorAngle = meta.labelAnchorAngle,
                labelAnchorT = meta.labelAnchorT,
                isInput = meta.isInput,
                isLocked = meta.isLocked
            )
        }.mapKeys { it.key.toString() }.let {
            // Convert to Map<Int, SerializedMetadata>
            it.map { (k, v) -> k.toInt() to v }.toMap()
        }
        val diagram = SerializedDiagram(APP_NAME, APP_VERSION, objects, metadata)
        return json.encodeToString(diagram)
    }

    fun deserialize(jsonStr: String, manager: GeometricObjectManager) {
        val diagram = json.decodeFromString<SerializedDiagram>(jsonStr)
        validateAppName(diagram.appName)
        validateVersion(diagram.version)

        // Sort objects by ID to maintain dependency order
        val sorted = diagram.objects.sortedBy { it.id }

        for (sObj in sorted) {
            val obj = deserializeObject(sObj, manager)
            val sMeta = diagram.metadata[sObj.id]
            if (sMeta != null) {
                val meta = manager.getMetadata(obj.id)
                if (meta != null) {
                    meta.visible = sMeta.visible
                    meta.label = sMeta.label
                    meta.labelVisible = sMeta.labelVisible
                    meta.labelOffsetX = sMeta.labelOffsetX
                    meta.labelOffsetY = sMeta.labelOffsetY
                    meta.inMeasurementTable = sMeta.inMeasurementTable
                    meta.labelAnchorAngle = sMeta.labelAnchorAngle
                    meta.labelAnchorT = sMeta.labelAnchorT
                    meta.isInput = sMeta.isInput
                    meta.isLocked = sMeta.isLocked
                }
            }
        }
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

    fun validateAppName(appName: String) {
        if (appName != APP_NAME) {
            throw IncompatibleAppNameException(
                "This file is not a Euclidean Sketchpad file."
            )
        }
    }

    fun validateVersion(fileVersion: String) {
        val fileMajor = parseMajorVersion(fileVersion)
        val appMajor = parseMajorVersion(APP_VERSION)
        if (fileMajor != appMajor) {
            throw IncompatibleVersionException(
                "This file was saved with version $fileVersion and cannot be opened by version $APP_VERSION."
            )
        }
    }

    fun parseMajorVersion(version: String): Int =
        version.substringBefore('.').toIntOrNull()
            ?: throw IllegalArgumentException("Invalid version format: $version")

    private fun deserializeObject(sObj: SerializedObject, manager: GeometricObjectManager): GeometricObject {
        val id = sObj.id
        val obj = when (sObj.type) {
            "FreePoint" -> FreePoint(id, sObj.x ?: 0.0, sObj.y ?: 0.0)
            "FreePointOnLine" -> FreePointOnLine(id, sObj.parentIds[0], sObj.t ?: 0.0)
            "FreePointOnRay" -> FreePointOnRay(id, sObj.parentIds[0], sObj.t ?: 0.0)
            "FreePointOnSegment" -> FreePointOnSegment(id, sObj.parentIds[0], sObj.t ?: 0.5)
            "FreePointOnCircle" -> FreePointOnCircle(id, sObj.parentIds[0], sObj.theta ?: 0.0)
            "LinearLinearIntersection" -> LinearLinearIntersection(id, sObj.parentIds[0], sObj.parentIds[1])
            "LinearCircleIntersection" -> LinearCircleIntersection(id, sObj.parentIds[0], sObj.parentIds[1], sObj.sign ?: 1)
            "CircleCircleIntersection" -> CircleCircleIntersection(id, sObj.parentIds[0], sObj.parentIds[1], sObj.sign ?: 1)
            "Line" -> Line(id, sObj.parentIds[0], sObj.parentIds[1])
            "Ray" -> Ray(id, sObj.parentIds[0], sObj.parentIds[1])
            "Segment" -> Segment(id, sObj.parentIds[0], sObj.parentIds[1])
            "Circle" -> Circle(id, sObj.parentIds[0], sObj.parentIds[1])
            "Angle" -> Angle(id, sObj.parentIds[0], sObj.parentIds[1], sObj.parentIds[2])
            "Region" -> Region(id, sObj.pointIds ?: emptyList())
            "Midpoint" -> Midpoint(id, sObj.parentIds[0], sObj.parentIds[1])
            "ReflectionPoint" -> ReflectionPoint(id, sObj.parentIds[0], sObj.parentIds[1])
            "ParallelLine" -> ParallelLine(id, sObj.parentIds[0], sObj.parentIds[1])
            "PerpendicularLine" -> PerpendicularLine(id, sObj.parentIds[0], sObj.parentIds[1])
            else -> throw IllegalArgumentException("Unknown object type: ${sObj.type}")
        }
        manager.addObjectWithId(id, obj)
        return obj
    }
}
