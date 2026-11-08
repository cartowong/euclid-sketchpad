# Euclid Sketchpad — Application Specification

A desktop application for creating geometric constructions using compass and straightedge. Geometry engine details are in [GEOMETRY.md](GEOMETRY.md).

---

## 1. Technology Stack

| Decision | Choice |
|----------|--------|
| Language | Kotlin |
| UI framework | JavaFX (OpenJFX) |
| Rendering | JavaFX scene graph (Shape nodes in a transformed Group) |
| Build tool | Gradle (Kotlin DSL) |
| Testing | JUnit 5 (geometry library unit tests only) |
| Serialization | kotlinx.serialization (JSON) |
| Distribution | `jpackage` for native installer; also runnable via `./gradlew run` |
| UI theme | AtlantaFX Primer Light (user agent stylesheet) |
| Input | Desktop only (mouse), no touch support |

---

## 2. Architecture

### 2.1 Package Structure

```
src/main/kotlin/euclidsketchpad/
  geometry/         # Pure geometry library (no JavaFX imports) — see GEOMETRY.md
  model/            # GeometricObjectManager, undo/redo, construction recording/applying
  persistence/      # JSON serialization/deserialization for diagrams and constructions
  ui/               # JavaFX views, canvas, tools, renderer, sidebar, toolbar
  export/           # PNG and SVG export (dialog + renderer)
  Main.kt           # Application entry point; dispatches --export to HeadlessExportApp

src/main/resources/
  styles/geometry.css   # JavaFX CSS stylesheet
  icons/                # Tool button icons (24x24 PNG) and app icon
```

### 2.2 Key Classes

| Class | Package | Responsibility |
|-------|---------|---------------|
| `GeometricObjectManager` | model | Owns the object map (`LinkedHashMap<Int, GeometricObject>`) and metadata map. Provides `addObject`, `removeWithDescendants`, `movePoint`, `propagateFrom`, `snapshot`/`restore`, `nextDefaultLabel`. |
| `ObjectMetadata` | model | Per-object mutable state: `visible`, `label`, `labelVisible`, `labelOffsetX/Y`, `inMeasurementTable`, `labelAnchorAngle`, `labelAnchorT`, `isInput`, `isLocked`. |
| `UndoManager` | model | Snapshot-based undo/redo. Stores a list of `ManagerState` snapshots. Tracks saved index for unsaved-changes detection. Supports `minIndex` clamping for recording mode. |
| `ConstructionRecorder` | model | Manages recording lifecycle: `startRecording`, `cancelRecording`, `endRecording`. Sets `isInput`/`isLocked` flags. |
| `ConstructionApplicator` | model | Applies a loaded construction to the current diagram. Handles ID remapping, type matching, and construction preview computation. |
| `CanvasPane` | ui | JavaFX `Pane` subclass. Owns the world group, grid group, object group, preview group, construction preview group. Handles pan/zoom, mouse dispatch, apply mode, and rendering. |
| `GeometryRenderer` | ui | Renders all geometric objects into a JavaFX Group in 5 passes: regions, angles, linears/circles, points, labels. |
| `HitTester` | ui | Intersection cache + tiered hit-testing. Converts screen coordinates to world coordinates. |
| `Sidebar` | ui | Left pane (objects table) and right pane (constructions + measurements tables). |
| `DiagramSerializer` | persistence | Serializes/deserializes the full diagram to/from JSON. |
| `ConstructionSerializer` | persistence | Saves constructions (with labels nulled) and loads construction files into `LoadedConstruction` records. |
| `ExportRenderer` | export | Renders to PNG (via JavaFX snapshot) or SVG (manual string generation). |
| `HeadlessExportApp` | export | CLI-driven headless export. Loads a diagram JSON and exports 6 files (canvas/custom/autofit × PNG/SVG). |

### 2.3 Coordinate System

- **Math coordinates**: origin at center of canvas, y-axis pointing up.
- **Viewport transform**: a JavaFX `Affine` on the world group. Initial state: translate to canvas center, scale y by -1 (flip).
- **Initial scale**: 1:1 (1 pixel = 1 math unit).
- **Zoom**: scroll wheel. Zooms around the mouse position. Factor: 1.1x per scroll tick.
- **Pan**: Space + drag. Translates in world coordinates (adjusted for scale).
- Snap/hit-testing thresholds are specified in **screen pixels** and converted to math coordinates via the current zoom level on each check.

### 2.4 Dependency Graph

- Every geometric object has an **immutable integer ID** starting at 0, assigned by `GeometricObjectManager.addObject()` via an incrementing counter.
- Each object stores a list of parent IDs. A parent's ID is always lower than its child's.
- **Update propagation**: `propagateFrom(startId)` iterates objects by ID from `startId` upward, calling `recompute()` on each. The ID ordering is a valid topological sort by construction.
- **Undefined cascading**: when an object's `recompute()` finds an undefined parent, it sets `isDefined = false`. All descendants will also become undefined via their own `recompute()` calls.

---

## 3. Tools

### 3.1 Tool Interface

All tools implement the `Tool` interface:

```
interface Tool {
    activate()
    deactivate(previewGroup)
    onMousePressed(event, worldX, worldY)
    onMouseMoved(event, worldX, worldY)
    onMouseDragged(event, worldX, worldY)
    onMouseReleased(event, worldX, worldY)
    onEscapePressed(previewGroup) -> Boolean
}
```

All methods have default no-op implementations. `onEscapePressed` returns `true` if an in-progress operation was cancelled.

### 3.2 Toolbar

- **9 tool buttons** in a horizontal `HBox`, left-aligned, above the canvas: Select, Point, Segment, Ray, Line, Circle, Angle, Region, Label.
- **Radio behavior** via a `ToggleGroup`: one tool active at a time.
- Active tool has a thick dark border (`2.5px #333`). Inactive buttons have a gray background (`#E0E0E0`).
- Each button is 32x32px with a 24x24px icon loaded from `/icons/<name>.png`. Falls back to text label if icon is missing.
- Switching tools clears the current selection.

### 3.3 Point Tool

- **Click on empty canvas**: creates a `FreePoint` at the click position.
- **Click on intersection preview**: constructs the appropriate intersection point (`LinearLinearIntersection`, `LinearCircleIntersection`, or `CircleCircleIntersection`).
- **Click on a linear object**: creates a semi-free point (`FreePointOnLine`, `FreePointOnRay`, or `FreePointOnSegment`) constrained to that object, with `t` computed from the click position.
- **Click on a circle**: creates a `FreePointOnCircle` constrained to that circle, with `theta` computed from the click position.
- **Click on an existing point**: no-op.
- **Mouse move**: when hovering near a cached intersection, shows a ghost point at preview opacity.

### 3.4 Selection Tool

- **Click an object**: toggle selection. No modifier key needed for multi-select.
- **Selected objects** are rendered in blue (stroke and fill).
- **Dragging when all objects are selected**: if every object in the manager is selected, dragging pans the viewport (same transform math as Space+drag) instead of moving objects. No undo checkpoint is created. Objects are not deselected on release.
- **Dragging (subset selection)**: works only when **all** selected objects pass `manager.isMovable()` (free or semi-free points). The tool:
  1. On mousedown, takes a snapshot.
  2. On drag, applies the displacement to each selected point via `manager.movePoint()`.
  3. On mouseup, creates an undo checkpoint. The entire drag is a single undo entry.
- If any selected object is not movable (and it's not an all-selected pan), dragging does nothing.

### 3.5 Line / Ray / Segment Tools

A single `LineTool` class with a `subMode: LineSubMode` enum (`LINE`, `RAY`, `SEGMENT`). Three toolbar buttons each set the sub-mode.

- **First click**: resolves or creates the first point (snapping to existing points or intersections). Takes a pre-construction snapshot.
- **Second click**: resolves or creates the second point. Creates the linear object (`Line`, `Ray`, or `Segment`). Creates an undo checkpoint.
- **Same point**: if second click resolves to the same point as the first, the click is ignored.
- **Preview**: after first click, mouse move renders a ghost line/ray/segment from the first point to the cursor position, plus ghost endpoints.
- **Escape**: restores the pre-construction snapshot, cancelling any auto-constructed first point.

### 3.6 Circle Tool

Same two-click pattern as the Line Tool:
- First click: center point.
- Second click: radius point. Creates a `Circle`.

### 3.7 Angle Tool

Three-click pattern:
- Click point A, then vertex B, then point C.
- Creates an `Angle(A, B, C)`.
- Same auto-construction and preview behavior as the Line Tool.

### 3.8 Region Tool

Multi-click pattern:
- Each click adds a vertex point (auto-constructed if needed).
- **Closing**: when hovering within snap threshold of the first point, clicking closes the polygon, creating a `Region` with the collected point IDs.
- **Escape**: restores to pre-construction snapshot, cancelling all auto-constructed points.

### 3.9 Label Tool

- **Click an unlabeled object**: assigns the next default label (A, B, C, ..., AA, AB, ...) and makes it visible.
- **Click a visible-label object**: hides the label (keeps the label text, sets `labelVisible = false`).
- **Click a hidden-label object**: re-shows the previous label.
- **Click directly on a label**: enters inline editing mode (text field).
- **Drag a label**: repositions it. The offset (`labelOffsetX`, `labelOffsetY`) is stored in screen pixels relative to the object's anchor point.
- Label anchor points:
  - Points: `(x, y)`.
  - Linears: interpolated at `t` along `(x1,y1)-(x2,y2)`. Default `t = 0.5` for lines/segments, `t = 0.0` for rays.
  - Circles: point on circumference at `labelAnchorAngle` (default `PI/2`).
  - Angles: vertex position.
  - Regions: centroid of vertices.

---

## 4. UI Layout

### 4.1 Overall Layout

Three-column layout. Initial window size: **1440x720**. Initial divider positions: **30% / 70%** (left edge at 30%, right edge at 70%).

```
+------------------+-------------------------------------------+------------------+
|   Left pane      |           Center column                   |   Right pane     |
|                  |  +-------------------------------------+  |                  |
| [x] Show hidden  |  | Toolbar: [S][P][/][/][/][O][<][△][A]|  | Constructions   |
|                  |  +-------------------------------------+  | table (top)     |
| Objects table    |  |                                     |  |                  |
| (scrollable)     |  |              Canvas                  |  +-----------+     |
|                  |  |                                     |  | Measurements|    |
|                  |  |                                     |  | table       |    |
|                  |  |                                     |  | (bottom)    |    |
+------------------+-------------------------------------------+------------------+
```

- **Left pane**: "Show hidden objects" checkbox + Object List table.
- **Center column**: `VBox` containing the toolbar and canvas. Not a `SplitPane` child — it sits between the two `SplitPane` children.
- **Right pane**: vertical `SplitPane` with Custom Constructions table (top) and Measurements table (bottom). Divider at 50%.
- **Table headers**: each table ("Object List", "Custom Constructions", "Measurements") has a header row with the title left-aligned and a close button ("X") right-aligned. Clicking the close button hides that table, equivalent to the corresponding View menu toggle.
- **Pane visibility**: toggled via View menu or header close buttons. When all tables in a pane are hidden, the pane is removed from the outer `SplitPane`. When a table is shown again, its pane is re-added with default divider positions. Not persisted across sessions. Both the View menu toggles and the header close buttons are disabled while the Export dialog is open.

### 4.2 Object List Table

| Column | Width | Description |
|--------|-------|-------------|
| ID | 40px fixed | Object ID |
| Type | 120px fixed | Display type name |
| Parents | flex | Comma-separated parent IDs |
| Label | flex | Editable inline (double-click); commit triggers undo checkpoint |
| Show | 55px fixed | Checkbox to toggle visibility. Lock icon during recording. |
| Del | 40px fixed | "X" button to delete (with confirmation dialog). Lock icon during recording. |

**Type display names** (exhaustive — matches `Sidebar.getTypeName()`):

| Class | Display Name |
|-------|-------------|
| `FreePoint` | Free Point |
| `FreePointOnLine` | Point on Line |
| `FreePointOnRay` | Point on Ray |
| `FreePointOnSegment` | Point on Segment |
| `FreePointOnCircle` | Point on Circle |
| `LinearLinearIntersection` | Intersection Point |
| `LinearCircleIntersection` | Intersection Point |
| `CircleCircleIntersection` | Intersection Point |
| `Midpoint` | Midpoint |
| `ReflectionPoint` | Reflection Point |
| `Line` | Line |
| `Ray` | Ray |
| `Segment` | Segment |
| `ParallelLine` | Parallel Line |
| `PerpendicularLine` | Perpendicular Line |
| `Circle` | Circle |
| `Angle` | Angle |
| `Region` | Region |

- **"Show hidden objects"** checkbox: when unchecked, rows with `visible = false` are hidden from the table.
- **Two-way selection binding**: clicking a row selects the object on canvas. Selecting on canvas highlights the row. Toggle behavior (click to select/deselect without modifier key).
- **Delete**: removes the object and all descendants via `manager.removeWithDescendants()`.

### 4.3 Measurement Table

| Column | Width | Description |
|--------|-------|-------------|
| ID | 40px fixed | Object ID |
| Type | 120px fixed | Display type name |
| Label | flex | Object label |
| Value | flex | Formatted measurement value |
| Del | 40px fixed | "X" button to remove from table (does not delete object) |

**Measurable types**: Segment (length, `"%.2f"`), Circle (circumference, `"%.2f"`), Angle (degrees, `"%.1f°"`), Region (area, `"%.2f"`).

Measurements update on every render (real-time during drag).

### 4.4 Custom Constructions Table

| Column | Width | Description |
|--------|-------|-------------|
| Apply | 60px fixed | Button to start the apply flow |
| File | flex | Construction filename (without `.json`) |
| Inputs | flex | Comma-separated input type names |
| Del | 40px fixed | "X" button to remove from table |

- Populated via Construction > Load Construction or Load from Folder.
- Not persisted across sessions.
- Point-like input types display as "Point". Non-point types display as their exact type name.

### 4.5 Canvas

- JavaFX `Pane` (`CanvasPane`) with clipping rectangle bound to its size.
- **World group**: `Group` with an `Affine` transform. Contains (in order): grid group, object group, preview group, construction preview group.
- **Export overlay group**: sibling of world group, outside the transform. Used for Custom export bounding box.
- **Hint label**: positioned at bottom center. Shows `"Input N: select a <type>"` during apply mode.
- **Recording banner**: positioned at top center. Shows `"Recording Construction"` during recording. Orange background (`#FF5722`).

### 4.6 Grid

- Adaptive spacing based on zoom level. Target: ~50 screen pixels per grid line. Spacing snaps to 1/2/5 × power-of-10.
- Grid lines: gray (`0.85`), 0.5px stroke.
- Axes: darker gray (`0.5`), 1.0px stroke.
- **Toggleable** via View > Show Grid (⌘G). **Off by default.**

---

## 5. Visual Styling

### 5.1 Colors and Sizes

Colors are set **programmatically** in the renderer, not via CSS classes. CSS classes (`point`, `segment`, `line`, `ray`, `circle`, `region`, `angle`, `label`) are added to nodes but colors are applied after CSS.

| Element | Property | Value |
|---------|----------|-------|
| Points | Fill | Solid filled circle, black |
| Points | Radius | 4px (screen pixels, counter-scaled) |
| Lines/Segments/Rays/Circles | Stroke | 2px (screen pixels, counter-scaled) |
| Default color | All objects | Black |
| Region fill | | `#FFEB3B` at 0.3 opacity (semi-transparent yellow) |
| Angle fill | | `#4CAF50` at 0.3 opacity (semi-transparent green) |
| Angle stroke | | `#4CAF50` at 0.7 opacity, 1px |
| Angle arc radius | | 25px (counter-scaled to world coordinates) |
| Arrowheads | | 8px triangles (counter-scaled) |

### 5.2 State Styles

| State | Style |
|-------|-------|
| Normal | Black, default size |
| Selected | Blue (`#2196F3`) fill and stroke |
| Hover | Blue (`#2196F3`), 1.5x stroke width (1.3x point radius) |
| Preview | Black, 0.4 opacity |
| Construction preview | Orange (`#FF5722`), 0.4 opacity |

### 5.3 Rendering Order

The renderer makes 5 passes in order (back to front):
1. **Regions** (filled polygons)
2. **Angles** (arc sectors)
3. **Linears and Circles** (strokes)
4. **Points** (filled circles, on top)
5. **Labels** (text, counter-scaled and y-flipped via `scaleY = -1`)

### 5.4 Arrow Rendering and Viewport Clipping

- **Lines** (and ParallelLine, PerpendicularLine): clipped to the visible viewport boundary with a 2% margin inset using **Liang-Barsky** line clipping. Arrowheads at both clipped endpoints. The line stroke ends at the arrowhead tip.
- **Rays**: clipped the same way. Arrowhead at the extending (far) end only. The origin end is drawn without an arrowhead.
- **Segments**: no arrowheads, no clipping.
- **Circles**: visually clipped to the same 2% inset clip rect by rendering only the arc segments inside the boundary. No arrowheads — arcs simply terminate. Applies to canvas rendering, circle tool preview, construction preview, and both export paths.
- **Point clamping**: free points created by direct click (PointTool, auto-constructed points in multi-click tools) and free points being dragged (SelectionTool) are clamped to the clip rect. Derived points (intersections, semi-free points, construction results) are not clamped. Clamping is dynamic (evaluated at interaction time against current viewport). X and Y are clamped independently.
- **Tool previews**: ghost lines, rays, and circles during multi-click construction use the same clipping.

### 5.5 CSS Stylesheet

`geometry.css` handles structural styling only:
- Canvas background: white.
- Toolbar background: `#f0f0f0` with bottom border.
- Sidebar backgrounds: `#f8f8f8` with borders.
- Recording mode: orange border (`#FF5722`, 3px) on canvas.
- Table row selection: blue (`#2196F3`) background, white text.

---

## 6. Hit-Testing

### 6.1 Intersection Cache

All pairwise intersections among **visible, non-locked** linear and circle objects are precomputed and stored as `IntersectionCandidate` records `(x, y, parent1Id, parent2Id, sign)`. The cache is rebuilt when objects are added, removed, moved, or have visibility changed.

The cache computes:
- Linear-linear intersections (bounds-checked).
- Linear-circle intersections (bounds-checked, with sign assignment).
- Circle-circle intersections (with sign assignment).

### 6.2 Hit-Testing Priority

When multiple objects are within the snap radius, the hit tester checks 6 tiers in order:

1. **Constructed points** (`PointLike` objects in the manager)
2. **Angles** (point inside arc sector via `containsPoint()`, using zoom-aware arc radius)
3. **Intersection previews** (cached intersection candidates)
4. **Linear objects** (`LinearLike` objects)
5. **Circles**
6. **Regions** (point-in-polygon test via `contains()`)

Distance is the tiebreaker within tiers 1, 3-5. For angles, the first matching angle wins. For regions, the first matching region wins.

**Snap radius**: 10 screen pixels, converted to world coordinates at the current zoom level.

### 6.3 HitResult Types

```
sealed class HitResult {
    ConstructedPoint(id)
    OnAngle(id)
    IntersectionPreview(candidate)
    OnLinear(id)
    OnCircle(id)
    OnRegion(id)
}
```

---

## 7. State Management

### 7.1 Undo / Redo

- **Pattern**: Snapshot-based. Each checkpoint stores a deep copy of the entire `GeometricObjectManager` state (all objects + all metadata + nextId counter).
- **Checkpoint creation**: after each undoable action (add, delete, move, visibility toggle, label edit, measurement toggle, construction apply).
- **Undo**: restores the previous snapshot. **Redo**: restores the next snapshot.
- **Redo truncation**: any new checkpoint truncates the redo stack.
- **Drag granularity**: snapshot on mousedown, checkpoint on mouseup. One undo entry per drag.
- **Recording mode clamping**: `minIndex` prevents undoing past the recording start.
- **Shortcuts**: ⌘Z (undo), ⌘⇧Z (redo).

### 7.2 Unsaved Changes Detection

Tracks the undo stack index at save time (`savedIndex`). If `currentIndex != savedIndex`, there are unsaved changes.

**Warning triggered on**: New (⌘N), Open (⌘O), window close.

---

## 8. Menu Bar

### 8.1 File Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| New | ⌘N | Clear canvas (with unsaved-changes confirmation) |
| *(separator)* | | |
| Save | ⌘S | Save diagram to JSON via FileChooser |
| Open | ⌘O | Load diagram from JSON via FileChooser (with unsaved-changes confirmation) |
| *(separator)* | | |
| Export | ⌘E | Open export dialog |

All File menu items are disabled during construction recording.

### 8.2 Action Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Select All | ⌘A | Select all objects |
| Deselect All | ⌘⇧A | Clear selection |
| Select Parents | ⌘⇧P | Select common parents of all currently selected objects (intersection of parent lists) |
| Select Children | ⌘⇧C | Select objects whose parents include all currently selected objects |
| *(separator)* | | |
| Show | ⌘⇧I | Make selected objects visible |
| Hide | ⌘I | Hide selected objects (also hides their labels) |
| Delete | ⌘D | Delete selected objects and descendants (with confirmation) |
| *(separator)* | | |
| Undo | ⌘Z | Undo |
| Redo | ⌘⇧Z | Redo |
| *(separator)* | | |
| Measure | ⌘M | Add measurable selected objects to measurement table |
| Clear Measurements | ⌘⇧M | Remove all objects from measurement table |

### 8.3 Construction Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Start Recording | ⌘R | Begin recording |
| Save Recording | ⌘⇧S | Save construction to JSON |
| Cancel Recording | — | Discard and restore (Escape key, handled via canvas fallthrough) |
| *(separator)* | | |
| Load Construction | ⌘L | Load `.json` files (multi-select) |
| Load from Folder... | ⌘⇧L | Load all `.json` from a directory |
| Unload All | ⌘U | Clear constructions table |
| *(separator)* | | |
| Midpoint | — | Built-in: Point, Point → Midpoint |
| Parallel Line | — | Built-in: Point, Linear → ParallelLine |
| Perpendicular Line | — | Built-in: Point, Linear → PerpendicularLine |
| Reflection Point | — | Built-in: Point, Linear → ReflectionPoint |

**Menu states**:
- Not recording: Start, Load, Load from Folder, Unload All, and built-ins enabled. Save Recording and Cancel disabled.
- Recording: Start, Load, Load from Folder, Unload All disabled. Save Recording, Cancel, and built-ins enabled. File menu items also disabled.

### 8.4 View Menu

| Item | Shortcut | Description |
|------|----------|-------------|
| Show Grid / Hide Grid | ⌘G | Toggle grid visibility (label toggles) |
| *(separator)* | | |
| Show / Hide Object List | — | Toggle left pane visibility (label toggles) |
| Show / Hide Custom Constructions | — | Toggle constructions table in right pane (label toggles) |
| Show / Hide Measurements | — | Toggle measurements table in right pane (label toggles) |

### 8.5 About Menu

| Item | Description |
|------|-------------|
| About Euclid Sketchpad | Dialog with app icon, version, description, copyright, license, GitHub link |

---

## 9. Construction System

### 9.1 Object Flags

Two boolean flags on `ObjectMetadata`, independent of `visible`:

| Flag | Default | Purpose |
|------|---------|---------|
| `isInput` | false | Marks objects as construction inputs during recording |
| `isLocked` | false | Hides objects from canvas and prevents interaction during recording |

The renderer skips objects where `isLocked == true`. The `visible` field is never modified by recording.

### 9.2 Recording Flow

1. **Start**: all visible `PointLike`, `LinearLike`, and `Circle` objects are marked `isInput = true`. All other objects are marked `isLocked = true`. An undo checkpoint is created, and `minIndex` is set.
2. **During recording**: user builds with normal tools. Built-in and custom constructions are available.
3. **Save**: opens FileChooser. Serializes the entire diagram with `isInput`/`isLocked` flags, labels nulled. Clears all flags. Clears `minIndex`.
4. **Cancel**: removes all objects with ID >= baseline. Clears all flags. Clears `minIndex`.

### 9.3 Recording Mode UI

- Orange border (`#FF5722`, 3px) around the canvas pane (via CSS class `recording-border`).
- Orange banner at top center: "Recording Construction".
- Locked objects show lock icon (🔒) in the Vis and Del columns.

### 9.4 Loading Constructions

- **Load Construction** (⌘L): FileChooser with multi-select for `.json` files.
- **Load from Folder** (⌘⇧L): DirectoryChooser. Loads all `.json` files (non-recursive, sorted by name).
- Each file is parsed into a `LoadedConstruction` record containing: `fileName`, `diagram` (parsed `SerializedDiagram`), `inputTypes` (list of type strings), `inputIds` (sorted IDs of input objects).
- Input types: all point subtypes map to `"Point"`. Non-point types use their exact type name.
- Parsing errors: a single alert lists all failed filenames. Successful files are added normally.

### 9.5 Applying a Construction

1. Click **Apply** on a constructions table row.
2. If the construction has **zero inputs**, it is applied immediately.
3. Otherwise, the app enters **apply mode**:
   - Hint label at bottom: `"Input N: select a <type>"`.
   - Hit-testing is type-filtered: only objects matching the expected type are highlighted on hover.
   - Clicks collect input object IDs sequentially.
   - **Escape** cancels.
4. Once all inputs are collected, `ConstructionApplicator.apply()` runs:
   - Locked objects in the file are skipped.
   - Input objects are mapped to user-selected objects by ID.
   - Constructed objects are instantiated with fresh IDs. Parent references are remapped.
   - Visibility from the file is preserved.
   - If a construction input is hidden in the file, the corresponding user-selected object is hidden.
5. An undo checkpoint is created.

### 9.6 Construction Preview

When hovering over the **last input** during apply mode, a preview of the construction result is rendered. `ConstructionApplicator.computePreview()` creates temporary objects with negative IDs, computes them against the real object map, and returns only the visible ones. These are rendered in the construction preview group with preview opacity.

Built-in constructions also support preview via a `builtInPreviewProvider` that creates a temporary object with ID -1, recomputes it, and returns it if defined.

### 9.7 Type Matching

| Expected type | Matches |
|--------------|---------|
| `"Point"` (or any point subtype name) | Any `PointLike` object |
| `"Linear"` | Any `LinearLike` object |
| `"Line"` | `Line` only |
| `"Ray"` | `Ray` only |
| `"Segment"` | `Segment` only |
| `"ParallelLine"` | `ParallelLine` only |
| `"PerpendicularLine"` | `PerpendicularLine` only |
| `"Circle"` | `Circle` only |

---

## 10. Preview Objects

Preview objects live in **two separate groups**, outside the `GeometricObjectManager`:

1. **`previewGroup`**: tool previews (ghost lines, ghost points during multi-click tools). Managed by each tool.
2. **`constructionPreviewGroup`**: construction apply previews. Managed by `CanvasPane`.

Both are siblings within the world group (under the Affine transform).

Properties of preview objects:
- No IDs assigned (or negative IDs for construction previews).
- No undo entries.
- Rendered with preview opacity (0.4).
- `isMouseTransparent = true`.
- Cleared on tool switch, escape, or commit.

---

## 11. Persistence

### 11.1 Serialization Format

JSON via kotlinx.serialization. Top-level structure:

```json
{
  "appName": "EuclidSketchpad",
  "version": "1.0.0",
  "objects": [ { "id": 0, "type": "FreePoint", "x": 100.0, "y": 50.0 }, ... ],
  "metadata": { "0": { "visible": true, "label": "A", ... }, ... },
  "viewportPanX": 0.0,
  "viewportPanY": 0.0,
  "viewportZoom": 1.0
}
```

**SerializedObject fields**: `id`, `type` (string), `parentIds`, `x`, `y`, `t`, `theta`, `sign`, `pointIds`. Only relevant fields are populated per type.

**SerializedMetadata fields**: `visible`, `label`, `labelVisible`, `labelOffsetX` (default 10), `labelOffsetY` (default -10), `inMeasurementTable`, `labelAnchorAngle`, `labelAnchorT`, `isInput`, `isLocked`. Unknown keys are ignored on load.

### 11.2 Diagram Save/Load

- **Save** (⌘S): serializes the full manager state via `DiagramSerializer.serialize()`. Marks undo as saved.
- **Open** (⌘O): clears current state, deserializes via `DiagramSerializer.deserialize()`, rebuilds intersection cache, creates checkpoint, marks saved.

### 11.3 Construction Files

Same JSON format as diagrams, with these differences:
- `isInput` and `isLocked` flags are set on the relevant objects.
- All label fields are nulled/defaulted.
- Serialized via `ConstructionSerializer.saveConstruction()`.

### 11.4 Versioning

Every JSON file (diagram or construction) includes top-level `"appName"` and `"version"` fields. The `appName` is always `"EuclidSketchpad"`. The version string follows `<major>.<minor>.<patch>` format and is set to the app's current version string (e.g. `"1.0.0"`).

**Constants**: `APP_NAME` and `APP_VERSION` are defined once as top-level `const val`s in `Main.kt`. Referenced by both serializers and the About dialog.

**Validation on load** (in order):
1. **App name check**: if `appName` is not `"EuclidSketchpad"`, the file is rejected with an `IncompatibleAppNameException`.
2. **Version check**: the app parses the major version from the file's `"version"` field and compares it to its own major version. If they differ, the file is rejected with an `IncompatibleVersionException`.
3. Both fields are required in `SerializedDiagram` (no defaults); files missing either field are rejected during deserialization.

**Compatibility contract**:
- Within a major version, all changes to the serialization format must be **additive only**: new fields with defaults, new object types. Renaming, removing, or changing the semantics of existing fields requires a major version bump.

---

## 12. Export

### 12.1 Export Dialog

Non-modal dialog with:

- **Method**: 3 radio buttons (Canvas, Custom, Auto-fit).
- **Format**: 2 radio buttons (PNG, SVG).
- **Options**: "Transparent background" checkbox, "Include grid" checkbox (both default off).
- **Dimensions**: Width and Height text fields.
- **Buttons**: Export, Cancel.

### 12.2 Canvas Method

Exports at the canvas's current screen size. Width/height fields are non-editable, showing current canvas dimensions.

### 12.3 Custom Method

- Width and height fields are editable.
- A **bounding box overlay** appears centered on the canvas (~80% of canvas area), with dark semi-transparent letterboxing around it. Orange border on the clear area.
- A scale factor label shows `"Preview at N%"`.
- **Pan mode is enabled**: Space+drag pans the canvas under the fixed bounding box.
- Toolbar is disabled during Custom mode.
- Export renders the content within the bounding box at the specified resolution.

### 12.4 Auto-fit Method

- Width is editable (default 1920). Height is computed from the bounding box aspect ratio (bidirectional: editing height also updates width).
- Bounding box is computed automatically by `ExportRenderer.computeAutoFitBounds()`:
  - All visible points.
  - Parent points of all visible linears and regions (regardless of parent visibility).
  - Extremal points of visible circles (top, bottom, left, right of each circle).
  - 8% margin on all sides (4× the 2% viewport margin fraction), providing comfortable padding beyond the clip boundary used during export rendering.
  - Degenerate fallback: if bounding box is empty, uses `[-100, -100, 100, 100]`. If width or height is near zero, pads by 50 units.

### 12.5 Export Rendering

Both PNG and SVG exports share the same rendering logic:

- Selection and hover states are cleared (all objects render black).
- 5 passes: regions → angles → linears/circles → points → labels.
- Lines, rays, and circles are clipped to the export region (with 2% margin inset). Lines and rays use **Liang-Barsky** clipping with arrowheads at clip points. Circles render only visible arc segments inside the clip rect.
- Region fill: yellow (`#FFEB3B`, 0.3 opacity).
- Angle fill: green (`#4CAF50`, 0.3 opacity).
- Grid uses the same adaptive spacing algorithm as the canvas.
- Background: white (or transparent).

**PNG**: builds a JavaFX `Group`, takes a snapshot via `Node.snapshot()`, writes via `ImageIO`.

**SVG**: generates SVG XML string manually. Each object type maps to SVG elements: points → `<circle>`, linears → `<line>`, circles → `<circle>`, angles → `<path>` (arc), regions → `<polygon>`, labels → `<text>`. Coordinates are y-flipped. Numbers formatted to 2 decimal places.

### 12.6 Headless Export (CLI)

The app supports a `--export` CLI flag for batch export without a GUI window:

```
./gradlew run --args="--export <diagram.json> [outputDir]"
```

- **diagram.json** (required): path to a diagram file.
- **outputDir** (optional, default `exports/`): directory for output files.

Produces 6 files: `<name>_canvas.png/svg` (1000×700, default viewport), `<name>_custom.png/svg` (1920×1080, auto-fit bounds), `<name>_autofit.png/svg` (1920×auto, auto-fit bounds with aspect-ratio height). All exports use white background, no grid.

`HeadlessExportApp` is a JavaFX `Application` subclass (required for PNG snapshot). It loads the diagram, runs `ExportRenderer.export()` for each combination, and calls `Platform.exit()`.

---

## 13. Escape Key Handling

Escape is handled in priority order:

1. **Apply mode active**: cancel apply mode.
2. **Current tool has in-progress operation**: cancel it (e.g., cancel mid-construction line). Tool's `onEscapePressed()` returns `true`.
3. **Recording mode active**: cancel recording (fires the Cancel Recording menu item via `onEscapeFallthrough`).
