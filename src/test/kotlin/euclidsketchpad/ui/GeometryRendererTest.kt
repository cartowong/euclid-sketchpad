package euclidsketchpad.ui

import euclidsketchpad.geometry.FreePoint
import euclidsketchpad.geometry.Segment
import euclidsketchpad.model.GeometricObjectManager
import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Circle as FxCircle
import javafx.scene.shape.Line as FxLine
import javafx.scene.transform.Affine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

class GeometryRendererTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initJfx() {
            val latch = CountDownLatch(1)
            try {
                Platform.startup { latch.countDown() }
            } catch (e: IllegalStateException) {
                // Toolkit already initialized
                latch.countDown()
            }
            latch.await()
        }
    }

    private fun createManagerWithPointAndSegment(): GeometricObjectManager {
        val manager = GeometricObjectManager()
        manager.addObject { id -> FreePoint(id, 10.0, 20.0) }  // id 0
        manager.addObject { id -> FreePoint(id, 50.0, 60.0) }  // id 1
        manager.addObject { id -> Segment(id, 0, 1) }          // id 2
        return manager
    }

    private fun renderAndCollect(
        manager: GeometricObjectManager,
        selectedIds: Set<Int> = emptySet(),
        hoveredId: Int? = null
    ): Group {
        val group = Group()
        val transform = Affine()
        transform.appendScale(1.0, -1.0)
        GeometryRenderer.renderAll(manager, group, transform, selectedIds, hoveredId)
        return group
    }

    /**
     * Renders objects into a Scene with the real geometry.css stylesheet applied,
     * forces a CSS pass, then returns the group for inspection.
     * This tests what the user actually sees, not just the programmatic values.
     */
    private fun renderWithCss(
        manager: GeometricObjectManager,
        selectedIds: Set<Int> = emptySet(),
        hoveredId: Int? = null
    ): Group {
        val group = Group()
        val transform = Affine()
        transform.appendScale(1.0, -1.0)
        GeometryRenderer.renderAll(manager, group, transform, selectedIds, hoveredId)

        // Embed in a real Scene with the actual stylesheet
        val root = Pane(group)
        val scene = Scene(root, 400.0, 400.0)
        val cssUrl = javaClass.getResource("/styles/geometry.css")?.toExternalForm()
        if (cssUrl != null) {
            scene.stylesheets.add(cssUrl)
        }
        // Force CSS to be applied
        root.applyCss()

        return group
    }

    // ---- Tests WITHOUT CSS (programmatic values only) ----

    @Test
    fun `normal point is black`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager)

        val pointNodes = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        assertTrue(pointNodes.isNotEmpty(), "Should render points")
        for (pt in pointNodes) {
            assertEquals(Color.BLACK, pt.fill, "Normal point fill should be black")
        }
    }

    @Test
    fun `selected point is blue`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, selectedIds = setOf(0))

        val selectedPoint = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
            .first { it.centerX == 10.0 && it.centerY == 20.0 }
        assertEquals(Color.web("#2196F3"), selectedPoint.fill, "Selected point fill should be blue")
    }

    @Test
    fun `hovered point is blue`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, hoveredId = 0)

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        val hoveredPoint = points.first { (it.centerX == 10.0 && it.centerY == 20.0) }
        assertEquals(Color.web("#2196F3"), hoveredPoint.fill,
            "Hovered point fill should be blue (#2196F3)")
    }

    @Test
    fun `hovered point is larger than normal`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, hoveredId = 0)

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        val hoveredPoint = points.first { (it.centerX == 10.0 && it.centerY == 20.0) }
        val normalPoint = points.first { (it.centerX == 50.0 && it.centerY == 60.0) }
        assertTrue(hoveredPoint.radius > normalPoint.radius,
            "Hovered point should be larger than normal point")
    }

    @Test
    fun `non-hovered point stays black`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, hoveredId = 0)

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        val normalPoint = points.first { (it.centerX == 50.0 && it.centerY == 60.0) }
        assertEquals(Color.BLACK, normalPoint.fill,
            "Non-hovered point should remain black")
    }

    @Test
    fun `normal segment stroke is black`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager)

        val segments = group.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }
        assertTrue(segments.isNotEmpty(), "Should render segments")
        assertEquals(Color.BLACK, segments[0].stroke, "Normal segment stroke should be black")
    }

    @Test
    fun `selected segment stroke is blue`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, selectedIds = setOf(2))

        val segments = group.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }
        assertEquals(Color.web("#2196F3"), segments[0].stroke,
            "Selected segment stroke should be blue")
    }

    @Test
    fun `hovered segment stroke is blue`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderAndCollect(manager, hoveredId = 2)

        val segments = group.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }
        assertEquals(Color.web("#2196F3"), segments[0].stroke,
            "Hovered segment stroke should be blue (#2196F3)")
    }

    @Test
    fun `hovered segment is thicker than normal`() {
        val manager = createManagerWithPointAndSegment()

        val normalGroup = renderAndCollect(manager)
        val normalWidth = normalGroup.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }[0].strokeWidth

        val hoveredGroup = renderAndCollect(manager, hoveredId = 2)
        val hoveredWidth = hoveredGroup.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }[0].strokeWidth

        assertTrue(hoveredWidth > normalWidth,
            "Hovered segment should be thicker than normal")
    }

    @Test
    fun `hover and selection use same blue color`() {
        val manager = createManagerWithPointAndSegment()

        val selectedGroup = renderAndCollect(manager, selectedIds = setOf(0))
        val selectedPoint = selectedGroup.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
            .first { it.centerX == 10.0 && it.centerY == 20.0 }

        val hoveredGroup = renderAndCollect(manager, hoveredId = 0)
        val hoveredPoint = hoveredGroup.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
            .first { (it.centerX == 10.0 && it.centerY == 20.0) }

        assertEquals(selectedPoint.fill, hoveredPoint.fill,
            "Hover and selection should use the same blue color")
    }

    // ---- Tests WITH CSS (verify stylesheet doesn't override hover colors) ----

    @Test
    fun `with CSS - hovered point fill is blue after stylesheet applied`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderWithCss(manager, hoveredId = 0)

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        val hoveredPoint = points.first { (it.centerX == 10.0 && it.centerY == 20.0) }
        val fill = hoveredPoint.fill
        assertEquals(Color.web("#2196F3"), fill,
            "After CSS applied, hovered point fill should still be blue. Actual: $fill")
    }

    @Test
    fun `with CSS - normal point fill is black after stylesheet applied`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderWithCss(manager)

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
        val point = points[0]
        val fill = point.fill
        assertEquals(Color.BLACK, fill,
            "After CSS applied, normal point fill should be black. Actual: $fill")
    }

    @Test
    fun `with CSS - hovered segment stroke is blue after stylesheet applied`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderWithCss(manager, hoveredId = 2)

        val segments = group.children.filterIsInstance<FxLine>()
            .filter { it.styleClass.contains("segment") }
        val stroke = segments[0].stroke
        assertEquals(Color.web("#2196F3"), stroke,
            "After CSS applied, hovered segment stroke should be blue. Actual: $stroke")
    }

    @Test
    fun `with CSS - selected point fill is blue after stylesheet applied`() {
        val manager = createManagerWithPointAndSegment()
        val group = renderWithCss(manager, selectedIds = setOf(0))

        val points = group.children.filterIsInstance<FxCircle>()
            .filter { it.styleClass.contains("point") }
            .filter { it.centerX == 10.0 && it.centerY == 20.0 }
        val fill = points[0].fill
        assertEquals(Color.web("#2196F3"), fill,
            "After CSS applied, selected point fill should be blue. Actual: $fill")
    }
}
