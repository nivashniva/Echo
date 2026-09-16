# Echo Music Design Guidelines

Echo Music follows a **custom, modern aesthetic** that blends some Material Design principles with unique, iOS-inspired patterns.

This document is the definitive guide for designing and implementing UI in the Echo Music codebase. All new UI work and refactors must follow these custom principles rather than strictly adhering to Google's Material Design 3 spec.

---

## 1. Color System & Theming

We use a dynamic color system, but apply it in a custom way to achieve a unique look.

### Dynamic Color & Seed
* **Dynamic First:** Colors must come from `MaterialTheme.colorScheme`, but are often modified (e.g., using alpha transparency) to create glass-like effects.
* **Translucency:** A core part of the Echo Music look is translucent surfaces. For example, cards often use `surfaceVariant.copy(alpha = 0.3f)` rather than solid M3 container colors.

### Semantic Color Roles
Use the correct semantic color roles as defined by our theme:
* **Primary (`primary` / `onPrimary`):** Used for the most prominent components across the app, active states, and filled buttons.
* **Surface (`surface` / `onSurface`):** Backgrounds for the app and solid menus.
* **Translucent Surfaces:** Custom translucent backgrounds (like `surfaceVariant.copy(alpha = 0.3f)`) are heavily used for cards, segmented buttons, and grouped lists to create a softer, layered aesthetic.

---

## 2. Components in Detail

Do NOT strictly force Material 3 components if they break the app's custom aesthetic. Match the existing components found in the app.

### Buttons & Controls
* **Segmented Controls:** We frequently use custom segmented buttons (e.g., Row with rounded buttons) rather than M3 standard tabs or segmented buttons.
* **Rounded Shapes:** Elements heavily lean towards large corner radii (`RoundedCornerShape(24.dp)` or `CircleShape`).

### Cards & Surfaces
* **Custom Cards:** Unlike standard M3 cards (which use solid `surfaceContainer` colors), Echo Music cards typically use:
  * *Container:* `surfaceVariant.copy(alpha = 0.3f)`
  * *Shape:* `RoundedCornerShape(24.dp)` or `28.dp`
  * *Elevation:* 0.dp (flat, translucent look).
* Grouped items within cards are a common pattern (similar to iOS Settings).

### Navigation & Headers
* **Top App Bars:** We often use custom implementations or standard `TopAppBar` rather than `LargeTopAppBar`. Headers are sometimes manually placed over scrolling content with custom fade-in animations rather than using standard M3 `Scaffold` scroll behaviors.
* **Bottom Navigation Bar:** Custom floating tab bars (`ui/component/floatingtabbar/`) are preferred over standard M3 `NavigationBar`.

---

## 3. Typography

Always use `MaterialTheme.typography` but respect the app's established font weights and sizes, which often lean towards bold, expressive headers and softer body text.

---

## 4. Motion & Player Transitions

Echo uses a **fast, coordinated motion language** rather than independent decorative animations.

* **Timing:** Prefer short 160–320ms transitions for navigation, surfaces, and content changes. Use spring motion for direct manipulation such as dragging or player expansion.
* **Easing:** Use the existing `EmphasizedEasing` or another short, smooth easing curve for non-interactive transitions. Avoid long, sluggish animations and large bounce effects.
* **State transitions:** Prefer `AnimatedVisibility`, `AnimatedContent`, `animate*AsState`, and coordinated `graphicsLayer` transforms over rebuilding separate screens for each state.
* **Player:** The mini-player and expanded player are one continuous interaction. Artwork, title, controls, progress, and background should transform together from the same player state instead of appearing as unrelated replacements.
* **Artwork:** Keep album art stable during playback-state changes. Animate scale/position/alpha rather than destroying and recreating the image node when practical.
* **High refresh rate:** Avoid per-frame allocations, bitmap conversion, or expensive blur/shader work inside animation lambdas. Keep animated state small and stable.
* **App identity:** The top-level title remains exactly **"Echo Music"**. Its entrance animation may combine a small horizontal translation, alpha, and scale, but must remain readable and must never permanently hide the title.
* **Loading:** Use existing shimmer/Lottie infrastructure for async content. Loading transitions should expose meaningful progress without blocking playback startup.

### Player Motion Contract

When a `BottomSheetPlayer` transitions between collapsed and expanded states:

1. Artwork position/size changes continuously with the sheet progress.
2. Text and controls follow the same progress rather than using unrelated enter/exit animations.
3. Background/blur intensity may change with progress, but must remain bounded to avoid frame-time spikes.
4. Reverse expansion must use the same geometry and state mapping so the collapse looks physically connected.
5. Player gestures remain responsive during the transition. Animation must never become a prerequisite for playback or seeking.

---

## 5. Performance Rules

* Avoid creating new objects or collections inside frequently recomposed animation content.
* Keep image decoding and theme extraction off the main thread.
* Prefer hardware-backed rendering for simple transforms (`graphicsLayer`) and use blur/effects selectively.
* Do not add continuous animations to static content without a clear interaction purpose.
* Preserve premium effects on capable hardware while keeping the visual path adaptive on constrained devices.
* Do not solve performance regressions by disabling existing product functionality.

---

## 6. Authentication UI

* Google account selection identifies the user's intended Google identity but does **not** manufacture a YouTube InnerTube session from a Google ID token.
* YouTube login must reuse a legitimate authenticated YouTube/YouTube Music WebView session when available.
* The app validates the resulting YouTube account before marking the handoff complete. Failed, expired, or mismatched sessions remain retryable.

---

## 7. Extending the Design System

Before adding a brand new UI component, always check `ui/component/` to see if an existing one already implements our conventions.

**Key Rule:** When working on UI, look at the existing screens (like the original Listen Together or Settings screens) and copy their specific visual style, spacing, and modifier chains. Do NOT refactor existing screens to match standard Material 3 unless explicitly requested. Our custom aesthetic takes precedence over M3 guidelines.
