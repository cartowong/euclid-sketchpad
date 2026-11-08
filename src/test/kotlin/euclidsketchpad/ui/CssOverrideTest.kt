package euclidsketchpad.ui

import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

/**
 * Tests whether adding "selected" style class to a JavaFX Shape
 * causes the default stylesheet (modena.css) to override programmatic fill/stroke.
 */
class CssOverrideTest {

    companion object {
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
    }

    @Test
    fun `adding selected style class should not override programmatic fill`() {
        val circle = Circle(0.0, 0.0, 5.0)
        circle.fill = Color.web("#2196F3")
        circle.styleClass.add("point")
        circle.styleClass.add("selected")

        // Put in a scene so CSS gets applied
        val root = Pane(circle)
        val scene = Scene(root, 100.0, 100.0)
        root.applyCss()

        println("[CSS-TEST] Circle with 'selected' class: fill = ${circle.fill}")
        assertEquals(Color.web("#2196F3"), circle.fill,
            "Adding 'selected' style class should not override programmatic blue fill. " +
            "Actual fill: ${circle.fill}")
    }

    @Test
    fun `circle without selected class keeps programmatic fill`() {
        val circle = Circle(0.0, 0.0, 5.0)
        circle.fill = Color.web("#2196F3")
        circle.styleClass.add("point")

        val root = Pane(circle)
        val scene = Scene(root, 100.0, 100.0)
        root.applyCss()

        println("[CSS-TEST] Circle without 'selected' class: fill = ${circle.fill}")
        assertEquals(Color.web("#2196F3"), circle.fill,
            "Circle without 'selected' class should keep programmatic blue fill. " +
            "Actual fill: ${circle.fill}")
    }

    @Test
    fun `compare fill with and without selected class`() {
        val withSelected = Circle(0.0, 0.0, 5.0)
        withSelected.fill = Color.web("#2196F3")
        withSelected.styleClass.add("point")
        withSelected.styleClass.add("selected")

        val withoutSelected = Circle(10.0, 0.0, 5.0)
        withoutSelected.fill = Color.web("#2196F3")
        withoutSelected.styleClass.add("point")

        val root = Pane(withSelected, withoutSelected)
        val scene = Scene(root, 100.0, 100.0)
        root.applyCss()

        println("[CSS-TEST] With 'selected': fill = ${withSelected.fill}")
        println("[CSS-TEST] Without 'selected': fill = ${withoutSelected.fill}")
        assertEquals(withoutSelected.fill, withSelected.fill,
            "Both circles should have the same fill regardless of 'selected' class")
    }
}
