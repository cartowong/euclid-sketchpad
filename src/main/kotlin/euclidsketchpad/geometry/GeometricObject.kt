package euclidsketchpad.geometry

sealed interface GeometricObject {
    val id: Int
    val parentIds: List<Int>
    val isDefined: Boolean
    fun distanceFrom(x: Double, y: Double): Double
    fun recompute(objects: Map<Int, GeometricObject>)
}
