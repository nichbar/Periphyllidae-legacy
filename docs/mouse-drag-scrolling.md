# Mouse Drag Scrolling

## Problem

Compose Multiplatform `LazyRow` and `LazyColumn` handle wheel and touchpad scrolling on desktop, but a left-button mouse drag is not automatically converted into list scrolling. In the home screen this meant album rows could be scrolled with a trackpad or wheel, but dragging horizontally with a mouse did not move the content. The same limitation also applied to vertical page scrolling.

## Implementation

The home screen now gives each scrollable list its own `LazyListState` and applies a platform-specific `Modifier.mouseDragScrollable(...)` helper:

- The home `LazyColumn` uses `Orientation.Vertical` for page-level mouse drag scrolling.
- Each album `LazyRow` uses `Orientation.Horizontal` for carousel-style mouse drag scrolling.
- The helper is declared in common code and implemented with `actual` platform functions.

On JVM, the helper uses `pointerInput` to watch mouse gestures. It waits until movement passes `viewConfiguration.touchSlop`, then claims the gesture only if the configured axis dominates the other axis. For example, a vertical list claims the drag only when vertical movement is greater than horizontal movement. Once claimed, the helper calls `LazyListState.dispatchRawDelta(-delta)` to move the list with the mouse.

On Android, the helper returns the original modifier unchanged. Android touch scrolling already works through Compose's built-in list behavior, so there is no custom pointer handling on that platform.

## Gesture Arbitration

The vertical and horizontal handlers intentionally use axis dominance before consuming pointer changes:

- A mostly vertical drag on the home screen scrolls the page.
- A mostly horizontal drag on an album row scrolls that row.
- Normal clicks still work because the helper waits for slop before consuming the gesture.
- Non-mouse pointer input is ignored by the JVM helper, preserving default behavior for touch and stylus input.

## Files

- `composeApp/src/commonMain/kotlin/now/link/periphyllidae/ui/screens/HomeScreen.kt` wires `LazyListState` into the home `LazyColumn` and album `LazyRow`s.
- `composeApp/src/jvmMain/kotlin/now/link/periphyllidae/ui/screens/MouseDragScrollable.jvm.kt` implements desktop mouse drag scrolling.
- `composeApp/src/androidMain/kotlin/now/link/periphyllidae/ui/screens/MouseDragScrollable.android.kt` keeps Android behavior unchanged.

## Verification

Use targeted compile checks after changes:

```bash
./gradlew :composeApp:compileKotlinJvm
./gradlew :composeApp:compileDebugKotlinAndroid
```
