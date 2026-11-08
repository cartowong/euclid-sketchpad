package euclidsketchpad.ui

import javafx.geometry.Insets
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox

enum class ToolType { SELECT, POINT, SEGMENT, RAY, LINE, CIRCLE, ANGLE, REGION, LABEL }

class Toolbar(
    private val onToolSelected: (ToolType) -> Unit
) : HBox() {

    private val toggleGroup = ToggleGroup()

    private val iconNames = mapOf(
        ToolType.SELECT to "select",
        ToolType.POINT to "point",
        ToolType.SEGMENT to "segment",
        ToolType.RAY to "ray",
        ToolType.LINE to "line",
        ToolType.CIRCLE to "circle",
        ToolType.ANGLE to "angle",
        ToolType.REGION to "region",
        ToolType.LABEL to "label"
    )

    companion object {
        private const val BASE_STYLE = "-fx-background-color: #E0E0E0; -fx-padding: 4; -fx-background-radius: 4; -fx-border-radius: 4;"
        private const val ACTIVE_STYLE = BASE_STYLE + " -fx-border-color: #333333; -fx-border-width: 2.5;"
    }

    init {
        styleClass.add("toolbar")
        spacing = 4.0
        padding = Insets(4.0, 8.0, 4.0, 8.0)

        val buttons = ToolType.entries.map { makeButton(it) }
        children.addAll(buttons)

        toggleGroup.selectedToggleProperty().addListener { _, oldToggle, newToggle ->
            (oldToggle as? ToggleButton)?.style = BASE_STYLE
            (newToggle as? ToggleButton)?.style = ACTIVE_STYLE
        }

        toggleGroup.selectToggle(buttons.first())
    }

    private fun makeButton(toolType: ToolType): ToggleButton {
        val iconName = iconNames[toolType] ?: toolType.name.lowercase()
        val iconUrl = javaClass.getResource("/icons/$iconName.png")
        val btn = ToggleButton()
        if (iconUrl != null) {
            val image = Image(iconUrl.toExternalForm(), 24.0, 24.0, true, true)
            btn.graphic = ImageView(image)
        } else {
            btn.text = toolType.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        btn.tooltip = Tooltip(toolType.name.lowercase().replaceFirstChar { it.uppercase() })
        btn.toggleGroup = toggleGroup
        btn.styleClass.add("tool-button")
        btn.style = BASE_STYLE
        btn.setPrefSize(32.0, 32.0)
        btn.setOnAction {
            if (btn.isSelected) {
                onToolSelected(toolType)
            } else {
                btn.isSelected = true
            }
        }
        return btn
    }
}
