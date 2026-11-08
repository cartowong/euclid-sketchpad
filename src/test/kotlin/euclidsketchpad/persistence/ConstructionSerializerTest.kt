package euclidsketchpad.persistence

import euclidsketchpad.APP_NAME
import euclidsketchpad.APP_VERSION
import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstructionSerializerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `save and load construction preserves structure`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 0.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        // Mark as if recording
        mgr.getMetadata(p1.id)!!.isInput = true
        mgr.getMetadata(p2.id)!!.isInput = true

        val file = File(tempDir, "test.json")
        ConstructionSerializer.saveConstruction(mgr, file)

        val loaded = ConstructionSerializer.loadFromFile(file)

        assertEquals("test", loaded.fileName)
        assertEquals(2, loaded.inputIds.size)
        assertEquals(2, loaded.inputTypes.size)
        assertEquals("Point", loaded.inputTypes[0])
        assertEquals("Point", loaded.inputTypes[1])
        // Diagram should have 3 objects total
        assertEquals(3, loaded.diagram.objects.size)
    }

    @Test
    fun `load construction labels are nulled out`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr.getMetadata(p1.id)!!.label = "A"
        mgr.getMetadata(p1.id)!!.labelVisible = true
        mgr.getMetadata(p1.id)!!.isInput = true

        val file = File(tempDir, "labeled.json")
        ConstructionSerializer.saveConstruction(mgr, file)

        val loaded = ConstructionSerializer.loadFromFile(file)
        val meta = loaded.diagram.metadata[p1.id]!!

        assertEquals(null, meta.label)
        assertEquals(false, meta.labelVisible)
    }

    @Test
    fun `load construction preserves isInput and isLocked flags`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 1.0, 0.0) }
        mgr.getMetadata(p1.id)!!.isInput = true
        mgr.getMetadata(p2.id)!!.isLocked = true

        val file = File(tempDir, "flags.json")
        ConstructionSerializer.saveConstruction(mgr, file)

        val loaded = ConstructionSerializer.loadFromFile(file)
        assertTrue(loaded.diagram.metadata[p1.id]!!.isInput)
        assertTrue(loaded.diagram.metadata[p2.id]!!.isLocked)
    }

    @Test
    fun `save construction includes appName and version`() {
        val mgr = GeometricObjectManager()
        mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        mgr.getMetadata(0)!!.isInput = true

        val file = File(tempDir, "versioned.json")
        ConstructionSerializer.saveConstruction(mgr, file)

        val diagram = DiagramSerializer.parseDiagram(file.readText())
        assertEquals(APP_NAME, diagram.appName)
        assertEquals(APP_VERSION, diagram.version)
    }

    @Test
    fun `load construction rejects incompatible major version`() {
        val file = File(tempDir, "bad_version.json")
        file.writeText("""
            {
                "appName": "EuclidSketchpad",
                "version": "2.0",
                "objects": [{"id": 0, "type": "FreePoint", "x": 0.0, "y": 0.0}],
                "metadata": {"0": {"isInput": true}}
            }
        """.trimIndent())

        assertThrows<IncompatibleVersionException> {
            ConstructionSerializer.loadFromFile(file)
        }
    }

    @Test
    fun `load construction rejects incompatible appName`() {
        val file = File(tempDir, "bad_app.json")
        file.writeText("""
            {
                "appName": "SomeOtherApp",
                "version": "1.0",
                "objects": [{"id": 0, "type": "FreePoint", "x": 0.0, "y": 0.0}],
                "metadata": {"0": {"isInput": true}}
            }
        """.trimIndent())

        assertThrows<IncompatibleAppNameException> {
            ConstructionSerializer.loadFromFile(file)
        }
    }

    @Test
    fun `non-point input types displayed as exact type`() {
        val mgr = GeometricObjectManager()
        val p1 = mgr.addObject { id -> FreePoint(id, 0.0, 0.0) }
        val p2 = mgr.addObject { id -> FreePoint(id, 3.0, 0.0) }
        val seg = mgr.addObject { id -> Segment(id, p1.id, p2.id) }

        mgr.getMetadata(p1.id)!!.isInput = true
        mgr.getMetadata(seg.id)!!.isInput = true

        val file = File(tempDir, "segInput.json")
        ConstructionSerializer.saveConstruction(mgr, file)

        val loaded = ConstructionSerializer.loadFromFile(file)
        assertEquals("Point", loaded.inputTypes[0])
        assertEquals("Segment", loaded.inputTypes[1])
    }
}
