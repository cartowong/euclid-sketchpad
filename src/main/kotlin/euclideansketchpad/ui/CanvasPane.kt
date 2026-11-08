package euclideansketchpad.ui

import euclideansketchpad.geometry.*
import euclideansketchpad.model.ConstructionApplicator
import euclideansketchpad.model.GeometricObjectManager
import euclideansketchpad.model.UndoManager
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Affine
import kotlin.math.abs

class CanvasPane(
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager
) : Pane() {

    private val worldGroup = Group()
    private val gridGroup = Group()
    private val objectGroup = Group()
    val previewGroup = Group()
    val transform = Affine()

    val hitTester = HitTester(manager, transform)

    private var isPanning = false
    private var panStartX = 0.0
    private var panStartY = 0.0
    private var spaceHeld = false

    private var currentTool: Tool? = null
    var selectedIds: Set<Int> = emptySet()
    var hoveredId: Int? = null
    var suppressHover = false
    var gridVisible = false
    var onEscapeFallthrough: (() -> Unit)? = null
    val exportOverlayGroup = Group().apply { isMouseTransparent = true }
    var exportPanMode = false

    // Apply mode state
    private var applyMode: ApplyModeState? = null

    private class ApplyModeState(
        val expectedTypes: List<String>,
        var currentIndex: Int = 0,
        val collectedIds: MutableList<Int> = mutableListOf(),
        val onComplete: (List<Int>) -> Unit,
        val onCancel: () -> Unit,
        val previewProvider: ((List<Int>, Int) -> List<euclideansketchpad.geometry.GeometricObject>)? = null
    )

    private var previewObjects: List<euclideansketchpad.geometry.GeometricObject> = emptyList()
    private val constructionPreviewGroup = Group().apply { isMouseTransparent = true }

    // Overlay labels (outside world transform)
    private val hintLabel = Label().apply {
        style = "-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 8; -fx-font-size: 13px;"
        isVisible = false
        isMouseTransparent = true
    }

    private val recordingBanner = Label("Recording Construction").apply {
        style = "-fx-background-color: #FF5722; -fx-text-fill: white; -fx-padding: 8; -fx-font-weight: bold; -fx-font-size: 13px;"
        isVisible = false
        isMouseTransparent = true
    }

    init {
        styleClass.add("canvas-pane")
        clip = Rectangle().also { clipRect ->
            clipRect.widthProperty().bind(widthProperty())
            clipRect.heightProperty().bind(heightProperty())
        }

        worldGroup.transforms.add(transform)
        worldGroup.isMouseTransparent = true
        worldGroup.children.addAll(gridGroup, objectGroup, previewGroup, constructionPreviewGroup)
        children.add(worldGroup)
        children.add(exportOverlayGroup)

        // Position hint label at bottom center
        hintLabel.layoutXProperty().bind(widthProperty().divide(2).subtract(hintLabel.widthProperty().divide(2)))
        hintLabel.layoutYProperty().bind(heightProperty().subtract(40))
        children.add(hintLabel)

        // Position recording banner at top center
        recordingBanner.layoutXProperty().bind(widthProperty().divide(2).subtract(recordingBanner.widthProperty().divide(2)))
        recordingBanner.layoutY = 8.0
        children.add(recordingBanner)

        // Center origin and flip y-axis once layout is known
        widthProperty().addListener { _, _, _ -> updateOriginTransform() }
        heightProperty().addListener { _, _, _ -> updateOriginTransform() }

        setupInputHandlers()
    }

    fun setTool(tool: Tool?) {
        currentTool?.deactivate(previewGroup)
        currentTool = tool
        currentTool?.activate()
    }

    fun setRecordingMode(recording: Boolean) {
        recordingBanner.isVisible = recording
        if (recording) {
            styleClass.add("recording-border")
        } else {
            styleClass.remove("recording-border")
        }
    }

    /** Returns [minX, minY, maxX, maxY] in math coordinates for the current viewport. */
    fun getViewportMathBounds(): DoubleArray {
        val inv = transform.createInverse()
        val topLeft = inv.transform(0.0, 0.0)
        val bottomRight = inv.transform(width, height)
        val minX = kotlin.math.min(topLeft.x, bottomRight.x)
        val maxX = kotlin.math.max(topLeft.x, bottomRight.x)
        val minY = kotlin.math.min(topLeft.y, bottomRight.y)
        val maxY = kotlin.math.max(topLeft.y, bottomRight.y)
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    fun startApplyMode(
        expectedTypes: List<String>,
        onComplete: (List<Int>) -> Unit,
        onCancel: () -> Unit,
        previewProvider: ((collectedIds: List<Int>, hoveredId: Int) -> List<euclideansketchpad.geometry.GeometricObject>)? = null
    ) {
        applyMode = ApplyModeState(expectedTypes, 0, mutableListOf(), onComplete, onCancel, previewProvider)
        updateHintLabel()
        requestFocus()
    }

    private fun cancelApplyMode() {
        val mode = applyMode ?: return
        mode.onCancel()
        applyMode = null
        hintLabel.isVisible = false
        clearPreviewObjects()
    }

    private fun clearPreviewObjects() {
        previewObjects = emptyList()
        constructionPreviewGroup.children.clear()
    }

    private fun updateHintLabel() {
        val mode = applyMode
        if (mode == null) {
            hintLabel.isVisible = false
            return
        }
        val idx = mode.currentIndex + 1
        val type = mode.expectedTypes[mode.currentIndex]
        hintLabel.text = "Input $idx: select a $type"
        hintLabel.isVisible = true
    }

    private fun updateOriginTransform() {
        transform.setToIdentity()
        transform.appendTranslation(width / 2, height / 2)
        transform.appendScale(1.0, -1.0) // y-up
    }

    private fun setupInputHandlers() {
        isFocusTraversable = true

        setOnKeyPressed { e -> onKeyPressed(e) }
        setOnKeyReleased { e ->
            if (e.code == KeyCode.SPACE) {
                spaceHeld = false
                isPanning = false
            }
        }

        setOnMousePressed { e -> onMousePressed(e) }
        setOnMouseDragged { e -> onMouseDragged(e) }
        setOnMouseReleased { e -> onMouseReleased(e) }
        setOnMouseMoved { e -> onMouseMoved(e) }
        setOnScroll { e -> onScroll(e) }
    }

    private fun onKeyPressed(e: KeyEvent) {
        when {
            e.code == KeyCode.SPACE -> spaceHeld = true
            e.code == KeyCode.ESCAPE -> {
                if (applyMode != null) {
                    cancelApplyMode()
                } else if (currentTool?.onEscapePressed(previewGroup) != true) {
                    onEscapeFallthrough?.invoke()
                }
            }
            // Undo/Redo handled by menu accelerators in SketchpadApp
        }
    }

    private fun screenToWorld(screenX: Double, screenY: Double): javafx.geometry.Point2D {
        val inv = transform.createInverse()
        return inv.transform(screenX, screenY)
    }

    private fun onMousePressed(e: MouseEvent) {
        requestFocus()
        if ((spaceHeld || exportPanMode) && e.button == MouseButton.PRIMARY) {
            isPanning = true
            panStartX = e.screenX
            panStartY = e.screenY
            return
        }
        if (e.button == MouseButton.PRIMARY && !isPanning) {
            // Apply mode intercepts clicks
            val mode = applyMode
            if (mode != null) {
                handleApplyModeClick(e)
                return
            }
            val world = screenToWorld(e.x, e.y)
            currentTool?.onMousePressed(e, world.x, world.y)
        }
    }

    private fun handleApplyModeClick(e: MouseEvent) {
        val mode = applyMode ?: return
        val expectedType = mode.expectedTypes[mode.currentIndex]

        // Type-filtered hit test: find the nearest object matching the expected type
        val hit = hitTester.hitTest(e.x, e.y)
        val hitId = when (hit) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnLinear -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }
        if (hitId == null) return

        val obj = manager.getObject(hitId) ?: return
        if (!ConstructionApplicator.matchesExpectedType(obj, expectedType)) return

        mode.collectedIds.add(hitId)
        mode.currentIndex++

        if (mode.currentIndex >= mode.expectedTypes.size) {
            // All inputs collected
            val collected = mode.collectedIds.toList()
            applyMode = null
            hintLabel.isVisible = false
            clearPreviewObjects()
            mode.onComplete(collected)
        } else {
            updateHintLabel()
        }
    }

    private fun onMouseDragged(e: MouseEvent) {
        if (isPanning) {
            val dx = e.screenX - panStartX
            val dy = e.screenY - panStartY
            transform.appendTranslation(dx / transform.mxx, dy / transform.myy)
            panStartX = e.screenX
            panStartY = e.screenY
            return
        }
        if (e.isPrimaryButtonDown) {
            if (applyMode != null) return
            val world = screenToWorld(e.x, e.y)
            currentTool?.onMouseDragged(e, world.x, world.y)
            updateHover(e.x, e.y)
        }
    }

    private fun onMouseReleased(e: MouseEvent) {
        if (isPanning) {
            isPanning = false
            return
        }
        if (applyMode != null) return
        val world = screenToWorld(e.x, e.y)
        currentTool?.onMouseReleased(e, world.x, world.y)
    }

    private fun onMouseMoved(e: MouseEvent) {
        if (exportPanMode) return
        val world = screenToWorld(e.x, e.y)
        if (applyMode == null) {
            currentTool?.onMouseMoved(e, world.x, world.y)
        }
        updateHover(e.x, e.y)
    }

    private fun onScroll(e: ScrollEvent) {
        val zoomFactor = if (e.deltaY > 0) 1.1 else 1.0 / 1.1
        val mouseX = e.x
        val mouseY = e.y

        val inverted = transform.createInverse()
        val worldPt = inverted.transform(mouseX, mouseY)

        transform.appendScale(zoomFactor, zoomFactor, worldPt.x, worldPt.y)
    }

    private fun updateHover(screenX: Double, screenY: Double) {
        if (exportPanMode || suppressHover) {
            if (hoveredId != null) {
                hoveredId = null
                renderAll()
            }
            return
        }

        val mode = applyMode
        if (mode != null) {
            // Type-filtered hover: only highlight objects matching the current expected type
            val expectedType = mode.expectedTypes[mode.currentIndex]
            val hit = hitTester.hitTest(screenX, screenY)
            val hitId = when (hit) {
                is HitResult.ConstructedPoint -> hit.id
                is HitResult.OnAngle -> hit.id
                is HitResult.OnLinear -> hit.id
                is HitResult.OnCircle -> hit.id
                is HitResult.OnRegion -> hit.id
                else -> null
            }
            val newHoveredId = if (hitId != null) {
                val obj = manager.getObject(hitId)
                if (obj != null && ConstructionApplicator.matchesExpectedType(obj, expectedType)) hitId else null
            } else null

            if (newHoveredId != hoveredId) {
                hoveredId = newHoveredId
                updatePreviewObjects(mode, newHoveredId)
                renderAll()
            }
            return
        }

        val newHoveredId = when (val hit = hitTester.hitTest(screenX, screenY)) {
            is HitResult.ConstructedPoint -> hit.id
            is HitResult.OnAngle -> hit.id
            is HitResult.OnLinear -> hit.id
            is HitResult.OnCircle -> hit.id
            is HitResult.OnRegion -> hit.id
            else -> null
        }
        if (newHoveredId != hoveredId) {
            hoveredId = newHoveredId
            renderAll()
        }
    }

    private fun updatePreviewObjects(mode: ApplyModeState, newHoveredId: Int?) {
        val provider = mode.previewProvider
        val isLastInput = mode.currentIndex == mode.expectedTypes.size - 1
        if (provider != null && isLastInput && newHoveredId != null) {
            previewObjects = provider(mode.collectedIds.toList(), newHoveredId)
        } else {
            previewObjects = emptyList()
        }
    }

    fun renderAll() {
        gridGroup.children.clear()
        if (gridVisible) drawGrid()
        val vpBounds = getViewportMathBounds()
        GeometryRenderer.renderAll(manager, objectGroup, transform, selectedIds, hoveredId, vpBounds)
        constructionPreviewGroup.children.clear()
        if (previewObjects.isNotEmpty()) {
            GeometryRenderer.renderPreviewObjects(previewObjects, constructionPreviewGroup, transform, vpBounds)
        }
    }

    private fun drawGrid() {
        gridGroup.children.clear()
        val scale = abs(transform.mxx)
        val counterScale = if (scale != 0.0) 1.0 / scale else 1.0

        val spacing = GeometryUtils.computeAdaptiveGridSpacing(scale)

        val extent = 5000.0 * counterScale
        val gridColor = Color.gray(0.85)
        val axisColor = Color.gray(0.5)

        // Grid lines
        var v = spacing
        while (v < extent) {
            for (sign in listOf(-1.0, 1.0)) {
                val pos = sign * v
                val hLine = Line(-extent, pos, extent, pos)
                hLine.stroke = gridColor
                hLine.strokeWidth = 0.5 * counterScale
                gridGroup.children.add(hLine)

                val vLine = Line(pos, -extent, pos, extent)
                vLine.stroke = gridColor
                vLine.strokeWidth = 0.5 * counterScale
                gridGroup.children.add(vLine)
            }
            v += spacing
        }

        // Axes
        val xAxis = Line(-extent, 0.0, extent, 0.0)
        xAxis.stroke = axisColor
        xAxis.strokeWidth = 1.0 * counterScale
        gridGroup.children.add(xAxis)

        val yAxis = Line(0.0, -extent, 0.0, extent)
        yAxis.stroke = axisColor
        yAxis.strokeWidth = 1.0 * counterScale
        gridGroup.children.add(yAxis)
    }
}
