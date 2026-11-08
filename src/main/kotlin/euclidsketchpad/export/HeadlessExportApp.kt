package euclidsketchpad.export

import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.persistence.DiagramSerializer
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import java.io.File

/**
 * Headless JavaFX application that loads a diagram and exports it in all
 * method/format combinations (6 files total), then exits.
 *
 * Usage: --export <diagram.json> [outputDir]
 */
class HeadlessExportApp : Application() {

    override fun start(stage: Stage) {
        try {
            val diagramPath = exportArgs[0]
            val outputDir = if (exportArgs.size > 1) exportArgs[1] else "exports"

            val diagramFile = File(diagramPath)
            require(diagramFile.exists()) { "Diagram file not found: $diagramPath" }

            val outDir = File(outputDir)
            outDir.mkdirs()

            val json = diagramFile.readText()
            val manager = GeometricObjectManager()
            DiagramSerializer.deserialize(json, manager)

            val baseName = diagramFile.nameWithoutExtension
            val autoBounds = ExportRenderer.computeAutoFitBounds(manager)

            // Canvas: default viewport [-500, -350, 500, 350] at 1000x700
            for (format in ExportFormat.entries) {
                val settings = ExportSettings(
                    method = ExportMethod.CANVAS,
                    format = format,
                    outputWidth = 1000,
                    outputHeight = 700,
                    transparentBackground = false,
                    includeGrid = false,
                    boundsMinX = -500.0,
                    boundsMinY = -350.0,
                    boundsMaxX = 500.0,
                    boundsMaxY = 350.0
                )
                val ext = format.name.lowercase()
                val file = File(outDir, "${baseName}_canvas.$ext")
                ExportRenderer.export(settings, manager, file)
                println("Exported: ${file.path}")
            }

            // Custom: auto-fit bounds at 1920x1080
            for (format in ExportFormat.entries) {
                val settings = ExportSettings(
                    method = ExportMethod.CUSTOM,
                    format = format,
                    outputWidth = 1920,
                    outputHeight = 1080,
                    transparentBackground = false,
                    includeGrid = false,
                    boundsMinX = autoBounds[0],
                    boundsMinY = autoBounds[1],
                    boundsMaxX = autoBounds[2],
                    boundsMaxY = autoBounds[3]
                )
                val ext = format.name.lowercase()
                val file = File(outDir, "${baseName}_custom.$ext")
                ExportRenderer.export(settings, manager, file)
                println("Exported: ${file.path}")
            }

            // Auto-fit: auto-fit bounds, width=1920, height from aspect ratio
            val boundsW = autoBounds[2] - autoBounds[0]
            val boundsH = autoBounds[3] - autoBounds[1]
            val autoHeight = ((1920.0 * boundsH) / boundsW).toInt().coerceAtLeast(1)
            for (format in ExportFormat.entries) {
                val settings = ExportSettings(
                    method = ExportMethod.AUTO_FIT,
                    format = format,
                    outputWidth = 1920,
                    outputHeight = autoHeight,
                    transparentBackground = false,
                    includeGrid = false,
                    boundsMinX = autoBounds[0],
                    boundsMinY = autoBounds[1],
                    boundsMaxX = autoBounds[2],
                    boundsMaxY = autoBounds[3]
                )
                val ext = format.name.lowercase()
                val file = File(outDir, "${baseName}_autofit.$ext")
                ExportRenderer.export(settings, manager, file)
                println("Exported: ${file.path}")
            }

            println("Done. 6 files written to $outputDir")
        } catch (e: Exception) {
            System.err.println("Export failed: ${e.message}")
            e.printStackTrace()
        } finally {
            Platform.exit()
        }
    }

    companion object {
        private lateinit var exportArgs: Array<String>

        fun run(args: Array<String>) {
            exportArgs = args
            launch(HeadlessExportApp::class.java)
        }
    }
}
