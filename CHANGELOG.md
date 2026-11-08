# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.5.7] - 2026-11-07

### Fixed

- ESC key now cancels construction apply mode when pressed immediately after clicking Apply (canvas requests focus on entering apply mode)
- Removed Escape accelerator from "Cancel Recording" menu item to prevent it from intercepting ESC when higher-priority handlers (apply mode, tool cancel) should take precedence

## [1.5.6] - 2026-11-07

### Changed

- Decreased angle arc radius from 30px to 25px (screen pixels, counter-scaled)
- Updated application screenshot

## [1.5.5] - 2026-11-02

### Added

- `appName` field (`"EuclideanSketchpad"`) in the JSON serialization format for diagrams and constructions, validated on load before version checking
- `APP_NAME` constant in `Main.kt` as the single source of truth for the app name
- GitHub Pages site in `docs/` with landing page, privacy policy, and support page for App Store submission

## [1.5.4] - 2026-10-31

### Changed

- Increased angle arc radius from 20px to 30px (screen pixels, counter-scaled)

### Fixed

- AngleTool preview arc now uses the shared `ANGLE_ARC_RADIUS` constant instead of a private hardcoded value, so the preview matches the rendered angle

## [1.5.3] - 2026-10-30

### Changed

- License changed from MIT to GNU General Public License v3.0
- About dialog now shows GPL v3.0 license and includes a "Third-Party Licenses" link that displays notices for AtlantaFX, OpenJFX, and kotlinx.serialization
- Updated THIRD_PARTY_NOTICES with OpenJFX (GPL 2.0 + Classpath Exception) and kotlinx.serialization (Apache 2.0)

## [1.5.2] - 2026-10-25

### Changed

- "Select Children" (⌘⇧C) now selects objects whose parent list contains **all** currently selected objects, instead of any one of them
- "Select Parents" (⌘⇧P) now selects the **common parents** (intersection) of all currently selected objects, instead of the union of all parent lists

## [1.5.1] - 2026-10-24

### Added

- Headless diagram export via `--export` CLI flag — produces 6 files (canvas/custom/autofit × PNG/SVG) without opening the GUI
- `export-diagram` Claude Code skill for quick export verification

### Fixed

- SVG angle export now renders arcs in the correct direction — `startAngle` was not negated to account for the y-flip in pixel coordinates

## [1.5.0] - 2026-10-24

### Added

- Dragging with all objects selected now pans the viewport instead of moving objects, matching the ergonomic expectation that Select All + drag should scroll the canvas

### Fixed

- Tangent intersection points (linear-circle and circle-circle) no longer disappear due to floating-point rounding — relaxed the intersection discriminant tolerance from `1e-10` to `1e-5`

## [1.4.1] - 2026-10-23

### Fixed

- Exported labels (PNG and SVG) now match their canvas positions — previously labels appeared ~8px closer to their parent point in exports due to a missing baseline shift that the canvas renderer applies via `scaleY = -1`

### Changed

- Table visibility toggles (View menu items and header close buttons) are disabled while the Export dialog is open, preventing layout changes during export

## [1.4.0] - 2026-10-23

### Added

- Angles are now hit-testable on the canvas: hover highlighting, selection with the Select tool, label assignment with the Label tool, and hiding with click all work on angle arcs
- New `HitResult.OnAngle` variant and tier 2 in the hit-tester (between points and intersection previews), using zoom-aware arc radius matching the visual arc size
- `Angle.containsPoint(x, y, arcRadiusWorld)` method for checking if a point falls inside the arc sector
- View menu items to show/hide the Object List, Custom Constructions, and Measurements tables. Hiding all tables in a pane removes the pane from the layout; showing restores it
- Close buttons on each table header (Object List, Custom Constructions, Measurements) that hide the corresponding table, equivalent to the View menu toggle

### Changed

- Renamed the left pane header from "Objects" to "Object List"
- Widened the Measurements table Type column from 62px to 120px to match the Object List table
- Updated application screenshot

## [1.3.0] - 2026-10-20

### Added

- AtlantaFX Primer Light theme applied as the global user agent stylesheet, replacing the default Modena look

### Changed

- Widened fixed-width table columns to accommodate theme padding: Objects table Show (35→55px) and Del (30→40px), Custom Constructions table Apply (45→60px) and Del (30→40px), Measurements table Del (30→40px)
- Widened sidebar panes: initial divider positions changed from 25%/75% to 30%/70%
- Updated application screenshot

## [1.2.1] - 2026-10-20

### Changed

- Extracted `VIEWPORT_MARGIN_FRACTION` constant in `GeometryUtils` as the single source of truth for the viewport clip margin (0.02). `computeClipRect` default parameter and `ExportRenderer.computeAutoFitBounds` margin both reference this constant
- Increased auto-fit export margin from 2× to 4× the viewport margin fraction for more comfortable padding around data points in exported images
- Deploy-app skill now runs `./gradlew clean test` before committing; release is aborted if any test fails
- Deploy-app skill rejects same-version releases when the git tag already exists
- Updated application screenshot

## [1.2.0] - 2026-10-19

### Added

- Viewport clipping for circles: circles are visually clipped to the viewport boundary (2% margin inset), rendering only the arc segments inside the visible area. Applies to canvas rendering, circle tool preview, construction preview, and both export paths (PNG and SVG)
- Point clamping: free points created by direct click or auto-constructed in multi-click tools are clamped to the viewport clip rect. Free points being dragged are also clamped. Derived points (intersections, semi-free points, construction results) are not clamped
- New `clampToRect` and `clipCircleToArcs` utility functions in `GeometryUtils`
- 12 new unit tests for `clampToRect` and `clipCircleToArcs`

### Changed

- Viewport clipping margin reduced from 5% to 2% inset for all clipped objects (lines, rays, circles, points)
- Auto-fit export margin reduced from 10% to 4% to match the 2% clip inset (2% data-to-clip gap, 2% clip-to-edge gap)
- All tools (`PointTool`, `SelectionTool`, `CircleTool`, `LineTool`, `AngleTool`, `RegionTool`) now receive a shared `viewportProvider` lambda for consistent viewport-aware behavior

## [1.1.1] - 2026-10-18

### Fixed

- Label positions in exported images (PNG and SVG) now match the canvas — previously labels were vertically mirrored due to incorrect `labelOffsetY` sign in the export coordinate transform

## [1.1.0] - 2026-10-18

### Added

- Arrowheads rendered at the visible canvas edge for lines and rays, clipped to the viewport boundary with a 5% margin inset using Liang-Barsky line clipping
- Tool preview (ghost) lines and rays also clip and show arrowheads matching the committed object positions

### Fixed

- Auto-fit export ray clipping: rays now extend to the clip boundary with a visible 5% margin from the image edge (previously arrowheads were either clipped too aggressively or flush with the edge)

### Changed

- Extracted shared rendering constants (`POINT_RADIUS`, `STROKE_WIDTH`, `LINE_EXTENT`, `ARROWHEAD_SIZE`, `ANGLE_ARC_RADIUS`, `FONT_SIZE`), `computeClipRect`, `computeArrowheadVertices`, and `computeAdaptiveGridSpacing` into `GeometryUtils`, removing duplicates from `GeometryRenderer`, `LineTool`, `CanvasPane`, and `ExportRenderer`
- Extracted `getObjectAnchor` into the `model` package as a shared top-level function, removing duplicates from `GeometryRenderer` and `ExportRenderer`
- Increased auto-fit export margin from 5% to 10% to accommodate the 5% clip inset without clipping data-edge points

## [1.0.0] - 2026-10-17

### Added

- Compass-and-straightedge construction tools: Point, Line, Ray, Segment, Circle, Angle, Region
- Selection tool with drag support for free and semi-free points
- Label tool with inline editing and drag repositioning
- Intersection detection and preview for linear-linear, linear-circle, and circle-circle pairs
- Measurement table for segment length, angle, circle circumference, and region area
- Construction recording, saving, and loading system
- Built-in constructions: Midpoint, Parallel Line, Perpendicular Line, Reflection Point
- Construction apply mode with type-filtered hit-testing and hover preview
- Snapshot-based undo/redo
- Diagram save/load (JSON format)
- Export to PNG and SVG (Canvas, Custom bounding box, and Auto-fit methods)
- Adaptive grid with toggle
- Pan (Space+drag) and scroll-wheel zoom
