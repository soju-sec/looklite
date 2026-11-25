# LookLite — Darkroom Studio & 3D LUT Color Grading for Android

[![Android](https://img.shields.io/badge/Android-8.0%20--%2017%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-3.0-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.khronos.org/opengles/)
[![Author](https://img.shields.io/badge/Author-soju--sec-blue?style=for-the-badge)](https://github.com/soju-sec)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

A native Android **Darkroom Studio** photo color-grading application built with **Kotlin**, **Jetpack Compose**, and **OpenGL ES 3.0**. Designed around a **Preset First, Fine-Tune Second** workflow for real-time 60 FPS non-destructive 3D LUT processing, custom look recipes, and full-resolution GPU offscreen export.

---

## Key Features

- **Native OpenGL ES 3.0 GPU Pipeline**:
  - Hardware **17³ 3D LUT (`sampler3D`)** trilinear interpolation executed directly on the GPU.
  - Configurable LUT stage placement (`early` or `late`) relative to tone and color matrices.
  - High-resolution **Offscreen FBO Export** saved directly to Android `MediaStore` (`Pictures/LookLite/`).
- **Auto Tone (Histogram & Color Cast Correction)**:
  - Fast per-image RGB percentile analysis (**2%–98% Auto Levels**) and **Gray-World White Balance** neutralization executed on GPU before preset grading.
- **50 Built-In Film & Color Looks (7 Categories)**:
  - **Kodak** (9): *Portra 400, Portra 160, Portra 800, Gold 200, Ektar 100, Kodachrome 64, CineStill 800T, Vision3 500T, Tri-X 400*
  - **Fuji** (10): *Classic Chrome, Classic Neg, Velvia 50, Provia 100F, Astia, Reala Ace, Eterna, Nostalgic Neg, ACROS, Bleach Bypass*
  - **Leica** (6): *Classic, Natural, Vivid, Teal, Monochrom, Greg Williams*
  - **Hasselblad** (4): *HNCS Standard, HNCS Portrait, HNCS Landscape, HNCS Monochrome*
  - **Ilford / Vintage** (5): *HP5 Plus, Disposable 400, Polaroid Color, Golden Light, Street Car*
  - **Retouch** (8): *Fnatix Moody Flash, Korean Porcelain, Emerald Moody, Night Ocean, Pro-Mist Dream, Studio Flash, Golden Radiance, Dark Aesthetic*
  - **Beauty** (8): *Cream Skin, Cool Porcelain, Ziran Natural, Pearl Violet, Peach Milk, Airy Fresh, Glass Sea, Soft Dim*
- **Darkroom Studio Interface**:
  - **Live-Filtered Preset Thumbnails**: Real-time `68dp × 88dp` LUT previews rendered from the currently loaded photo.
  - **Floating Canvas Inspector**: Translucent overlay pill for interactive **Aspect Ratio Crop** (`Original`, `1:1`, `4:5`, `9:16`, `16:9`, `3:2` with Rule-of-Thirds grid), **Split View** (`BEFORE` / `AFTER` draggable divider), and **Hold for Original**.
  - **Center-Detent Fine-Tune Sliders**: Borderless sliders with haptic center-detent at `0`, double-tap label reset, and tabular monospace (`tnum`) readouts across **Light**, **Color**, **Retouch**, and **Grain / FX**.
- **Custom User Recipes & Frame Templates (`DataStore`)**:
  - **My Looks**: Save active LUT + intensity + all slider adjustments as named custom presets, with long-press Rename, Overwrite, and Delete.
  - **Frame & Export Templates**: Add Classic White, Darkroom Black, or Film Polaroid borders with adjustable padding and custom signature captions, and save reusable one-tap frame templates.
- **Instant Localization & Studio Settings**:
  - Supports **System**, **English**, and **Indonesia** via `LocaleManager` / `AppCompatDelegate` without restarting the activity.
  - Hidden **Developer Diagnostics** unlocked by tapping the app version 5 times in Settings (GPU frame time, active 3D LUT, canvas resolution, and debug report console).

---

## Shader Pipeline & Data Schema

### 1. Fragment Shader Order (`looklite_native.frag`)

```text
Input Texture (uImage)
  ├── [uShowBefore == 1 / vUv.x < uSplitPos] ──▶ Output Original
  ▼
1. applyAutoColor()         ── Auto Levels (2%-98%) + Gray-World WB + Subtle Vibrance
2. applySkinPolish()        ── Surface-aware skin smoothing & luminance radiance
3. applyWB()                ── Temperature & Tint matrix
4. [uLutStage == 0 ?]
     ├─ Early: applyLut() ─▶ toneBlock() ─▶ colorBlock()
     └─ Late:  toneBlock() ─▶ colorBlock() ─▶ applyLut()
5. applyClarityGlowSharp()  ── Local midtone contrast, soft bloom, unsharp mask
6. vignette()               ── Radial edge falloff
7. filmGrain()              ── Procedural organic grain (uGrainIntensity, uGrainSize)
  ▼
Output FragColor (Screen / Offscreen FBO)
```

### 2. Preset Catalog Schema (`app/src/main/assets/presets.json`)

```json
{
  "preset_id": "beauty.cream_skin",
  "camera_system": "beauty",
  "look_name": "Cream Skin",
  "display_label": "Cream Skin",
  "tag": "CREAM",
  "lut_asset_path": "luts/meitu.cream_skin.cube",
  "lut_stage": "early",
  "description": "Signature creamy pastel skin polish with lifted shadow floor and luminous soft glow.",
  "slider_ranges": {
    "exposure": { "min": -3, "max": 3 },
    "contrast": { "min": -100, "max": 100 },
    "skin_smooth": { "min": 0, "max": 100 }
  },
  "default_sliders": {
    "exposure": 0.15,
    "contrast": -8,
    "highlights": -12,
    "shadows": 18,
    "whites": 10,
    "blacks": 8,
    "temp": 4,
    "tint": 6,
    "vibrance": 4,
    "saturation": -6,
    "vignette": 0,
    "grain_intensity": 0,
    "grain_size": 10,
    "skin_smooth": 55,
    "skin_radiance": 45,
    "clarity": -6,
    "glow": 25,
    "sharpness": 10,
    "hsl": {}
  }
}
```

### 3. 3D LUT Format (`.cube`)

All LUT files in `app/src/main/assets/luts/` use standard **17³** `.cube` format (`4913` RGB float entries in `0.0..1.0`):

```text
TITLE "Cream Skin"
LUT_3D_SIZE 17
DOMAIN_MIN 0.0 0.0 0.0
DOMAIN_MAX 1.0 1.0 1.0
```

---

## Installation & Usage

### 1. Install APK
Download the latest signed APK from [Releases](../../releases) or install `LookLite-release.apk` via ADB:

```bash
adb install -r LookLite-release.apk
```

### 2. Build from Source
Requires **JDK 17** and **Android SDK (API 34)**:

```bash
./gradlew assembleRelease
```

The output APK is generated at `app/build/outputs/apk/release/LookLite-release.apk`.

---

## Repository Structure

```text
├── app/src/main/
│   ├── AndroidManifest.xml          # App manifest & locale configuration
│   ├── assets/
│   │   ├── luts/                    # 50+ 3D LUT .cube files (17³)
│   │   ├── shaders/                 # GLSL ES 3.0 vertex & fragment shaders
│   │   └── presets.json             # Preset catalog & default parameters
│   ├── java/app/looklite/android/
│   │   ├── MainActivity.kt          # Darkroom Studio UI, crop, export, localization
│   │   ├── data/                    # PresetCatalog & StudioPreferences (DataStore)
│   │   ├── engine/                  # LookLiteGLView, LookLiteRenderer, LutLoader, Thumbnails
│   │   ├── model/                   # Data models & enums
│   │   └── ui/                      # Compose components & Darkroom Studio theme
│   └── res/                         # Adaptive icon, EN/ID string resources, styles
├── schemas/                         # Formal JSON schemas
├── LookLite-release.apk             # Signed release APK
├── LICENSE                          # MIT License
└── README.md                        # Project documentation
```

---

## Author

- Developer: **soju-sec** ([@soju-sec](https://github.com/soju-sec))

---

## License

This project is distributed under the [MIT](LICENSE) License.
