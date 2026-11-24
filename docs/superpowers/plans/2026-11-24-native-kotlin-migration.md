# Full Native Kotlin Android Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild LookLite as a 100% Full Native Android app written in Kotlin using Jetpack Compose and native OpenGL ES 3.0, replacing the WebView hybrid layer completely while retaining all 42 film simulation profiles, real 17³ 3D LUT processing, and Apple HIG Liquid Glass UI.

**Architecture:** A native single-activity Android app (`MainActivity.kt`) hosting a Jetpack Compose UI layer over a high-performance native OpenGL ES 3.0 renderer (`LookLiteRenderer.kt`). OpenGL ES 3.0 handles hardware-accelerated 3D LUT color grading, skin polish, glow, clarity, and film grain via GLSL shaders. The UI layer provides the boxy modern header, aspect ratio bar, interactive Rule-of-Thirds crop overlay, floating sub-menu controls card, and floating bottom navigation pill.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose Material 3, AndroidX Activity/Lifecycle KTX, OpenGL ES 3.0 (`GLES30`), Android Photo Picker (`ActivityResultContracts.PickVisualMedia`), Android `MediaStore` export API.

**Spec:** Native Kotlin Android Application Architecture with zero WebView dependencies.

## Global Constraints
- 100% Native Kotlin and OpenGL ES 3.0 — no WebViews, no HTML/JS runtime.
- Maintain all 42 color science profiles across 6 brands (POLES, FUJIFILM, KODAK, LEICA, HASSELBLAD, ILFORD/VINTAGE) with identical real 17³ 3D LUT mathematical accuracy.
- Maintain Apple HIG Light Liquid Glass visual language: Coral Red active accents (`#FF2D55`), boxy modern top header (`12dp` radius), floating sub-menu card (`20dp` radius), and floating bottom navigation capsule (`9999dp` pill).
- Provide native interactive crop framing with draggable Rule-of-Thirds grid and multi-ratio selection (`Original`, `1:1`, `4:5`, `9:16`, `16:9`, `3:2`, `2:3`).
- Zero permission errors when loading photos on Android 13/14/15/17.

## Review Focus
- High-resolution photo loading (12MP, 24MP, 48MP) without OOM or GPU texture overflows.
- 17³ 3D LUT texture allocation and sampling in `GLES30` on various Adreno/Mali GPU drivers.
- Smooth Compose drag gestures on crop framing and split-view comparisons without UI thread stutters.
- High-res export rendering via offscreen FBO and saving cleanly to `Pictures/LookLite`.
- Preserving slider deltas and filter intensity scaling (0–100%) identically to previous shader behavior.

---

### Task 1: Gradle Build Configuration for Kotlin & Jetpack Compose

**Files:**
- Modify: `build.gradle`
- Modify: `app/build.gradle`
- Modify: `settings.gradle`

- [ ] **Step 1: Configure Kotlin Android and Compose Compiler plugins**
  Update `build.gradle` to add Kotlin Android plugin `1.9.22`.
  Update `app/build.gradle` with `org.jetbrains.kotlin.android`, enable `buildFeatures { compose true }`, and add Compose compiler extension `1.5.8`.

- [ ] **Step 2: Add AndroidX & Jetpack Compose dependencies**
  Add dependencies:
  - `androidx.core:core-ktx:1.12.0`
  - `androidx.lifecycle:lifecycle-runtime-ktx:2.7.0`
  - `androidx.activity:activity-compose:1.8.2`
  - `androidx.compose.ui:ui:1.6.2`
  - `androidx.compose.ui:ui-graphics:1.6.2`
  - `androidx.compose.ui:ui-tooling-preview:1.6.2`
  - `androidx.compose.material3:material3:1.2.0`
  - `androidx.compose.material:material-icons-extended:1.6.2`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`

- [ ] **Step 3: Test compilation**
  Run `./gradlew compileReleaseJavaWithJavac` and `./gradlew compileReleaseKotlin` to verify the build setup passes.

- [ ] **Step 4: Commit changes**
  `git commit -m "build(gradle): configure Kotlin 1.9.22 and Jetpack Compose dependencies"`

---

### Task 2: Native Color Science Engine & 42 Preset Catalog

**Files:**
- Create: `app/src/main/java/app/looklite/android/model/PresetModels.kt`
- Create: `app/src/main/java/app/looklite/android/data/PresetCatalog.kt`
- Create: `app/src/main/java/app/looklite/android/engine/LutLoader.kt`

- [ ] **Step 1: Define Kotlin data models for presets and sliders**
  Define `CameraBrand`, `PresetProfile`, `SliderValues`, `ColorBalance`, `HslShift`, `HslVector`, and `SliderRanges`.

- [ ] **Step 2: Populate all 42 profiles in `PresetCatalog.kt`**
  Port the 42 profiles from `web/app.js` and `scripts/verify-filters.js`:
  - 8 Poles (Fnatix Flash, Korean Porcelain, Emerald Moody, Night Ocean, Pro-Mist Dream, Studio Flash, Golden Radiance, Dark Aesthetic)
  - 10 Fujifilm (Classic Chrome, Classic Neg, Velvia 50, Provia 100F, Astia, Reala Ace, Eterna, Nostalgic Neg, Acros, Bleach Bypass)
  - 9 Kodak (Portra 400, Portra 160, Portra 800, Gold 200, Ektar 100, Kodachrome 64, CineStill 800T, Vision3 500T, Tri-X 400)
  - 6 Leica (Classic, Natural, Vivid, Teal, Monochrom, Greg Williams)
  - 4 Hasselblad (HNCS Standard, X2D Portrait, Nordic Nature, MF Monochrom)
  - 5 Ilford & Vintage (HP5 Plus, Disposable 400, Polaroid Color, Golden Light, Street Car)

- [ ] **Step 3: Implement `LutLoader.kt` for native 17³ 3D LUT buffers**
  Reads binary LUT data from assets (`assets/luts/<id>.bin` or `.cube`) into a direct `ByteBuffer` ready for `GLES30.glTexImage3D`.

- [ ] **Step 4: Verify preset catalog and LUT loader**
  Write a Kotlin unit test verifying that all 42 LUT assets exist, load without error, and contain exactly $17^3 \times 3 = 14,739$ bytes.

- [ ] **Step 5: Commit changes**
  `git commit -m "feat(engine): implement native preset catalog and 17³ 3D LUT loader"`

---

### Task 3: Native OpenGL ES 3.0 Shader Renderer (`LookLiteRenderer.kt`)

**Files:**
- Create: `app/src/main/assets/shaders/looklite_native.vert`
- Create: `app/src/main/assets/shaders/looklite_native.frag`
- Create: `app/src/main/java/app/looklite/android/engine/LookLiteRenderer.kt`
- Create: `app/src/main/java/app/looklite/android/engine/LookLiteGLView.kt`

- [ ] **Step 1: Write native GLSL 3.0 ES shaders**
  Port vertex and fragment shaders to `#version 300 es` with support for `sampler3D uLut`, exposure, contrast, highlights/shadows, color balance, 8-channel HSL vectors, clarity, glow, skin smooth/radiance, film grain, and split-screen before/after comparison.

- [ ] **Step 2: Implement `LookLiteRenderer.kt`**
  Implements `GLSurfaceView.Renderer`:
  - `onSurfaceCreated`: Compiles shader program, sets up full-screen quad VAO/VBO, creates image texture (`GLES30.GL_TEXTURE0`) and 3D LUT texture (`GLES30.GL_TEXTURE1`).
  - `onSurfaceChanged`: Sets viewport and aspect ratio.
  - `onDrawFrame`: Binds textures, pushes slider uniforms with intensity scaling, and renders quad.
  - Method `setImageBitmap(bitmap: Bitmap)`: Uploads bitmap to `GLES30.GL_TEXTURE_2D`.
  - Method `setLutBuffer(buffer: ByteBuffer, size: Int)`: Updates 3D LUT via `GLES30.glTexImage3D`.
  - Method `renderOffscreen(targetWidth: Int, targetHeight: Int): Bitmap`: Renders to an offscreen FBO for high-res JPEG export.

- [ ] **Step 3: Create `LookLiteGLView.kt`**
  A customized `GLSurfaceView` configured for OpenGL ES 3.0 with `RENDERMODE_WHEN_DIRTY` for optimal battery life and responsiveness.

- [ ] **Step 4: Verify GL compilation & offscreen rendering**
  Run test build and verify OpenGL shader links cleanly.

- [ ] **Step 5: Commit changes**
  `git commit -m "feat(renderer): implement native OpenGL ES 3.0 shader pipeline and offscreen FBO exporter"`

---

### Task 4: Apple HIG Jetpack Compose UI Components

**Files:**
- Create: `app/src/main/java/app/looklite/android/ui/theme/Theme.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/LookLiteHeader.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/AspectRatioBar.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/CropOverlay.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/SubMenuCard.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/FloatingDockBar.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/ExportDialog.kt`
- Create: `app/src/main/java/app/looklite/android/ui/components/DiagnosticDialog.kt`

- [ ] **Step 1: Theme & Visual Tokens (`Theme.kt`)**
  Define Apple HIG Light Liquid Glass color scheme:
  - Background: `#F8F8FA`
  - Glass surface: `Color(0xE6FFFFFF)`
  - Active Coral: `Color(0xFFFF2D55)`
  - Text primary: `Color(0xFF1D1D1F)`, secondary: `Color(0xFF86868B)`
  - Rounded corners: Header `12.dp`, SubMenu `20.dp`, DockBar `9999.dp`

- [ ] **Step 2: Boxy Modern Header (`LookLiteHeader.kt`)**
  Rectangular box with `12.dp` radius containing:
  - LookLite brand + active preset badge
  - `Open` button (launches Android native PhotoPicker)
  - `Reset` button (resets intensity and deltas)
  - `Bug/Diagnostic` button (opens diagnostic dialog)
  - `Save` button (opens export resolution sheet)

- [ ] **Step 3: Aspect Ratio Bar & Interactive Crop Overlay (`AspectRatioBar.kt`, `CropOverlay.kt`)**
  - Ratio chips bar sitting right above preview: `Asli`, `1:1`, `4:5`, `9:16`, `16:9`, `3:2`, `2:3`.
  - Interactive crop frame with Rule-of-Thirds grid lines and draggable framing via Compose `pointerInput`.

- [ ] **Step 4: Sub-Menu Controls Card (`SubMenuCard.kt`)**
  Separated floating card (`20.dp` radius) situated above bottom dock:
  - Presets Mode: Brand chips carousel (`POLES`, `FUJI`, `KODAK`, `LEICA`, `HASSELBLAD`, `ILFORD`), Filter Intensity slider (0–100%), squircle preset cards.
  - Fine-Tune Mode: Category tabs (`Light`, `Color`, `Poles`, `Grain`), slider rows with `[-]`, slider bar, `[+]`, value display.
  - Collapsible via grabber or active tab toggle.

- [ ] **Step 5: Floating Navigation Pill (`FloatingDockBar.kt`)**
  Floating capsule pill (`9999.dp` radius) detached at the bottom:
  - Tabs: `Presets`, `Fine-Tune`, `Split View` (toggles split divider), `Before` (press-and-hold compare).

- [ ] **Step 6: Export & Diagnostic Dialogs**
  - Resolution selector: `Original`, `4K`, `Social (2048px)`, `Full HD (1080px)`.
  - Diagnostic dialog: Shows device specs, GL vendor, texture limits, and copyable debug log.

- [ ] **Step 7: Commit UI components**
  `git commit -m "feat(ui): implement native Apple HIG Compose components, ratio bar, crop overlay, and floating dock"`

---

### Task 5: Main Activity Assembly & Media Management

**Files:**
- Rewrite: `app/src/main/java/app/looklite/android/MainActivity.kt` (replaces `MainActivity.java`)
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Wire Native Photo Picker & State Management**
  Use `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` in `MainActivity.kt`.
  Load selected `Uri` directly using `contentResolver.openInputStream(uri)` and `BitmapFactory.decodeStream` with proper EXIF orientation handling.

- [ ] **Step 2: Connect Compose UI to `LookLiteRenderer`**
  Embed `LookLiteGLView` via `AndroidView` inside Compose layout.
  Bind slider states, intensity, brand selection, and active preset changes directly to the renderer.

- [ ] **Step 3: Implement High-Res Export to Gallery**
  When export is confirmed, crop the source bitmap to the selected aspect ratio and framing offsets, pass to `renderOffscreen`, and save output JPEG to `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` under `Pictures/LookLite`.

- [ ] **Step 4: Remove obsolete WebView and Java files**
  Delete old `MainActivity.java` and clean unused WebView assets from the Android project.

- [ ] **Step 5: Commit changes**
  `git commit -m "feat(app): assemble 100% native Kotlin MainActivity with Compose and OpenGL ES 3.0"`

---

### Task 6: End-to-End Build, Verification & Release

**Files:**
- Project-wide verification

- [ ] **Step 1: Build release APK with Gradle**
  Run `JAVA_HOME=/home/soju/.local/jdk-17 ANDROID_HOME=/home/soju/.local/android-sdk ./gradlew assembleRelease`.
  Verify successful build without errors or warnings.

- [ ] **Step 2: Verify APK size and package structure**
  Check `app/build/outputs/apk/release/LookLite-release.apk`.
  Verify DEX contains Kotlin and Compose classes, zero WebView runtime overhead.

- [ ] **Step 3: Verify all 42 color presets & features**
  Ensure all 42 profiles render accurately with their real 17³ 3D LUTs.

- [ ] **Step 4: Push to GitHub**
  Commit all changes and push to `origin main`.
