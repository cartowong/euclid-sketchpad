package euclidsketchpad.export

import euclidsketchpad.APP_NAME
import euclidsketchpad.APP_VERSION
import euclidsketchpad.export.ExportRendererSnapshotTest.Companion.buildFixture
import euclidsketchpad.export.ExportRendererSnapshotTest.Companion.exportOnFxThread
import euclidsketchpad.export.ExportRendererSnapshotTest.Companion.fixtureSettings
import euclidsketchpad.geometry.FreePoint
import euclidsketchpad.model.GeometricObjectManager
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File

class PdfExportTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initJfx() = ExportRendererSnapshotTest.initJfx()
    }

    private fun exportPdf(dir: File, manager: GeometricObjectManager = buildFixture(), transparent: Boolean = false, grid: Boolean = true): Pair<File, List<String>> {
        val file = File(dir, "my-diagram.pdf")
        val settings = fixtureSettings(ExportFormat.PDF, transparent).copy(includeGrid = grid)
        return file to exportOnFxThread(settings, manager, file)
    }

    private fun renderPage(file: File): BufferedImage =
        Loader.loadPDF(file).use { PDFRenderer(it).renderImageWithDPI(0, 72f, ImageType.ARGB) }

    @Test
    fun `PDF has one page of exactly W x H points and metadata`(@TempDir tmp: File) {
        val (file, warnings) = exportPdf(tmp)
        assertTrue(warnings.isEmpty())
        Loader.loadPDF(file).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val box = doc.getPage(0).mediaBox
            assertEquals(800f, box.width)
            assertEquals(600f, box.height)
            assertEquals("my-diagram", doc.documentInformation.title)
            assertEquals("$APP_NAME $APP_VERSION", doc.documentInformation.creator)
        }
    }

    @Test
    fun `PDF embeds a DejaVu Sans subset and contains label text`(@TempDir tmp: File) {
        val (file, _) = exportPdf(tmp)
        Loader.loadPDF(file).use { doc ->
            val resources = doc.getPage(0).resources
            val fonts = resources.fontNames.map { resources.getFont(it) }
            val font = fonts.single() as PDType0Font
            // Subset fonts get a six-letter tag prefix, e.g. "ABCDEF+DejaVuSans".
            assertTrue(Regex("^[A-Z]{6}\\+DejaVuSans$").matches(font.name), font.name)
            assertNotNull(font.descendantFont.fontDescriptor.fontFile2, "font program must be embedded")

            val text = PDFTextStripper().getText(doc)
            for (label in listOf("A", "B", "C", "s", "c")) {
                assertTrue(text.contains(label), "missing label $label in: $text")
            }
        }
    }

    @Test
    fun `transparent background omits the white page fill`(@TempDir tmp: File) {
        val opaqueDir = File(tmp, "opaque").apply { mkdirs() }
        val clearDir = File(tmp, "clear").apply { mkdirs() }
        val opaque = renderPage(exportPdf(opaqueDir, grid = false).first)
        val clear = renderPage(exportPdf(clearDir, transparent = true, grid = false).first)
        // (5, 5) is empty canvas in the fixture.
        assertEquals(0xFFFFFFFF.toInt(), opaque.getRGB(5, 5))
        assertEquals(0, clear.getRGB(5, 5) ushr 24)
    }

    @Test
    fun `objects are drawn as vectors at their pixel positions`(@TempDir tmp: File) {
        val image = renderPage(exportPdf(tmp, grid = false).first)
        // Point A: math (-100, -50) -> pixel (200, 400), large red (#F44336) dot.
        val rgb = image.getRGB(200, 400)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        assertTrue(r > 200 && g < 120 && b < 120, "expected red at point A, got ${Integer.toHexString(rgb)}")
    }

    @Test
    fun `characters missing from the font become question marks with a warning`(@TempDir tmp: File) {
        val manager = GeometricObjectManager()
        val p = manager.addObject { FreePoint(it, 0.0, 0.0) }.id
        // U+1F9E0 (Unicode 10) postdates DejaVu Sans 2.37, which does cover the older emoticon block.
        manager.getMetadata(p)!!.apply { label = "P\u4E2D\uD83E\uDDE0"; labelVisible = true }
        val (file, warnings) = exportPdf(tmp, manager)
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("\u4E2D") && warnings[0].contains("\uD83E\uDDE0"), warnings[0])
        Loader.loadPDF(file).use { doc ->
            assertTrue(PDFTextStripper().getText(doc).contains("P??"))
        }
    }
}
