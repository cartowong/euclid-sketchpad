# Euclidean Geometry Engine

Specification for the geometry computation layer. This module is pure math — no UI framework imports. All classes live in the `geometry/` package.

---

## 1. Core Interfaces

### 1.1 GeometricObject

```
sealed interface GeometricObject {
    id: Int                              // immutable, assigned in construction order, starting at 0
    parentIds: List<Int>                 // IDs of parent objects (empty for free points)
    isDefined: Boolean                   // false when geometrically invalid (e.g., non-intersecting)
    distanceFrom(x, y) -> Double         // distance from point (x,y) to this object, for hit-testing
    recompute(objects: Map<Int, Object>) // recompute position/state from parent objects
}
```

Every concrete geometric type implements this interface. The sealed constraint means the set of types is closed — adding a new type requires updating all exhaustive `when` expressions in serializers, the object manager, and the sidebar.

### 1.2 PointLike

```
interface PointLike {
    x: Double    // x-coordinate in math space
    y: Double    // y-coordinate in math space
}
```

All point types implement both `GeometricObject` and `PointLike`. Non-point code reads coordinates through this interface without knowing the concrete point type.

### 1.3 LinearLike

```
enum LinearType { LINE, RAY, SEGMENT }

interface LinearLike {
    x1, y1: Double      // first defining point
    x2, y2: Double      // second defining point
    linearType: LinearType
    linearDistanceFrom(x, y) -> Double   // default implementation using GeometryUtils
}
```

All linear objects (Line, Ray, Segment, ParallelLine, PerpendicularLine) implement both `GeometricObject` and `LinearLike`. The `linearDistanceFrom` default method delegates to the appropriate `GeometryUtils` distance function based on `linearType`.

### 1.4 LinearData

```
data class LinearData(x1, y1, x2, y2: Double, type: LinearType)
```

Extracted from any `LinearLike` object by `IntersectionUtils.extractLinear()`. Used to pass linear geometry data to intersection algorithms without requiring the full object.

---

## 2. Object Types

### 2.1 Free Points

**FreePoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `x` | Double (mutable) | x-coordinate, freely settable |
| `y` | Double (mutable) | y-coordinate, freely settable |

- Parents: none.
- Always defined.
- `recompute()`: no-op.
- `distanceFrom(x, y)`: `hypot(this.x - x, this.y - y)`.

**FreePointOnLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `lineId` | Int | Parent LinearLike object ID |
| `t` | Double (mutable) | Parameter, range `(-inf, +inf)` |

- Parents: `[lineId]`.
- `recompute()`: extracts `LinearData` from parent. Position = `(x1 + t*(x2-x1), y1 + t*(y2-y1))`. Undefined if parent is undefined.
- `updateParameterFromPosition(mx, my)`: sets `t` to `closestParameterOnLine(mx, my, ...)` (unclamped).
- `distanceFrom(x, y)`: Euclidean distance from `(this.x, this.y)` to `(x, y)`.

**FreePointOnRay**

Same structure as FreePointOnLine, except:
- `t` is clamped to `[0, +inf)` in both `recompute()` and `updateParameterFromPosition()`.

**FreePointOnSegment**

Same structure as FreePointOnLine, except:
- `t` is clamped to `[0, 1]` in both `recompute()` and `updateParameterFromPosition()`.

**FreePointOnCircle**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circleId` | Int | Parent Circle object ID |
| `theta` | Double (mutable) | Angle parameter in radians |

- Parents: `[circleId]`.
- `recompute()`: `x = cx + r*cos(theta)`, `y = cy + r*sin(theta)`. Undefined if parent circle is undefined.
- `updateParameterFromPosition(mx, my)`: `theta = atan2(my - cy, mx - cx)`.
- `distanceFrom(x, y)`: Euclidean distance.

### 2.2 Derived Points

**Midpoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First parent point |
| `point2Id` | Int | Second parent point |

- Parents: `[point1Id, point2Id]`.
- `recompute()`: `x = (p1.x + p2.x) / 2`, `y = (p1.y + p2.y) / 2`. Undefined if either parent is undefined.
- Not movable.
- `distanceFrom(x, y)`: Euclidean distance.

**ReflectionPoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point to reflect |
| `linearId` | Int | LinearLike object to reflect across |

- Parents: `[pointId, linearId]`.
- `recompute()`: reflects across the **infinite line** defined by the linear object's direction, regardless of whether the parent is a Line, Ray, or Segment.
  ```
  dx = x2 - x1, dy = y2 - y1
  lenSq = dx*dx + dy*dy
  t = ((px - x1)*dx + (py - y1)*dy) / lenSq
  projX = x1 + t*dx, projY = y1 + t*dy
  x = 2*projX - px, y = 2*projY - py
  ```
- Not movable.
- `distanceFrom(x, y)`: Euclidean distance.

### 2.3 Intersection Points

All intersection types implement `PointLike`. They become undefined when the intersection is geometrically invalid (parents don't intersect, or intersection falls outside bounded parent's range).

**LinearLinearIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `linear1Id` | Int | First LinearLike parent |
| `linear2Id` | Int | Second LinearLike parent |

- Parents: `[linear1Id, linear2Id]`.
- `recompute()`:
  1. Extract `LinearData` from both parents.
  2. Compute infinite-line intersection via `lineLineIntersection()`.
  3. Compute parameter `t` on each parent via `parameterOnLine()`.
  4. Check both `t` values against bounds via `isInBounds(t, type)`.
  5. Undefined if parallel, coincident, or intersection falls outside a bounded parent's range.

**LinearCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `linearId` | Int | LinearLike parent |
| `circleId` | Int | Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[linearId, circleId]`.
- `recompute()`:
  1. Compute all line-circle intersection candidates via `lineCircleIntersections()`.
  2. Filter candidates to those within the linear parent's bounds.
  3. If one candidate: use it (tangent case).
  4. If two candidates: select by `sign` using cross-product disambiguation. Reference direction = perpendicular to line direction `(-dy, dx)`. `assignSign(ix, iy, cx, cy, -refDy, refDx)` determines which candidate matches.
  5. Undefined if no in-bounds candidates.

**CircleCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circle1Id` | Int | First Circle parent |
| `circle2Id` | Int | Second Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[circle1Id, circle2Id]`.
- `recompute()`:
  1. Compute candidates via `circleCircleIntersections()`.
  2. If one candidate: use it (tangent case).
  3. If two candidates: select by `sign`. Reference direction = vector from center1 to center2 `(c2x-c1x, c2y-c1y)`. `assignSign(ix, iy, c1x, c1y, refDx, refDy)` determines which candidate matches.
  4. Undefined if circles don't intersect (too far apart, concentric, or one inside the other).

### 2.4 Linear Objects

All linear objects implement both `GeometricObject` and `LinearLike`. Their `distanceFrom()` delegates to `linearDistanceFrom()`.

**Line**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First defining point |
| `point2Id` | Int | Second defining point |

- `linearType`: `LINE` (extends infinitely both directions).
- `recompute()`: copies coordinates from both parent points. Undefined if either parent is undefined.

**Ray**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `endpointId` | Int | Ray's endpoint (origin) |
| `throughId` | Int | Point the ray passes through |

- `linearType`: `RAY` (extends infinitely from endpoint through throughPoint).
- `x1, y1` = endpoint; `x2, y2` = throughPoint.
- `recompute()`: same as Line.

**Segment**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First endpoint |
| `point2Id` | Int | Second endpoint |

- `linearType`: `SEGMENT` (finite, between two endpoints).
- `recompute()`: same as Line.

**ParallelLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point the line passes through |
| `linearId` | Int | LinearLike object to be parallel to |

- `linearType`: `LINE`.
- `recompute()`: extracts direction `(dx, dy)` from parent linear. Sets `x1, y1` to parent point's position. Sets `x2 = x1 + dx`, `y2 = y1 + dy`. Always defined if parents are defined and direction is non-zero.

**PerpendicularLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point the line passes through |
| `linearId` | Int | LinearLike object to be perpendicular to |

- `linearType`: `LINE`.
- `recompute()`: extracts direction `(dx, dy)` from parent linear. Rotates 90 degrees: `x2 = x1 - dy`, `y2 = y1 + dx`. Always defined if parents are defined and direction is non-zero.

### 2.5 Circle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `centerId` | Int | Center point |
| `radiusPointId` | Int | Point on the circle |

- Parents: `[centerId, radiusPointId]`.
- Computed properties: `cx`, `cy` (center coordinates), `radius` (Euclidean distance from center to radius point).
- Does not implement `LinearLike` or `PointLike`.
- `recompute()`: copies center coordinates, computes `radius = euclideanDistance(cx, cy, rx, ry)`. Undefined if either parent is undefined.
- `distanceFrom(x, y)`: `|euclideanDistance(x, y, cx, cy) - radius|` (distance to the circle's circumference).

### 2.6 Angle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointAId` | Int | First ray point |
| `vertexId` | Int | Vertex point |
| `pointCId` | Int | Second ray point |

- Parents: `[pointAId, vertexId, pointCId]`.
- Computed properties: `vertexX`, `vertexY`, `startAngleRadians`, `sweepAngleRadians`.
- Does not implement `LinearLike` or `PointLike`.
- `recompute()`:
  ```
  angleBA = atan2(A.y - V.y, A.x - V.x)
  angleBC = atan2(C.y - V.y, C.x - V.x)
  startAngleRadians = angleBA
  sweep = -(angleBC - angleBA)       // negate for clockwise
  if (sweep <= 0) sweep += 2*PI      // normalize to (0, 2*PI]
  sweepAngleRadians = sweep
  ```
  The angle is a **clockwise sweep** from ray VA to ray VC. Swapping A and C gives the supplementary/reflex angle.
- `measureDegrees()`: `sweepAngleRadians * 180 / PI`.
- `distanceFrom(x, y)`: checks if point is within the arc wedge (within 1.5x the visual arc radius of the vertex, and within the angular sweep). Returns distance to the midpoint of the arc region, or `MAX_VALUE` if outside. The visual arc radius (20.0) is hardcoded in world coordinates.

### 2.7 Region

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointIds` | List\<Int\> | Ordered list of vertex point IDs |

- Parents: `pointIds` (all vertex points).
- Computed property: `vertices: List<Pair<Double, Double>>` — resolved coordinates.
- Does not implement `LinearLike` or `PointLike`.
- `recompute()`: resolves all point IDs to `(x, y)` pairs. Undefined if any parent point is undefined.
- `distanceFrom(x, y)`: minimum Euclidean distance to any edge segment.
- `contains(x, y)`: ray-casting algorithm for point-in-polygon test.
- `measureArea()`: shoelace formula (unsigned/absolute value). Valid for simple (non-self-intersecting) polygons only.

---

## 3. Utility Functions

### 3.1 GeometryUtils

All functions in this module use Euclidean (Cartesian) geometry.

| Function | Formula |
|----------|---------|
| `euclideanDistance(x1, y1, x2, y2)` | `hypot(x2-x1, y2-y1)` |
| `pointToLineDistance(px, py, x1, y1, x2, y2)` | `|cross product| / length` of the infinite line through (x1,y1)-(x2,y2) |
| `pointToSegmentDistance(px, py, x1, y1, x2, y2)` | Project onto segment, clamp `t` to `[0, 1]`, return Euclidean distance to projection |
| `pointToRayDistance(px, py, x1, y1, x2, y2)` | Project onto ray, clamp `t` to `[0, inf)`, return Euclidean distance to projection |
| `pointToCircleDistance(px, py, cx, cy, r)` | `|euclideanDistance(px, py, cx, cy) - r|` |
| `closestParameterOnLine(px, py, x1, y1, x2, y2)` | `((px-x1)*dx + (py-y1)*dy) / (dx*dx + dy*dy)` — unclamped parameter `t` where `t=0` is `(x1,y1)` and `t=1` is `(x2,y2)` |

### 3.2 IntersectionUtils

Epsilon constant: `1e-10`.

**extractLinear(obj)**: Returns `LinearData` if `obj` is a defined `LinearLike`, otherwise `null`.

**lineLineIntersection(x1, y1, x2, y2, x3, y3, x4, y4)**:
- Computes intersection of two infinite lines using the determinant method.
- Returns `null` if lines are parallel or coincident (`|denom| < EPS`).
- Formula: `t = ((x3-x1)*dy2 - (y3-y1)*dx2) / (dx1*dy2 - dy1*dx2)`, then `(x1 + t*dx1, y1 + t*dy1)`.

**parameterOnLine(px, py, x1, y1, x2, y2)**:
- Returns parameter `t` for the projection of `(px, py)` onto the infinite line.
- Same formula as `closestParameterOnLine`.

**isInBounds(t, type)**:
- `LINE`: always true.
- `RAY`: `t >= -EPS`.
- `SEGMENT`: `t >= -EPS && t <= 1.0 + EPS`.

**lineCircleIntersections(x1, y1, x2, y2, cx, cy, r)**:
- Substitutes the parametric line equation into the circle equation, yielding a quadratic in `t`.
- `a = dx*dx + dy*dy`, `b = 2*(fx*dx + fy*dy)`, `c = fx*fx + fy*fy - r*r` where `f = (x1-cx, y1-cy)`.
- Discriminant `disc = b*b - 4*a*c`. Returns empty if `disc < -EPS`.
- Returns one point if `|t1 - t2| <= EPS` (tangent), otherwise two points.
- **Does not** bounds-check against the linear type — callers must check bounds separately.

**circleCircleIntersections(cx1, cy1, r1, cx2, cy2, r2)**:
- Returns empty if: concentric (`d < EPS`), too far apart (`d > r1+r2+EPS`), or one inside the other (`d < |r1-r2|-EPS`).
- Computes midpoint on radical axis: `a = (r1*r1 - r2*r2 + d*d) / (2*d)`, `h = sqrt(r1*r1 - a*a)`.
- Returns one point if `h < EPS` (tangent), otherwise two points offset perpendicular to the center-to-center line.

**assignSign(ix, iy, refX, refY, refDx, refDy)**:
- Cross product: `(ix-refX)*refDy - (iy-refY)*refDx`.
- Returns `+1` if `>= 0`, `-1` otherwise.
- Used to deterministically pick one of two intersection candidates so the choice is stable under continuous parent movement.

### 3.3 MeasurementUtils

| Function | Input | Formula |
|----------|-------|---------|
| `segmentLength(seg)` | Segment | `euclideanDistance(x1, y1, x2, y2)` |
| `circleCircumference(circle)` | Circle | `2 * PI * radius` |
| `angleDegrees(angle)` | Angle | `sweepAngleRadians * 180 / PI` |
| `regionArea(region)` | Region | Shoelace formula (absolute value) |

`measure(obj)` dispatches by type and returns `null` for non-measurable types (points, lines, rays).

`measureLabel(obj)` returns a formatted string: `"%.2f"` for lengths/areas, `"%.1f\u00B0"` for angles.

---

## 4. Dependency Graph

- Every object's ID is assigned in construction order, starting at 0.
- A parent's ID is always lower than its child's ID.
- **Update propagation**: iterate objects from lowest to highest ID. Each object with a dirty parent calls `recompute()` once. The ID ordering guarantees a valid topological sort.
- **Undefined cascading**: when an object becomes undefined, all descendants that depend on it also become undefined via their `recompute()` checks.

---

## 5. Type Summary

| Type | Implements | Parents | Movable | Measurable |
|------|-----------|---------|---------|------------|
| FreePoint | PointLike | none | yes | no |
| FreePointOnLine | PointLike | LinearLike | yes (constrained) | no |
| FreePointOnRay | PointLike | LinearLike | yes (constrained) | no |
| FreePointOnSegment | PointLike | LinearLike | yes (constrained) | no |
| FreePointOnCircle | PointLike | Circle | yes (constrained) | no |
| Midpoint | PointLike | Point, Point | no | no |
| ReflectionPoint | PointLike | Point, LinearLike | no | no |
| LinearLinearIntersection | PointLike | LinearLike, LinearLike | no | no |
| LinearCircleIntersection | PointLike | LinearLike, Circle | no | no |
| CircleCircleIntersection | PointLike | Circle, Circle | no | no |
| Line | LinearLike | Point, Point | no | no |
| Ray | LinearLike | Point, Point | no | no |
| Segment | LinearLike | Point, Point | no | yes (length) |
| ParallelLine | LinearLike | Point, LinearLike | no | no |
| PerpendicularLine | LinearLike | Point, LinearLike | no | no |
| Circle | — | Point, Point | no | yes (circumference) |
| Angle | — | Point, Point, Point | no | yes (degrees) |
| Region | — | Point list | no | yes (area) |

---

## 6. File Layout

```
geometry/
  GeometricObject.kt    # GeometricObject sealed interface
  Point.kt              # PointLike interface, FreePoint, FreePointOn*, Midpoint, ReflectionPoint
  Line.kt               # LinearType enum, LinearLike interface, Line, Ray, Segment, ParallelLine, PerpendicularLine
  Circle.kt             # Circle
  Angle.kt              # Angle
  Region.kt             # Region
  Intersection.kt       # LinearLinearIntersection, LinearCircleIntersection, CircleCircleIntersection
  IntersectionUtils.kt  # LinearData, IntersectionUtils object (intersection algorithms)
  GeometryUtils.kt      # GeometryUtils object (distance functions)
  MeasurementUtils.kt   # MeasurementUtils object (measurement dispatch)
```
