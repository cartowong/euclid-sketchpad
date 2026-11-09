package euclidsketchpad.model

import euclidsketchpad.geometry.*

class StyleSettings {
    // Point defaults
    var pointColor: String = "black"
    var pointSize: String = "medium"

    // Linear defaults
    var linearColor: String = "black"
    var linearStrokeStyle: String = "solid"
    var linearStrokeThickness: String = "normal"

    // Circle defaults
    var circleColor: String = "black"
    var circleStrokeStyle: String = "solid"
    var circleStrokeThickness: String = "normal"

    // Angle defaults
    var angleColor: String = "green"
    var angleSize: String = "medium"

    // Region defaults
    var regionColor: String = "yellow"

    fun applyDefaults(metadata: ObjectMetadata, obj: GeometricObject) {
        when (categoryOf(obj)) {
            "Point" -> {
                metadata.color = pointColor
                metadata.size = pointSize
            }
            "Linear" -> {
                metadata.color = linearColor
                metadata.strokeStyle = linearStrokeStyle
                metadata.strokeThickness = linearStrokeThickness
            }
            "Circle" -> {
                metadata.color = circleColor
                metadata.strokeStyle = circleStrokeStyle
                metadata.strokeThickness = circleStrokeThickness
            }
            "Angle" -> {
                metadata.color = angleColor
                metadata.size = angleSize
            }
            "Region" -> {
                metadata.color = regionColor
            }
        }
    }

    companion object {
        val COLOR_MAP = linkedMapOf(
            "black" to "#000000",
            "brown" to "#795548",
            "green" to "#4CAF50",
            "purple" to "#9C27B0",
            "red" to "#F44336",
            "yellow" to "#FFEB3B"
        )

        fun colorToHex(name: String): String = COLOR_MAP[name] ?: "#000000"

        fun pointRadius(size: String): Double = when (size) {
            "small" -> 3.0
            "medium" -> 4.0
            "large" -> 6.0
            else -> 4.0
        }

        fun angleArcRadius(size: String): Double = when (size) {
            "small" -> 20.0
            "medium" -> 25.0
            "large" -> 30.0
            else -> 25.0
        }

        fun strokeThicknessPixels(thickness: String): Double = when (thickness) {
            "thin" -> 1.0
            "normal" -> 2.0
            "thick" -> 3.0
            else -> 2.0
        }

        fun categoryOf(obj: GeometricObject): String = when {
            obj is PointLike -> "Point"
            obj is LinearLike -> "Linear"
            obj is Circle -> "Circle"
            obj is Angle -> "Angle"
            obj is Region -> "Region"
            else -> "Point"
        }
    }
}
