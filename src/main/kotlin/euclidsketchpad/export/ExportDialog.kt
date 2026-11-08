package euclidsketchpad.export

import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.ui.CanvasPane
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage

class ExportDialog(
    private val owner: Stage,
    private val canvasPane: CanvasPane,
    private val manager: GeometricObjectManager,
    private val onExport: (ExportSettings, java.io.File) -> Unit,
    private val onCustomModeChanged: (Boolean) -> Unit = {},
    private val onDialogClosed: () -> Unit = {}
) {

    private val stage = Stage()

    // Method radios
    private val methodGroup = ToggleGroup()
    private val canvasRadio = RadioButton("Canvas").apply { toggleGroup = methodGroup; isSelected = true }
    private val customRadio = RadioButton("Custom").apply { toggleGroup = methodGroup }
    private val autoFitRadio = RadioButton("Auto-fit").apply { toggleGroup = methodGroup }

    // Format radios
    private val formatGroup = ToggleGroup()
    private val pngRadio = RadioButton("PNG").apply { toggleGroup = formatGroup; isSelected = true }
    private val svgRadio = RadioButton("SVG").apply { toggleGroup = formatGroup }

    // Checkboxes
    private val transparentBgCheck = CheckBox("Transparent background")
    private val includeGridCheck = CheckBox("Include grid")

    // Method-specific fields
    private val widthField = TextField("1920")
    private val heightField = TextField("1080")
    private val widthLabel = Label("Width:")
    private val heightLabel = Label("Height:")
    private val scaleLabel = Label()

    // Overlay rectangles for Custom mode bounding box
    private val overlayGroup: Group get() = canvasPane.exportOverlayGroup

    // Guard to prevent infinite listener loops in Auto-fit mode
    private var updatingAutoFit = false

    init {
        stage.initOwner(owner)
        stage.initModality(Modality.NONE)
        stage.title = "Export"
        stage.isResizable = false

        val root = VBox(12.0).apply { padding = Insets(16.0) }

        // Method section
        root.children.add(Label("Method:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(HBox(12.0, canvasRadio, customRadio, autoFitRadio))

        // Format section
        root.children.add(Label("Format:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(HBox(12.0, pngRadio, svgRadio))

        // Options
        root.children.add(Label("Options:").apply { style = "-fx-font-weight: bold;" })
        root.children.add(VBox(6.0, transparentBgCheck, includeGridCheck))

        // Dimensions grid
        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 6.0
        }
        grid.add(widthLabel, 0, 0)
        grid.add(widthField, 1, 0)
        grid.add(heightLabel, 0, 1)
        grid.add(heightField, 1, 1)
        grid.add(scaleLabel, 0, 2, 2, 1)
        widthField.prefWidth = 100.0
        heightField.prefWidth = 100.0
        root.children.add(grid)

        // Buttons
        val exportBtn = Button("Export")
        val cancelBtn = Button("Cancel")
        val buttonBox = HBox(8.0, exportBtn, cancelBtn).apply { alignment = Pos.CENTER_RIGHT }
        root.children.add(buttonBox)

        exportBtn.setOnAction { doExport() }
        cancelBtn.setOnAction { close() }

        stage.setOnCloseRequest {
            canvasPane.exportPanMode = false
            onCustomModeChanged(false)
            clearOverlay()
            onDialogClosed()
        }

        // React to method changes
        methodGroup.selectedToggleProperty().addListener { _, _, _ -> updateFieldStates() }

        // React to dimension changes
        widthField.textProperty().addListener { _, _, _ ->
            updateOverlay()
            if (selectedMethod() == ExportMethod.AUTO_FIT && !updatingAutoFit) {
                updatingAutoFit = true
                updateAutoFitFromWidth()
                updatingAutoFit = false
            }
        }
        heightField.textProperty().addListener { _, _, _ ->
            updateOverlay()
            if (selectedMethod() == ExportMethod.AUTO_FIT && !updatingAutoFit) {
                updatingAutoFit = true
                updateAutoFitFromHeight()
                updatingAutoFit = false
            }
        }

        stage.scene = Scene(root)
        updateFieldStates()
    }

    fun show() {
        stage.show()
        updateFieldStates()
    }

    fun close() {
        canvasPane.exportPanMode = false
        onCustomModeChanged(false)
        clearOverlay()
        stage.close()
        onDialogClosed()
    }

    private fun updateFieldStates() {
        val method = selectedMethod()
        val enteringCustom = method == ExportMethod.CUSTOM
        canvasPane.exportPanMode = enteringCustom
        onCustomModeChanged(enteringCustom)

        when (method) {
            ExportMethod.CANVAS -> {
                widthField.isEditable = false
                heightField.isEditable = false
                widthField.text = canvasPane.width.toInt().toString()
                heightField.text = canvasPane.height.toInt().toString()
                scaleLabel.text = ""
                clearOverlay()
            }
            ExportMethod.CUSTOM -> {
                widthField.isEditable = true
                heightField.isEditable = true
                if (widthField.text.isBlank()) widthField.text = "1920"
                if (heightField.text.isBlank()) heightField.text = "1080"
                updateOverlay()
            }
            ExportMethod.AUTO_FIT -> {
                widthField.isEditable = true
                heightField.isEditable = true
                updatingAutoFit = true
                if (widthField.text.isBlank()) widthField.text = "1920"
                updateAutoFitFromWidth()
                updatingAutoFit = false
                clearOverlay()
            }
        }
    }

    private fun autoFitAspect(): Double? {
        val bounds = ExportRenderer.computeAutoFitBounds(manager)
        val bw = bounds[2] - bounds[0]
        val bh = bounds[3] - bounds[1]
        if (bw <= 0 || bh <= 0) return null
        return bw / bh
    }

    private fun updateAutoFitFromWidth() {
        val aspect = autoFitAspect() ?: return
        val w = widthField.text.toIntOrNull() ?: return
        heightField.text = (w / aspect).toInt().toString()
    }

    private fun updateAutoFitFromHeight() {
        val aspect = autoFitAspect() ?: return
        val h = heightField.text.toIntOrNull() ?: return
        widthField.text = (h * aspect).toInt().toString()
    }

    private fun updateOverlay() {
        if (selectedMethod() != ExportMethod.CUSTOM) {
            clearOverlay()
            return
        }

        val outW = widthField.text.toDoubleOrNull() ?: return
        val outH = heightField.text.toDoubleOrNull() ?: return
        if (outW <= 0 || outH <= 0) {
            clearOverlay()
            return
        }

        val canvasW = canvasPane.width
        val canvasH = canvasPane.height
        if (canvasW <= 0 || canvasH <= 0) return

        // Scale overlay to fit ~80% of canvas
        val aspect = outW / outH
        val maxW = canvasW * 0.8
        val maxH = canvasH * 0.8
        val overlayW: Double
        val overlayH: Double
        if (maxW / aspect <= maxH) {
            overlayW = maxW
            overlayH = maxW / aspect
        } else {
            overlayH = maxH
            overlayW = maxH * aspect
        }

        val left = (canvasW - overlayW) / 2.0
        val top = (canvasH - overlayH) / 2.0

        val scalePct = (overlayW / outW * 100).toInt()
        scaleLabel.text = "Preview at $scalePct%"

        // Letterbox: 4 dark rectangles around the clear center
        overlayGroup.children.clear()
        val dim = Color.rgb(0, 0, 0, 0.4)

        // Top bar
        overlayGroup.children.add(Rectangle(canvasW, top).apply { fill = dim })
        // Bottom bar
        overlayGroup.children.add(Rectangle(canvasW, canvasH - top - overlayH).apply {
            fill = dim; layoutY = top + overlayH
        })
        // Left bar
        overlayGroup.children.add(Rectangle(left, overlayH).apply { fill = dim; layoutY = top })
        // Right bar
        overlayGroup.children.add(Rectangle(canvasW - left - overlayW, overlayH).apply {
            fill = dim; layoutX = left + overlayW; layoutY = top
        })

        // Border around clear area
        overlayGroup.children.add(Rectangle(overlayW, overlayH).apply {
            fill = Color.TRANSPARENT
            stroke = Color.web("#FF5722")
            strokeWidth = 2.0
            layoutX = left
            layoutY = top
        })
    }

    private fun clearOverlay() {
        overlayGroup.children.clear()
    }

    private fun selectedMethod(): ExportMethod = when (methodGroup.selectedToggle) {
        customRadio -> ExportMethod.CUSTOM
        autoFitRadio -> ExportMethod.AUTO_FIT
        else -> ExportMethod.CANVAS
    }

    private fun selectedFormat(): ExportFormat = when (formatGroup.selectedToggle) {
        svgRadio -> ExportFormat.SVG
        else -> ExportFormat.PNG
    }

    private fun doExport() {
        val method = selectedMethod()
        val format = selectedFormat()
        val outW = widthField.text.toIntOrNull()
        val outH = heightField.text.toIntOrNull()

        if (outW == null || outH == null || outW <= 0 || outH <= 0) {
            Alert(Alert.AlertType.ERROR, "Invalid dimensions.").showAndWait()
            return
        }

        val bounds = computeBounds(method, outW, outH) ?: return

        val ext = if (format == ExportFormat.PNG) "*.png" else "*.svg"
        val desc = if (format == ExportFormat.PNG) "PNG images" else "SVG images"
        val chooser = FileChooser()
        chooser.extensionFilters.add(FileChooser.ExtensionFilter(desc, ext))
        chooser.title = "Export Image"
        val file = chooser.showSaveDialog(stage) ?: return

        val settings = ExportSettings(
            method = method,
            format = format,
            outputWidth = outW,
            outputHeight = outH,
            transparentBackground = transparentBgCheck.isSelected,
            includeGrid = includeGridCheck.isSelected,
            boundsMinX = bounds[0],
            boundsMinY = bounds[1],
            boundsMaxX = bounds[2],
            boundsMaxY = bounds[3]
        )

        onExport(settings, file)
        close()
    }

    private fun computeBounds(method: ExportMethod, outW: Int, outH: Int): DoubleArray? {
        return when (method) {
            ExportMethod.CANVAS -> {
                canvasPane.getViewportMathBounds()
            }
            ExportMethod.CUSTOM -> {
                // Compute math bounds from the overlay center rectangle
                val canvasW = canvasPane.width
                val canvasH = canvasPane.height
                val aspect = outW.toDouble() / outH.toDouble()
                val maxW = canvasW * 0.8
                val maxH = canvasH * 0.8
                val overlayW: Double
                val overlayH: Double
                if (maxW / aspect <= maxH) {
                    overlayW = maxW
                    overlayH = maxW / aspect
                } else {
                    overlayH = maxH
                    overlayW = maxH * aspect
                }
                val left = (canvasW - overlayW) / 2.0
                val top = (canvasH - overlayH) / 2.0

                // Convert overlay screen corners to math coordinates
                val inv = canvasPane.transform.createInverse()
                val topLeft = inv.transform(left, top)
                val bottomRight = inv.transform(left + overlayW, top + overlayH)
                val minX = kotlin.math.min(topLeft.x, bottomRight.x)
                val maxX = kotlin.math.max(topLeft.x, bottomRight.x)
                val minY = kotlin.math.min(topLeft.y, bottomRight.y)
                val maxY = kotlin.math.max(topLeft.y, bottomRight.y)
                doubleArrayOf(minX, minY, maxX, maxY)
            }
            ExportMethod.AUTO_FIT -> {
                ExportRenderer.computeAutoFitBounds(manager)
            }
        }
    }
}
