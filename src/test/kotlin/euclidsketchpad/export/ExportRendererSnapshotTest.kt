package euclidsketchpad.export

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.ObjectMetadata
import javafx.application.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Golden-file tests for PNG and SVG export. Goldens live in
 * src/test/resources/export-snapshots/. To regenerate them after an intended
 * output change, run: UPDATE_SNAPSHOTS=1 ./gradlew test --tests '*ExportRendererSnapshotTest*'
 */
class ExportRendererSnapshotTest {

    companion object {
        private val SNAPSHOT_DIR = File("src/test/resources/export-snapshots")
        private val UPDATE = System.getenv("UPDATE_SNAPSHOTS") == "1"

        // Kept under build/ (not a temp dir) so CI can upload them when a snapshot differs.
        fun actualOutput(name: String) = File("build/snapshot-actual", name).also { it.parentFile.mkdirs() }

        @JvmStatic
        @BeforeAll
        fun initJfx() {
            val latch = CountDownLatch(1)
            try {
                Platform.startup { latch.countDown() }
            } catch (e: IllegalStateException) {
                latch.countDown()
            }
            latch.await()
        }

        /** Builds a diagram that exercises every render pass and style variant. */
        fun buildFixture(): GeometricObjectManager {
            val m = GeometricObjectManager()
            fun meta(id: Int, block: ObjectMetadata.() -> Unit) = m.getMetadata(id)!!.block()
            fun point(x: Double, y: Double) = m.addObject { FreePoint(it, x, y) }.id

            val a = point(-100.0, -50.0)
            val b = point(50.0, -80.0)
            val c = point(0.0, 80.0)
            val region = m.addObject { Region(it, listOf(a, b, c)) }.id
            meta(region) { color = "yellow" }
            val angle = m.addObject { Angle(it, b, a, c) }.id
            meta(angle) { color = "green"; size = "large" }

            val seg = m.addObject { Segment(it, a, b) }.id
            meta(seg) { color = "red"; strokeThickness = "thick"; label = "s"; labelVisible = true; labelOffsetX = 5.0; labelOffsetY = 12.0 }
            val seg2 = m.addObject { Segment(it, b, c) }.id
            meta(seg2) { strokeStyle = "dashed"; strokeThickness = "thin" }

            val d = point(-150.0, 100.0)
            val e = point(-80.0, 120.0)
            val line = m.addObject { Line(it, d, e) }.id
            meta(line) { color = "brown"; strokeStyle = "dashed" }

            val f = point(-170.0, -120.0)
            val g = point(-120.0, -90.0)
            val ray = m.addObject { Ray(it, f, g) }.id
            meta(ray) { color = "purple" }

            val c1 = point(60.0, 40.0)
            val c1r = point(60.0, 80.0)
            val circle1 = m.addObject { Circle(it, c1, c1r) }.id
            meta(circle1) { label = "c"; labelVisible = true }

            // Center chosen so the circle is clipped on the right and bottom only
            // (vertically asymmetric), producing multiple visible arcs.
            val c2 = point(150.0, -100.0)
            val c2r = point(150.0, -30.0)
            val circle2 = m.addObject { Circle(it, c2, c2r) }.id
            meta(circle2) { color = "red"; strokeStyle = "dashed"; strokeThickness = "thick" }

            meta(a) { label = "A"; labelVisible = true; size = "large"; color = "red" }
            meta(b) { label = "B"; labelVisible = true; labelOffsetX = -20.0; labelOffsetY = -18.0; size = "small" }
            meta(c) { label = "C"; labelVisible = true; color = "purple" }
            meta(e) { visible = false }
            return m
        }

        fun fixtureSettings(format: ExportFormat, transparent: Boolean = false) = ExportSettings(
            method = ExportMethod.CUSTOM,
            format = format,
            outputWidth = 800,
            outputHeight = 600,
            transparentBackground = transparent,
            includeGrid = true,
            boundsMinX = -200.0,
            boundsMinY = -150.0,
            boundsMaxX = 200.0,
            boundsMaxY = 150.0
        )

        /** PNG export snapshots a node, which must happen on the FX thread. */
        fun exportOnFxThread(settings: ExportSettings, manager: GeometricObjectManager, file: File): List<String> {
            var result: List<String> = emptyList()
            var error: Throwable? = null
            val latch = CountDownLatch(1)
            Platform.runLater {
                try {
                    result = ExportRenderer.export(settings, manager, file)
                } catch (t: Throwable) {
                    error = t
                } finally {
                    latch.countDown()
                }
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS), "export timed out")
            error?.let { throw it }
            return result
        }
    }

    @Test
    fun `SVG export matches golden`() {
        val out = actualOutput("fixture.svg")
        exportOnFxThread(fixtureSettings(ExportFormat.SVG), buildFixture(), out)
        val golden = File(SNAPSHOT_DIR, "fixture.svg")
        if (UPDATE) {
            SNAPSHOT_DIR.mkdirs()
            out.copyTo(golden, overwrite = true)
        }
        assertEquals(golden.readText(), out.readText())
    }

    @Test
    fun `PNG export matches golden`() {
        val out = actualOutput("fixture.png")
        exportOnFxThread(fixtureSettings(ExportFormat.PNG), buildFixture(), out)
        val golden = File(SNAPSHOT_DIR, "fixture.png")
        if (UPDATE) {
            SNAPSHOT_DIR.mkdirs()
            out.copyTo(golden, overwrite = true)
        }
        val expected = ImageIO.read(golden)
        val actual = ImageIO.read(out)
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)

        // Tolerance absorbs antialiasing/text-rasterization drift between JavaFX
        // builds and GPU/software pipelines (CI runs macOS-latest). Kept tight: the
        // pre-fix PNG bug (clipped arcs mirrored vertically) differed in only 0.24%.
        val maxChannelDelta = 8
        var differing = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val p = expected.getRGB(x, y)
                val q = actual.getRGB(x, y)
                if (p == q) continue
                val maxDelta = (0..24 step 8).maxOf { shift -> abs(((p ushr shift) and 0xFF) - ((q ushr shift) and 0xFF)) }
                if (maxDelta > maxChannelDelta) differing++
            }
        }
        val fraction = differing.toDouble() / (expected.width * expected.height)
        assertTrue(fraction <= 0.0005, "PNG differs from golden in $differing pixels (${"%.3f".format(fraction * 100)}%)")
    }
}
