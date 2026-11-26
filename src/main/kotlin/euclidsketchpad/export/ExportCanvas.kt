package euclidsketchpad.export

import java.io.File

internal val DASH_PATTERN = doubleArrayOf(6.0, 4.0)

/** Colors are hex strings such as "#F44336". */
internal class Stroke(
    val color: String,
    val width: Double,
    val dashed: Boolean = false,
    val opacity: Double = 1.0
)

/**
 * Drawing surface for one export, implemented per output format.
 *
 * Coordinates are output pixels (= PDF points), origin top-left, y down.
 * Angles are radians measured counter-clockwise as seen on screen;
 * a negative sweep runs clockwise.
 */
internal interface ExportCanvas {
    /** Full-width (or full-height, if [vertical]) grid line at pixel position [at]. */
    fun gridLine(vertical: Boolean, at: Double, stroke: Stroke)
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke)
    /** Unstroked filled polygon; [points] is x0, y0, x1, y1, ... */
    fun polygon(points: DoubleArray, color: String, opacity: Double)
    /** Opaque filled triangle; [points] as from GeometryUtils.computeArrowheadVertices. */
    fun arrowhead(points: DoubleArray, color: String)
    /** Unfilled circle outline. */
    fun circle(cx: Double, cy: Double, r: Double, stroke: Stroke)
    /** Filled circle (a point marker). */
    fun dot(cx: Double, cy: Double, r: Double, color: String)
    /** Open circular arc. */
    fun arc(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, stroke: Stroke)
    /** Pie slice (angle marker): filled and outlined. */
    fun wedge(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, color: String, fillOpacity: Double, stroke: Stroke)
    /** Black label text with its baseline starting at (x, y). */
    fun text(x: Double, y: Double, text: String)
    /** Writes the output and returns any warnings to show the user. */
    fun finish(file: File): List<String>
}
