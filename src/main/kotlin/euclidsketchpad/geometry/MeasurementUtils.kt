package euclidsketchpad.geometry

object MeasurementUtils {

    fun segmentLength(seg: Segment): Double {
        if (!seg.isDefined) return 0.0
        return GeometryUtils.euclideanDistance(seg.x1, seg.y1, seg.x2, seg.y2)
    }

    fun angleDegrees(angle: Angle): Double {
        if (!angle.isDefined) return 0.0
        return angle.measureDegrees()
    }

    fun regionArea(region: Region): Double {
        if (!region.isDefined) return 0.0
        return region.measureArea()
    }

    fun circleCircumference(circle: Circle): Double {
        if (!circle.isDefined) return 0.0
        return 2.0 * Math.PI * circle.radius
    }

    fun measure(obj: GeometricObject): Double? = when (obj) {
        is Segment -> segmentLength(obj)
        is Angle -> angleDegrees(obj)
        is Region -> regionArea(obj)
        is Circle -> circleCircumference(obj)
        else -> null
    }

    fun measureLabel(obj: GeometricObject): String? = when (obj) {
        is Segment -> "%.2f".format(segmentLength(obj))
        is Angle -> "%.1f\u00B0".format(angleDegrees(obj))
        is Region -> "%.2f".format(regionArea(obj))
        is Circle -> "%.2f".format(circleCircumference(obj))
        else -> null
    }
}
