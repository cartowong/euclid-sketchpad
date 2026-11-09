package euclidsketchpad.model

import euclidsketchpad.geometry.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StyleSettingsTest {

    @Test
    fun `applyDefaults sets point style from settings`() {
        val ss = StyleSettings()
        ss.pointColor = "red"
        ss.pointSize = "large"
        val meta = ObjectMetadata()
        val obj = FreePoint(0, 1.0, 2.0)
        ss.applyDefaults(meta, obj)
        assertEquals("red", meta.color)
        assertEquals("large", meta.size)
    }

    @Test
    fun `applyDefaults sets linear style from settings`() {
        val ss = StyleSettings()
        ss.linearColor = "purple"
        ss.linearStrokeStyle = "dashed"
        ss.linearStrokeThickness = "thick"
        val meta = ObjectMetadata()
        val p1 = FreePoint(0, 0.0, 0.0)
        val p2 = FreePoint(1, 1.0, 0.0)
        val seg = Segment(2, 0, 1)
        ss.applyDefaults(meta, seg)
        assertEquals("purple", meta.color)
        assertEquals("dashed", meta.strokeStyle)
        assertEquals("thick", meta.strokeThickness)
    }

    @Test
    fun `applyDefaults sets circle style from settings`() {
        val ss = StyleSettings()
        ss.circleColor = "brown"
        ss.circleStrokeStyle = "dashed"
        ss.circleStrokeThickness = "thin"
        val meta = ObjectMetadata()
        val circle = Circle(2, 0, 1)
        ss.applyDefaults(meta, circle)
        assertEquals("brown", meta.color)
        assertEquals("dashed", meta.strokeStyle)
        assertEquals("thin", meta.strokeThickness)
    }

    @Test
    fun `applyDefaults sets angle defaults`() {
        val ss = StyleSettings()
        val meta = ObjectMetadata()
        val angle = Angle(3, 0, 1, 2)
        ss.applyDefaults(meta, angle)
        assertEquals("green", meta.color)
        assertEquals("medium", meta.size)
    }

    @Test
    fun `applyDefaults sets region defaults`() {
        val ss = StyleSettings()
        val meta = ObjectMetadata()
        val region = Region(4, listOf(0, 1, 2))
        ss.applyDefaults(meta, region)
        assertEquals("yellow", meta.color)
    }

    @Test
    fun `colorToHex returns correct hex values`() {
        assertEquals("#000000", StyleSettings.colorToHex("black"))
        assertEquals("#795548", StyleSettings.colorToHex("brown"))
        assertEquals("#4CAF50", StyleSettings.colorToHex("green"))
        assertEquals("#9C27B0", StyleSettings.colorToHex("purple"))
        assertEquals("#F44336", StyleSettings.colorToHex("red"))
        assertEquals("#FFEB3B", StyleSettings.colorToHex("yellow"))
        assertEquals("#000000", StyleSettings.colorToHex("unknown"))
    }

    @Test
    fun `pointRadius returns correct values`() {
        assertEquals(3.0, StyleSettings.pointRadius("small"))
        assertEquals(4.0, StyleSettings.pointRadius("medium"))
        assertEquals(6.0, StyleSettings.pointRadius("large"))
    }

    @Test
    fun `angleArcRadius returns correct values`() {
        assertEquals(20.0, StyleSettings.angleArcRadius("small"))
        assertEquals(25.0, StyleSettings.angleArcRadius("medium"))
        assertEquals(30.0, StyleSettings.angleArcRadius("large"))
    }

    @Test
    fun `strokeThicknessPixels returns correct values`() {
        assertEquals(1.0, StyleSettings.strokeThicknessPixels("thin"))
        assertEquals(2.0, StyleSettings.strokeThicknessPixels("normal"))
        assertEquals(3.0, StyleSettings.strokeThicknessPixels("thick"))
    }

    @Test
    fun `categoryOf returns correct categories`() {
        assertEquals("Point", StyleSettings.categoryOf(FreePoint(0, 0.0, 0.0)))
        assertEquals("Linear", StyleSettings.categoryOf(Segment(2, 0, 1)))
        assertEquals("Linear", StyleSettings.categoryOf(Line(2, 0, 1)))
        assertEquals("Linear", StyleSettings.categoryOf(Ray(2, 0, 1)))
        assertEquals("Circle", StyleSettings.categoryOf(Circle(2, 0, 1)))
        assertEquals("Angle", StyleSettings.categoryOf(Angle(3, 0, 1, 2)))
        assertEquals("Region", StyleSettings.categoryOf(Region(4, listOf(0, 1, 2))))
    }
}
