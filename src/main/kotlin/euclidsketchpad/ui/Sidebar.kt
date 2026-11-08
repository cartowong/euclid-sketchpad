package euclidsketchpad.ui

import euclidsketchpad.geometry.*
import euclidsketchpad.model.GeometricObjectManager
import euclidsketchpad.model.UndoManager
import euclidsketchpad.persistence.LoadedConstruction
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Text

class Sidebar(
    private val manager: GeometricObjectManager,
    private val undoManager: UndoManager,
    private val hitTester: HitTester,
    private val onSelectionChanged: (Set<Int>) -> Unit,
    private val requestRender: () -> Unit,
    private val onApplyConstruction: (LoadedConstruction) -> Unit
) {

    private val showHiddenCheckbox = CheckBox("Show hidden objects")
    private val objectTable = TableView<ObjectRow>()
    private val measurementTable = TableView<MeasurementRow>()
    private val constructionTable = TableView<ConstructionRow>()
    private val constructionItems = FXCollections.observableArrayList<ConstructionRow>()

    private var externalSelectedIds = mutableSetOf<Int>()
    private var updatingFromExternal = false

    var onCloseObjectList: (() -> Unit)? = null
    var onCloseConstructions: (() -> Unit)? = null
    var onCloseMeasurements: (() -> Unit)? = null

    val leftPane: VBox
    val rightPane: VBox
    private val rightSplitPane: SplitPane
    private val constructionsBox: VBox
    private val measurementsBox: VBox
    private var objectCloseBtn: Button
    private var constructionCloseBtn: Button
    private var measurementCloseBtn: Button

    data class ObjectRow(
        val id: Int,
        val type: String,
        val parents: String,
        val label: SimpleStringProperty,
        val visible: SimpleBooleanProperty,
        val measurable: Boolean,
        val inMeasurement: SimpleBooleanProperty,
        val isLocked: Boolean
    )

    data class MeasurementRow(
        val id: Int,
        val type: String,
        val parents: String,
        val label: String,
        val measurement: String
    )

    data class ConstructionRow(val construction: LoadedConstruction)

    init {
        showHiddenCheckbox.selectedProperty().addListener { _, _, _ -> refresh() }

        setupObjectTable()
        setupMeasurementTable()
        setupConstructionTable()

        // Left pane: checkbox + objects table
        val (objectHeader, objBtn) = makeHeader("Object List") { onCloseObjectList?.invoke() }
        objectCloseBtn = objBtn
        leftPane = VBox(4.0, showHiddenCheckbox, objectHeader, objectTable)
        leftPane.styleClass.add("sidebar-left")
        leftPane.padding = Insets(8.0)
        VBox.setVgrow(objectTable, Priority.ALWAYS)

        // Right pane: measurements + constructions in vertical split
        val (constructionHeader, conBtn) = makeHeader("Custom Constructions") { onCloseConstructions?.invoke() }
        constructionCloseBtn = conBtn
        val (measurementHeader, measBtn) = makeHeader("Measurements") { onCloseMeasurements?.invoke() }
        measurementCloseBtn = measBtn

        constructionsBox = VBox(4.0, constructionHeader, constructionTable)
        VBox.setVgrow(constructionTable, Priority.ALWAYS)
        measurementsBox = VBox(4.0, measurementHeader, measurementTable)
        measurementsBox.padding = Insets(8.0, 0.0, 0.0, 0.0)
        VBox.setVgrow(measurementTable, Priority.ALWAYS)

        rightSplitPane = SplitPane()
        rightSplitPane.items.addAll(constructionsBox, measurementsBox)
        rightSplitPane.orientation = javafx.geometry.Orientation.VERTICAL
        rightSplitPane.setDividerPositions(0.5)
        VBox.setVgrow(rightSplitPane, Priority.ALWAYS)

        rightPane = VBox(4.0, rightSplitPane)
        rightPane.styleClass.add("sidebar-right")
        rightPane.padding = Insets(8.0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupObjectTable() {
        objectTable.isEditable = true
        objectTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        objectTable.selectionModel.selectionMode = SelectionMode.MULTIPLE

        objectTable.setRowFactory {
            val row = TableRow<ObjectRow>()
            row.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
                if (event.button == MouseButton.PRIMARY && !row.isEmpty) {
                    // Let clicks on buttons, checkboxes, and text fields pass through
                    var node = event.target as? javafx.scene.Node
                    while (node != null) {
                        if (node is javafx.scene.control.Button || node is javafx.scene.control.CheckBox || node is javafx.scene.control.TextField) return@addEventFilter
                        node = node.parent
                    }
                    // Let double-clicks pass through for inline editing
                    if (event.clickCount >= 2) return@addEventFilter
                    val sm = objectTable.selectionModel
                    if (sm.isSelected(row.index)) {
                        sm.clearSelection(row.index)
                    } else {
                        sm.select(row.index)
                    }
                    event.consume()
                }
            }
            row
        }

        val idCol = TableColumn<ObjectRow, String>("ID")
        idCol.setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }
        idCol.minWidth = 40.0
        idCol.maxWidth = 40.0
        idCol.style = "-fx-alignment: CENTER;"
        idCol.isEditable = false

        val typeCol = TableColumn<ObjectRow, String>("Type")
        typeCol.setCellValueFactory { SimpleStringProperty(it.value.type) }
        typeCol.minWidth = 120.0
        typeCol.maxWidth = 120.0
        typeCol.isEditable = false

        val parentCol = TableColumn<ObjectRow, String>("Parents")
        parentCol.setCellValueFactory { SimpleStringProperty(it.value.parents) }
        parentCol.prefWidth = 45.0
        parentCol.isEditable = false

        val labelCol = TableColumn<ObjectRow, String>("Label")
        labelCol.setCellValueFactory { it.value.label }
        labelCol.cellFactory = TextFieldTableCell.forTableColumn()
        labelCol.setOnEditCommit { event ->
            val row = event.rowValue
            val meta = manager.getMetadata(row.id) ?: return@setOnEditCommit
            meta.label = event.newValue.ifBlank { null }
            if (meta.label != null) meta.labelVisible = true
            row.label.set(meta.label ?: "")
            undoManager.checkpoint()
            requestRender()
            objectTable.selectionModel.clearSelection()
        }
        labelCol.prefWidth = 40.0
        labelCol.style = "-fx-alignment: CENTER;"

        val visCol = TableColumn<ObjectRow, Void>("Show")
        visCol.setCellFactory {
            object : TableCell<ObjectRow, Void>() {
                private val checkBox = CheckBox()
                private val lockText = Text("\uD83D\uDD12")

                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val row = tableView.items[index]
                    if (row.isLocked) {
                        graphic = lockText
                    } else {
                        checkBox.isSelected = row.visible.get()
                        checkBox.setOnAction {
                            row.visible.set(checkBox.isSelected)
                        }
                        graphic = checkBox
                    }
                    alignment = javafx.geometry.Pos.CENTER
                }
            }
        }
        visCol.minWidth = 55.0
        visCol.maxWidth = 55.0

        val deleteCol = TableColumn<ObjectRow, Void>("Del")
        deleteCol.setCellFactory {
            object : TableCell<ObjectRow, Void>() {
                private val btn = Button("X")
                private val lockText = Text("\uD83D\uDD12")
                init {
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        val row = tableView.items[index]
                        val alert = Alert(Alert.AlertType.CONFIRMATION, "Delete object ${row.id} and its descendants?")
                        alert.showAndWait().ifPresent { result ->
                            if (result == ButtonType.OK) {
                                manager.removeWithDescendants(row.id)
                                hitTester.rebuildIntersectionCache()
                                undoManager.checkpoint()
                                requestRender()
                                refresh()
                            }
                        }
                    }
                }
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= tableView.items.size) {
                        graphic = null
                        return
                    }
                    val row = tableView.items[index]
                    graphic = if (row.isLocked) lockText else btn
                    alignment = javafx.geometry.Pos.CENTER
                }
            }
        }
        deleteCol.minWidth = 40.0
        deleteCol.maxWidth = 40.0

        objectTable.columns.addAll(idCol, typeCol, parentCol, labelCol, visCol, deleteCol)

        objectTable.selectionModel.selectedItems.addListener(
            javafx.collections.ListChangeListener {
                if (updatingFromExternal) return@ListChangeListener
                val ids = objectTable.selectionModel.selectedItems.map { it.id }.toSet()
                externalSelectedIds.clear()
                externalSelectedIds.addAll(ids)
                onSelectionChanged(ids)
            }
        )
    }

    private fun setupMeasurementTable() {
        val idCol = TableColumn<MeasurementRow, String>("ID")
        idCol.setCellValueFactory { SimpleStringProperty(it.value.id.toString()) }
        idCol.minWidth = 40.0
        idCol.maxWidth = 40.0
        idCol.style = "-fx-alignment: CENTER;"

        val typeCol = TableColumn<MeasurementRow, String>("Type")
        typeCol.setCellValueFactory { SimpleStringProperty(it.value.type) }
        typeCol.minWidth = 120.0
        typeCol.maxWidth = 120.0

        val labelCol = TableColumn<MeasurementRow, String>("Label")
        labelCol.setCellValueFactory { SimpleStringProperty(it.value.label) }
        labelCol.prefWidth = 40.0
        labelCol.style = "-fx-alignment: CENTER;"

        val measureCol = TableColumn<MeasurementRow, String>("Value")
        measureCol.setCellValueFactory { SimpleStringProperty(it.value.measurement) }
        measureCol.prefWidth = 70.0

        val deleteCol = TableColumn<MeasurementRow, Void>("Del")
        deleteCol.setCellFactory {
            object : TableCell<MeasurementRow, Void>() {
                private val btn = Button("X")
                init {
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        val row = tableView.items[index]
                        val meta = manager.getMetadata(row.id) ?: return@setOnAction
                        meta.inMeasurementTable = false
                        undoManager.checkpoint()
                        refresh()
                    }
                }
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else btn
                    alignment = javafx.geometry.Pos.CENTER
                }
            }
        }
        deleteCol.minWidth = 40.0
        deleteCol.maxWidth = 40.0

        measurementTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        measurementTable.columns.addAll(idCol, typeCol, labelCol, measureCol, deleteCol)
    }

    private fun setupConstructionTable() {
        constructionTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        constructionTable.items = constructionItems

        val applyCol = TableColumn<ConstructionRow, Void>("Apply")
        applyCol.setCellFactory {
            object : TableCell<ConstructionRow, Void>() {
                private val btn = Button("Apply")
                init {
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        val row = tableView.items[index]
                        onApplyConstruction(row.construction)
                    }
                }
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else btn
                    alignment = javafx.geometry.Pos.CENTER
                }
            }
        }
        applyCol.minWidth = 60.0
        applyCol.maxWidth = 60.0

        val fileCol = TableColumn<ConstructionRow, String>("File")
        fileCol.setCellValueFactory { SimpleStringProperty(it.value.construction.fileName) }
        fileCol.prefWidth = 80.0

        val inputsCol = TableColumn<ConstructionRow, String>("Inputs")
        inputsCol.setCellValueFactory { SimpleStringProperty(it.value.construction.inputTypes.joinToString(", ")) }
        inputsCol.prefWidth = 80.0

        val removeCol = TableColumn<ConstructionRow, Void>("Del")
        removeCol.setCellFactory {
            object : TableCell<ConstructionRow, Void>() {
                private val btn = Button("X")
                init {
                    btn.styleClass.add("delete-button")
                    btn.setOnAction {
                        val row = tableView.items[index]
                        constructionItems.remove(row)
                    }
                }
                override fun updateItem(item: Void?, empty: Boolean) {
                    super.updateItem(item, empty)
                    graphic = if (empty) null else btn
                    alignment = javafx.geometry.Pos.CENTER
                }
            }
        }
        removeCol.minWidth = 40.0
        removeCol.maxWidth = 40.0

        constructionTable.columns.addAll(applyCol, fileCol, inputsCol, removeCol)
    }

    fun addConstruction(loaded: LoadedConstruction) {
        constructionItems.add(ConstructionRow(loaded))
    }

    fun clearAllConstructions() {
        constructionItems.clear()
    }

    fun refresh() {
        updatingFromExternal = true
        val showHidden = showHiddenCheckbox.isSelected

        // Object table
        val objectRows = manager.allObjects().mapNotNull { obj ->
            val meta = manager.getMetadata(obj.id) ?: return@mapNotNull null
            if (!showHidden && !meta.visible) return@mapNotNull null

            val type = getTypeName(obj)
            val parents = obj.parentIds.joinToString(",")
            val labelProp = SimpleStringProperty(meta.label ?: "")
            val visProp = SimpleBooleanProperty(meta.visible)
            visProp.addListener { _, _, newVal ->
                meta.visible = newVal
                if (!newVal) meta.labelVisible = false
                hitTester.rebuildIntersectionCache()
                undoManager.checkpoint()
                requestRender()
            }
            val measurable = obj is Segment || obj is Circle || obj is Angle || obj is Region
            val inMeasProp = SimpleBooleanProperty(meta.inMeasurementTable)
            inMeasProp.addListener { _, _, newVal ->
                meta.inMeasurementTable = newVal
                undoManager.checkpoint()
                refresh()
            }

            ObjectRow(obj.id, type, parents, labelProp, visProp, measurable, inMeasProp, meta.isLocked)
        }
        objectTable.selectionModel.clearSelection()
        objectTable.items = FXCollections.observableArrayList(objectRows)

        // Measurement table
        val measurementRows = manager.allObjects().mapNotNull { obj ->
            val meta = manager.getMetadata(obj.id) ?: return@mapNotNull null
            if (!meta.inMeasurementTable) return@mapNotNull null
            val measurement = MeasurementUtils.measureLabel(obj) ?: return@mapNotNull null
            MeasurementRow(
                obj.id,
                getTypeName(obj),
                obj.parentIds.joinToString(","),
                meta.label ?: "",
                measurement
            )
        }
        measurementTable.items = FXCollections.observableArrayList(measurementRows)

        // Sync selection (clearSelection was done before items replacement above)
        for (row in objectTable.items) {
            if (row.id in externalSelectedIds) {
                objectTable.selectionModel.select(row)
            }
        }
        updatingFromExternal = false
    }

    fun setSelectedIds(ids: Set<Int>) {
        externalSelectedIds.clear()
        externalSelectedIds.addAll(ids)
        updatingFromExternal = true
        objectTable.selectionModel.clearSelection()
        for (row in objectTable.items) {
            if (row.id in ids) {
                objectTable.selectionModel.select(row)
            }
        }
        updatingFromExternal = false
    }

    private fun makeHeader(title: String, onClose: () -> Unit): Pair<HBox, Button> {
        val label = Label(title)
        label.styleClass.add("sidebar-header")
        val spacer = javafx.scene.layout.Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)
        val closeBtn = Button("X")
        closeBtn.styleClass.add("delete-button")
        closeBtn.setOnAction { onClose() }
        return Pair(HBox(4.0, label, spacer, closeBtn), closeBtn)
    }

    fun setCloseButtonsDisabled(disabled: Boolean) {
        objectCloseBtn.isDisable = disabled
        constructionCloseBtn.isDisable = disabled
        measurementCloseBtn.isDisable = disabled
    }

    val isRightPaneEmpty: Boolean
        get() = rightSplitPane.items.isEmpty()

    fun setConstructionsVisible(visible: Boolean) {
        if (visible) {
            if (constructionsBox !in rightSplitPane.items) {
                rightSplitPane.items.add(0, constructionsBox)
            }
        } else {
            rightSplitPane.items.remove(constructionsBox)
        }
        if (rightSplitPane.items.size == 2) rightSplitPane.setDividerPositions(0.5)
    }

    fun setMeasurementsVisible(visible: Boolean) {
        if (visible) {
            if (measurementsBox !in rightSplitPane.items) {
                rightSplitPane.items.add(measurementsBox)
            }
        } else {
            rightSplitPane.items.remove(measurementsBox)
        }
        if (rightSplitPane.items.size == 2) rightSplitPane.setDividerPositions(0.5)
    }

    private fun getTypeName(obj: GeometricObject): String = when (obj) {
        is FreePoint -> "Free Point"
        is FreePointOnLine -> "Point on Line"
        is FreePointOnRay -> "Point on Ray"
        is FreePointOnSegment -> "Point on Segment"
        is FreePointOnCircle -> "Point on Circle"
        is LinearLinearIntersection, is LinearCircleIntersection,
        is CircleCircleIntersection -> "Intersection Point"
        is Midpoint -> "Midpoint"
        is ReflectionPoint -> "Reflection Point"
        is Line -> "Line"
        is Ray -> "Ray"
        is Segment -> "Segment"
        is ParallelLine -> "Parallel Line"
        is PerpendicularLine -> "Perpendicular Line"
        is Circle -> "Circle"
        is Angle -> "Angle"
        is Region -> "Region"
    }
}
