package euclidsketchpad.model

import euclidsketchpad.geometry.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ObjectMetadata(
    var visible: Boolean = true,
    var label: String? = null,
    var labelVisible: Boolean = false,
    var labelOffsetX: Double = 10.0,
    var labelOffsetY: Double = -10.0,
    var inMeasurementTable: Boolean = false,
    var labelAnchorAngle: Double? = null,
    var labelAnchorT: Double? = null,
    var isInput: Boolean = false,
    var isLocked: Boolean = false
)

fun getObjectAnchor(obj: GeometricObject, meta: ObjectMetadata? = null): Pair<Double, Double>? {
    if (obj is PointLike) return Pair(obj.x, obj.y)
    if (obj is LinearLike) {
        val t = meta?.labelAnchorT ?: if (obj.linearType == LinearType.RAY) 0.0 else 0.5
        return Pair(obj.x1 + t * (obj.x2 - obj.x1), obj.y1 + t * (obj.y2 - obj.y1))
    }
    return when (obj) {
        is Circle -> {
            val angle = meta?.labelAnchorAngle ?: (PI / 2)
            Pair(obj.cx + obj.radius * cos(angle), obj.cy + obj.radius * sin(angle))
        }
        is Angle -> Pair(obj.vertexX, obj.vertexY)
        is Region -> {
            if (obj.vertices.isNotEmpty()) {
                val cx = obj.vertices.sumOf { it.first } / obj.vertices.size
                val cy = obj.vertices.sumOf { it.second } / obj.vertices.size
                Pair(cx, cy)
            } else null
        }
        else -> null
    }
}
