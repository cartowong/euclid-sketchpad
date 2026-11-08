package euclidsketchpad.persistence

import euclidsketchpad.APP_NAME
import euclidsketchpad.APP_VERSION
import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramSerializerTest {

    private val eps = 1e-9

    @Test
    fun `round trip preserves free points`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 1.0, 2.0) }
        mgr1.addObject { id -> FreePoint(id, 3.0, 4.0) }
        mgr1.getMetadata(0)!!.label = "A"
        mgr1.getMetadata(0)!!.labelVisible = true

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        assertEquals(2, mgr2.objectCount())
        val p = mgr2.getObject(0) as FreePoint
        assertEquals(1.0, p.x, eps)
        assertEquals(2.0, p.y, eps)
        assertEquals("A", mgr2.getMetadata(0)!!.label)
        assertTrue(mgr2.getMetadata(0)!!.labelVisible)
    }

    @Test
    fun `round trip preserves segment`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 3.0, 4.0) }
        mgr1.addObject { id -> Segment(id, 0, 1) }

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        assertEquals(3, mgr2.objectCount())
        val seg = mgr2.getObject(2) as Segment
        assertTrue(seg.isDefined)
        assertEquals(0.0, seg.x1, eps)
        assertEquals(3.0, seg.x2, eps)
    }

    @Test
    fun `round trip preserves circle`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 3.0, 4.0) }
        mgr1.addObject { id -> Circle(id, 0, 1) }

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        val cir = mgr2.getObject(2) as Circle
        assertTrue(cir.isDefined)
        assertEquals(5.0, cir.radius, eps)
    }

    @Test
    fun `round trip preserves angle`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 1.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 0.0, 1.0) }
        mgr1.addObject { id -> Angle(id, 0, 1, 2) }

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        val angle = mgr2.getObject(3) as Angle
        assertTrue(angle.isDefined)
    }

    @Test
    fun `round trip preserves region`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 1.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 1.0, 1.0) }
        mgr1.addObject { id -> FreePoint(id, 0.0, 1.0) }
        mgr1.addObject { id -> Region(id, listOf(0, 1, 2, 3, 0)) }

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        val region = mgr2.getObject(4) as Region
        assertTrue(region.isDefined)
        assertEquals(1.0, region.measureArea(), eps)
    }

    @Test
    fun `round trip preserves metadata`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.getMetadata(0)!!.visible = false
        mgr1.getMetadata(0)!!.label = "P"
        mgr1.getMetadata(0)!!.labelVisible = true
        mgr1.getMetadata(0)!!.labelOffsetX = 15.0
        mgr1.getMetadata(0)!!.labelOffsetY = -20.0
        mgr1.getMetadata(0)!!.inMeasurementTable = true

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        val meta = mgr2.getMetadata(0)!!
        assertEquals(false, meta.visible)
        assertEquals("P", meta.label)
        assertTrue(meta.labelVisible)
        assertEquals(15.0, meta.labelOffsetX, eps)
        assertEquals(-20.0, meta.labelOffsetY, eps)
        assertTrue(meta.inMeasurementTable)
    }

    @Test
    fun `round trip preserves appName and version`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }

        val json = DiagramSerializer.serialize(mgr1)
        val diagram = DiagramSerializer.parseDiagram(json)

        assertEquals(APP_NAME, diagram.appName)
        assertEquals(APP_VERSION, diagram.version)
    }

    @Test
    fun `deserialize rejects incompatible major version`() {
        val json = """
            {
                "appName": "EuclidSketchpad",
                "version": "2.0",
                "objects": [],
                "metadata": {}
            }
        """.trimIndent()

        val mgr = GeometricObjectManager()
        assertThrows<IncompatibleVersionException> {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `deserialize rejects incompatible appName`() {
        val json = """
            {
                "appName": "SomeOtherApp",
                "version": "1.0",
                "objects": [],
                "metadata": {}
            }
        """.trimIndent()

        val mgr = GeometricObjectManager()
        assertThrows<IncompatibleAppNameException> {
            DiagramSerializer.deserialize(json, mgr)
        }
    }

    @Test
    fun `deserialize accepts compatible minor version`() {
        val json = """
            {
                "appName": "EuclidSketchpad",
                "version": "1.99",
                "objects": [{"id": 0, "type": "FreePoint", "x": 1.0, "y": 2.0}],
                "metadata": {}
            }
        """.trimIndent()

        val mgr = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr)
        assertEquals(1, mgr.objectCount())
    }

    @Test
    fun `parseMajorVersion extracts major component`() {
        assertEquals(1, DiagramSerializer.parseMajorVersion("1.0"))
        assertEquals(1, DiagramSerializer.parseMajorVersion("1.0.0"))
        assertEquals(2, DiagramSerializer.parseMajorVersion("2.5"))
        assertEquals(10, DiagramSerializer.parseMajorVersion("10.3"))
    }

    @Test
    fun `round trip preserves intersection with sign`() {
        val mgr1 = GeometricObjectManager()
        mgr1.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 4.0, 0.0) }
        mgr1.addObject { id -> Segment(id, 0, 1) }
        mgr1.addObject { id -> FreePoint(id, 2.0, 0.0) }
        mgr1.addObject { id -> FreePoint(id, 2.0, 3.0) }
        mgr1.addObject { id -> Circle(id, 3, 4) }
        mgr1.addObject { id -> LinearCircleIntersection(id, 2, 5, 1) }

        val json = DiagramSerializer.serialize(mgr1)

        val mgr2 = GeometricObjectManager()
        DiagramSerializer.deserialize(json, mgr2)

        val ix = mgr2.getObject(6) as LinearCircleIntersection
        assertEquals(1, ix.sign)
    }
}
