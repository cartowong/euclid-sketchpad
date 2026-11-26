package euclidsketchpad.export

import euclidsketchpad.geometry.GeometryUtils.FONT_SIZE
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal class SvgExportCanvas(
    private val width: Int,
    private val height: Int,
    transparentBackground: Boolean
) : ExportCanvas {

    private val sb = StringBuilder()

    init {
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
        if (!transparentBackground) {
            sb.appendLine("""  <rect width="$width" height="$height" fill="white"/>""")
        }
    }

    private fun strokeAttrs(s: Stroke): String {
        val dash = if (s.dashed) """ stroke-dasharray="${DASH_PATTERN.joinToString(" ") { it.toInt().toString() }}"""" else ""
        return """stroke="${s.color}" stroke-width="${s.width}"$dash"""
    }

    override fun gridLine(vertical: Boolean, at: Double, stroke: Stroke) {
        val w = width.toDouble()
        val h = height.toDouble()
        if (vertical) {
            sb.appendLine("""  <line x1="${fmt(at)}" y1="0" x2="${fmt(at)}" y2="$h" ${strokeAttrs(stroke)}/>""")
        } else {
            sb.appendLine("""  <line x1="0" y1="${fmt(at)}" x2="$w" y2="${fmt(at)}" ${strokeAttrs(stroke)}/>""")
        }
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: Stroke) {
        sb.appendLine("""  <line x1="${fmt(x1)}" y1="${fmt(y1)}" x2="${fmt(x2)}" y2="${fmt(y2)}" ${strokeAttrs(stroke)}/>""")
    }

    override fun polygon(points: DoubleArray, color: String, opacity: Double) {
        sb.appendLine("""  <polygon points="${pointList(points)}" fill="$color" fill-opacity="$opacity" stroke="none"/>""")
    }

    override fun arrowhead(points: DoubleArray, color: String) {
        sb.appendLine("""  <polygon points="${pointList(points)}" fill="$color"/>""")
    }

    override fun circle(cx: Double, cy: Double, r: Double, stroke: Stroke) {
        sb.appendLine("""  <circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="${fmt(r)}" fill="none" ${strokeAttrs(stroke)}/>""")
    }

    override fun dot(cx: Double, cy: Double, r: Double, color: String) {
        sb.appendLine("""  <circle cx="${fmt(cx)}" cy="${fmt(cy)}" r="$r" fill="$color"/>""")
    }

    override fun arc(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, stroke: Stroke) {
        val (x1, y1, x2, y2) = arcEnds(cx, cy, r, startRad, sweepRad)
        sb.appendLine("""  <path d="M ${fmt(x1)} ${fmt(y1)} A ${fmt(r)} ${fmt(r)} 0 ${largeArc(sweepRad)} ${sweepFlag(sweepRad)} ${fmt(x2)} ${fmt(y2)}" fill="none" ${strokeAttrs(stroke)}/>""")
    }

    override fun wedge(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double, color: String, fillOpacity: Double, stroke: Stroke) {
        val (x1, y1, x2, y2) = arcEnds(cx, cy, r, startRad, sweepRad)
        val sw = if (stroke.width == stroke.width.toInt().toDouble()) stroke.width.toInt().toString() else stroke.width.toString()
        sb.appendLine("""  <path d="M ${fmt(cx)} ${fmt(cy)} L ${fmt(x1)} ${fmt(y1)} A $r $r 0 ${largeArc(sweepRad)} ${sweepFlag(sweepRad)} ${fmt(x2)} ${fmt(y2)} Z" fill="$color" fill-opacity="$fillOpacity" stroke="${stroke.color}" stroke-opacity="${stroke.opacity}" stroke-width="$sw"/>""")
    }

    override fun text(x: Double, y: Double, text: String) {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        sb.appendLine("""  <text x="${fmt(x)}" y="${fmt(y)}" font-size="$FONT_SIZE" fill="black">$escaped</text>""")
    }

    override fun finish(file: File): List<String> {
        sb.appendLine("</svg>")
        file.writeText(sb.toString())
        return emptyList()
    }

    private fun arcEnds(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double): DoubleArray {
        val end = startRad + sweepRad
        // Screen y points down, so counter-clockwise on screen subtracts the sine.
        return doubleArrayOf(cx + r * cos(startRad), cy - r * sin(startRad), cx + r * cos(end), cy - r * sin(end))
    }

    private fun largeArc(sweepRad: Double) = if (abs(sweepRad) > PI) 1 else 0

    // SVG sweep-flag 1 is clockwise on screen.
    private fun sweepFlag(sweepRad: Double) = if (sweepRad < 0) 1 else 0

    private fun pointList(points: DoubleArray): String =
        (points.indices step 2).joinToString(" ") { "${fmt(points[it])},${fmt(points[it + 1])}" }

    private fun fmt(v: Double): String = "%.2f".format(v)
}
