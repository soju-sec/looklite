# LookLite

**LookLite** adalah aplikasi *photo color-grading* Android native berbasis **Kotlin**, **Jetpack Compose**, dan **OpenGL ES 3.0** dengan filosofi **Preset First, Fine-Tune Second** — ringan, real-time 60 FPS, dan non-destruktif.

---

## Fitur Utama

### 1. Mesin Rendering GPU Native (OpenGL ES 3.0)
- **3D LUT Hardware Trilinear (`sampler3D`):** Mendukung profil LUT `.cube` **17³** dengan interpolasi trilinear langsung di GPU.
- **Stage Placement (`early` / `late`):** Preset dapat mengeksekusi 3D LUT sebelum *tone/color matrix* (mengunci *base look*) atau sesudahnya (film *print* look).
- **Real-Time Non-Destructive Pipeline:** Seluruh penyesuaian dihitung per-frame tanpa mengubah piksel sumber.
- **Offscreen FBO High-Res Export:** Ekspor resolusi penuh via *Framebuffer Object* (FBO) langsung ke Android `MediaStore` (`Pictures/LookLite/`).

### 2. Koreksi Warna Otomatis (*Auto Color*)
- Analisis histogram RGB (persentil **2%–98%** *Auto Levels*) dan estimasi *Gray-World White Balance* saat foto dimuat.
- Toggle satu ketukan (**Koreksi Warna**) di panel preset untuk menetralkan *color cast* dan menyeimbangkan rentang dinamis sebelum filter diterapkan.

### 3. Katalog 50 Preset & 7 Kategori
- **POLES** (8 preset): *Fnatix Moody Flash, Korean Porcelain, Emerald Moody, Night Ocean, Pro-Mist Dream, Studio Flash, Golden Radiance, Dark Aesthetic*
- **BEAUTY** (8 preset): *Cream Skin, Cool Porcelain, Ziran Natural, Pearl Violet, Peach Milk, Airy Fresh, Glass Sea, Soft Dim*
- **FUJI** (10 preset): *Classic Chrome, Classic Neg, Velvia 50, Provia 100F, Astia, Reala Ace, Eterna, Nostalgic Neg, ACROS, Bleach Bypass*
- **KODAK** (9 preset): *Portra 400, Portra 160, Portra 800, Gold 200, Ektar 100, Kodachrome 64, CineStill 800T, Vision3 500T, Tri-X 400*
- **LEICA** (6 preset): *Classic, Natural, Vivid, Teal, Monochrom, Greg Williams*
- **HASSELBLAD** (4 preset): *HNCS Standard, HNCS Portrait, HNCS Landscape, HNCS Monochrome*
- **ILFORD / VINTAGE** (5 preset): *HP5 Plus, Disposable 400, Polaroid Color, Golden Light, Street Car*

### 4. Fine-Tune & Detail Controls
- **Light:** Exposure (`-3..3`), Contrast, Highlights, Shadows, Whites, Blacks (`-100..100`)
- **Color:** Temperature, Tint, Vibrance, Saturation (`-100..100`)
- **Poles / Portrait:** Skin Smooth, Skin Radiance, Glow (`0..100`), Clarity (`-100..100`)
- **Grain / FX:** Sharpness, Vignette, Grain Intensity (`0..100`), Grain Size (`1..100`)
- **HSL & Color Balance:** 8-channel HSL (*Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta*) dan 3-way *Color Balance* (*Shadows, Midtones, Highlights*).

### 5. Crop Aspek Rasio, Split View & Ekspor
- **Aspect Ratio Bar + Interactive Crop:** `Asli`, `1:1 Persegi`, `4:5 Feed IG`, `9:16 Story`, `16:9 Cinema`, `3:2 Foto` dengan grid *Rule-of-Thirds* yang dapat digeser.
- **Split View (Sebelum / Sesudah):** Garis pemisah interaktif yang dapat digeser langsung pada foto.
- **Before Hold:** Tekan & tahan tombol *Before* untuk membandingkan foto asli secara instan.
- **Pilihan Resolusi Ekspor:** Ukuran Asli, `4K Ultra HD (3840px)`, `Media Sosial (2048px)`, `Full HD (1080px)`.

---

## Arsitektur Pipeline Shader (`looklite_native.frag`)

Urutan pemrosesan per-piksel pada OpenGL ES 3.0 (`#version 300 es`):

```text
Input Texture (uImage)
  ├── [uShowBefore == 1 / vUv.x < uSplitPos] ──▶ Output Original
  ▼
1. applyAutoColor()         ── Auto Levels (2%-98%) + Gray-World WB + Subtle Vibrance
2. applySkinPolish()        ── Surface-aware skin smoothing & skin luminance radiance
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

---

## Skema Data & Preset

### 1. Skema Preset (`app/src/main/assets/presets.json`)

```json
{
  "preset_id": "meitu.cream_skin",
  "camera_system": "meitu",
  "look_name": "Cream Skin",
  "display_label": "Cream Skin",
  "tag": "CREAM",
  "lut_asset_path": "luts/meitu.cream_skin.cube",
  "lut_stage": "early",
  "description": "Creamy pastel skin polish with lifted shadow floor and luminous soft glow.",
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

### 2. Format 3D LUT (`.cube`)

Setiap file LUT di `app/src/main/assets/luts/` menggunakan format standar `.cube` **17³** (`4913` entri RGB float `0.0..1.0`):

```text
TITLE "Cream Skin"
LUT_3D_SIZE 17
DOMAIN_MIN 0.0 0.0 0.0
DOMAIN_MAX 1.0 1.0 1.0
```

### 3. Model State Kotlin (`PresetModels.kt`)

- `CameraBrand` — enum kategori preset (`POLES`, `BEAUTY`, `FUJIFILM`, `KODAK`, `LEICA`, `HASSELBLAD`, `VINTAGE`)
- `PresetProfile` — metadata preset, path LUT, `lutStage`, `defaultSliders`, dan warna *swatch*
- `SliderValues` — seluruh parameter *tone*, *color*, *skin/FX*, `HslChannels`, dan `ColorBalance`
- `AspectRatioOption` — konfigurasi rasio *crop* (`id`, `label`, `badge`, `ratio`)

> Referensi JSON Schema formal tersedia di direktori [`schemas/`](schemas/).

---

## Struktur Proyek

```text
app/src/main/
├── assets/
│   ├── luts/                      # 50+ file 3D LUT .cube (17³)
│   ├── shaders/
│   │   ├── looklite_native.vert   # Vertex shader (orientasi UV Android Bitmap)
│   │   └── looklite_native.frag   # Fragment shader pipeline lengkap
│   └── presets.json               # Katalog 50 preset
├── java/app/looklite/android/
│   ├── MainActivity.kt            # Compose UI, Photo Picker, Crop, Export MediaStore
│   ├── data/PresetCatalog.kt      # Loader katalog preset, swatch, & rasio aspek
│   ├── engine/
│   │   ├── LookLiteGLView.kt      # GLSurfaceView wrapper (EGL ES 3.0)
│   │   ├── LookLiteRenderer.kt    # Renderer, Auto Color analyzer, & Offscreen FBO export
│   │   └── LutLoader.kt           # Parser .cube ke ByteBuffer 3D RGB8
│   ├── model/PresetModels.kt      # Data classes & enums
│   └── ui/
│       ├── components/            # LookLiteHeader, SubMenuCard, FloatingDockBar, CropOverlay, Dialogs
│       └── theme/Theme.kt         # Tema Apple Glass & Coral
└── res/                           # Adaptive Icon & resources
```

---

## Build APK

Membutuhkan **JDK 17** dan **Android SDK (API 34)**:

```bash
./gradlew assembleRelease
```

Output APK tersedia di `app/build/outputs/apk/release/LookLite-release.apk`.
