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

### Unified Player Morph Contract

The mini-player and expanded player are not separate visual components. They are two render states of the same player surface.

When `BottomSheetState.progress` changes from 0 to 1:

1. The player surface moves continuously rather than appearing as a replacement.
2. Collapsed content fades and scales out from the same progress that reveals expanded content.
3. Expanded content starts slightly translated and underscaled, then settles to its final geometry.
4. Artwork, controls, text, gradients, blur, and navigation chrome must remain driven by the same expansion state.
5. Reverse collapse must use the same mapping so the surface visually travels back to its docked position.
6. Direct gestures must remain interruptible at every frame. Motion never blocks playback, seeking, or queue interaction.

Use low-amplitude easing for visual polish and spring-based motion for direct manipulation. Avoid independent nested animations that fight the sheet gesture.

### Title Motion

The app identity is always the exact text **"Echo Music"**. Use a small alpha + translation + scale entrance, normally within the existing 160–320ms motion envelope. The title must remain readable during route changes and must not animate continuously without a meaningful state change.

---

## 5. Performance Rules

* Avoid creating new objects or collections inside frequently recomposed animation content.
* Keep image decoding and theme extraction off the main thread.
* Prefer hardware-backed rendering for simple transforms (`graphicsLayer`) and use blur/effects selectively.
* Do not add continuous animations to static content without a clear interaction purpose.
* Preserve premium effects on capable hardware while keeping the visual path adaptive on constrained devices.
* Do not solve performance regressions by disabling existing product functionality.
* For player morphing, animate alpha, translation, scale, and bounded effect intensity from the existing sheet progress instead of launching separate per-frame coroutines.
* Do not perform network resolution, database I/O, bitmap decoding, or FFmpeg work from frame-critical UI animation lambdas.

---

## 6. Authentication UI

* Google account selection identifies the user's intended Google identity but does **not** manufacture a YouTube InnerTube session from a Google ID token.
* YouTube login must reuse a legitimate authenticated YouTube/YouTube Music WebView session when available.
* The app validates the resulting YouTube account before marking the handoff complete. Failed, expired, or mismatched sessions remain retryable.

---

## 7. Audio Quality & Export UI

* User-facing quality choices are truthful. `Lossless when available` means genuine lossless media only; compressed Opus/AAC streams are never mislabeled as lossless.
* Legacy preference values may remain for compatibility, but new playback/download/export paths must resolve through the current selected quality.
* Ringtone trimming and audio export are real media operations. UI progress must represent actual work and must not be implemented by copying a source file or blocking a coroutine with `runBlocking` merely to report progress.
* Exported output may still use the app's existing MP3 export behavior, but source resolution must honor the selected quality before transcoding.

---

## 8. Extending the Design System

Before adding a brand new UI component, always check `ui/component/` to see if an existing one already implements our conventions.

**Key Rule:** When working on UI, look at the existing screens (like the original Listen Together or Settings screens) and copy their specific visual style, spacing, and modifier chains. Do NOT refactor existing screens to match standard Material 3 unless explicitly requested. Our custom aesthetic takes precedence over M3 guidelines.
