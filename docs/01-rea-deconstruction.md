# Tahap 1 — Dekonstruksi Sistem Target (REA)

## 1. Investigation framing

**Decision question:** Which rendering stages must LookLite reproduce so that “apply camera look → fine-tune with familiar sliders → export” feels like Lightroom/VSCO class apps, without AI masking, healing, geometry, or cloud sync?

**Target class (desired, not yet supplied):** Android APK / iOS IPA of Lightroom Mobile and VSCO.  
**Authority used in this document:** public pipeline literature + reconstructed clean-room design (**not** binary Evidence).

### Question checklist

| # | Question | Evidence needed | Status |
|---|---|---|---|
| Q1 | Order of tone vs color vs grain in the render graph | Static shader/module graph from APK, or Adobe ACR docs | Inferred from ACR-class pipelines |
| Q2 | How presets serialize (XMP vs LUT vs JSON) | Package inventory of `.xmp` / `.cube` / asset packs | Inferred; VSCO historically packs look assets |
| Q3 | Which ops are GPU vs CPU | Runtime GPU capture / Metal/GL symbol refs | Inferred: LUT+tonal = GPU; export encode = CPU/GPU |
| Q4 | How base look stays locked while sliders move | Param composition model in editor state | Reconstructed for LookLite |
| Q5 | Preview resolution strategy for 60 FPS | Frame timing of preview FBO | Reconstructed (screen-res FBO + dirty uniforms) |

---

## 2. Deconstructed non-destructive pipeline (ACR / Lightroom-class)

Industry-standard **parametric** pipeline (Input → parametric stack → display/export), not pixel-baking until export:

```
┌─────────────┐   decode    ┌──────────────┐
│ Bitmap/JPEG │────────────▶│ Working RGB  │  (sRGB or Display P3 preview)
│ or RAW/DNG  │             │ linear-ish   │  (mobile often stays gamma-encoded
└─────────────┘             │ buffer       │   for preview speed)
                            └──────┬───────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │ 0. Color space / WB (Temp/Tint)│
                    │ 1. Tone: Exposure, Contrast,   │
                    │    Highlights, Shadows,        │
                    │    Whites, Blacks              │
                    │ 2. Tone Curve (RGB / channel)  │
                    │ 3. Color: Vibrance, Saturation │
                    │ 4. HSL / Color Mixer           │
                    │ 5. Color Grading / Balance     │
                    │ 6. Creative LUT / Profile      │  ← “Base Look”
                    │ 7. Detail: Sharpen, NR         │  (Lite: optional later)
                    │ 8. Effects: Grain, Vignette    │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │ GPU Fragment Pass (preview)  │
                    │ Multi-pass or single uber-   │
                    │ shader with LUT 3D texture   │
                    └──────────────┬──────────────┘
                                   │
                         Canvas / Export bake
```

### Lightroom Mobile (behavioral model)

| Stage | Mechanism | LookLite? |
|---|---|---|
| Input | JPEG/HEIC common; RAW via DNG module | JPEG/HEIC first; RAW later |
| Profile / Creative | Camera Matching / Adobe profiles + user presets | **Base LUT + locked color science** |
| Tone | Parametric tone + curves | Keep |
| HSL | Per-hue L/S/H | Keep (8-hue or 6-hue) |
| Color grading | Shadows/Midtones/Highlights wheels | Simplify → Color Balance 3-way |
| Grain | Luma/chroma noise model | Keep (simplex/blue-noise) |
| Masking / AI | Subject, sky, brush | **Eliminate** |
| Healing | Content-aware patch | **Eliminate** |
| Geometry | Upright, transform | **Eliminate** |
| Cloud / sync | Creative Cloud catalog | **Eliminate** |
| Catalog DB | Multi-photo library | Optional thin “Recents” only |

### VSCO (behavioral model)

- **Preset-first UX**: looks carousel dominates; adjust is secondary — this is LookLite’s product north star.
- Presets historically behave like **baked look recipes** (tone + color + grain + sometimes fade), closer to film stocks than ACR “technical” profiles.
- Fine-tune often exposes a **subset** of parameters derived from the look (not the full ACR surface).

---

## 3. Essential vs eliminated components

### Keep (essential Lite core)

1. **Exposure / Contrast / Highlights / Shadows / Whites / Blacks**
2. **Temp / Tint** (WB)
3. **Vibrance / Saturation**
4. **RGB Tone Curve** (global; optional channel later)
5. **HSL shifts** (primary hues)
6. **Color Balance** (shadows / midtones / highlights RGB bias)
7. **3D LUT** (Base Look)
8. **Vignette + Film Grain**

### Cut (complexity / bloat)

- AI / radial / brush / luminance / color-range masks  
- Healing / clone / generative fill  
- Lens corrections / transform / crop-as-geometry suite (simple crop/rotate OK)  
- Cloud catalogs, collaborative albums, marketplace DRM packs  
- Full Adobe Camera Matching matrix (use our own LUTs)  
- Multi-layer local adjustments  

---

## 4. Preset mechanism — technical map

### Observed industry representations

| Format | Role | Notes |
|---|---|---|
| **`.cube` 3D LUT** | Dense color remapping | Portable; 17³–33³ typical for mobile |
| **HaldCLUT PNG** | LUT encoded as image | Easy asset pipeline; sample in shader |
| **XMP / sidecar** | Parametric deltas (ACR) | Great for non-destructive; heavy surface |
| **JSON recipe** | App-owned slider + LUT refs | Best for Lite control & versioning |
| **VSCO look packs** | Opaque asset bundles | Treat as black box without REA target |

### LookLite locked “Base Look” model

```
FinalParams = Compose(
  BaseLook.LUT,                 // immutable identity of the preset
  BaseLook.default_sliders,     // starting point shown on dials
  User.slider_deltas            // additive / multiplicative deltas
)
```

**Lock rule:** Changing sliders never swaps or rewrites the LUT. Reset restores `default_sliders` but keeps the same `lut_asset_path`. Saving a **User Recipe** clones LUT ref + current absolute slider values into a new `preset_id`.

**Dynamic range preservation:** Tone ops run in a working space *before or after* LUT depending on look design:

- **Film-stock presets:** LUT after gentle tone (stock “prints” the graded image) — default LookLite order.  
- **Camera-profile presets:** optional LUT-as-matrix early (color science) then user tone — flagged in schema `lut_stage: "early" | "late"`.

---

## 5. Performance dependencies (preview)

| Cost driver | Mitigation |
|---|---|
| Full-res 3D LUT sample | 17³ or 33³ texture; tetrahedral interp only on export |
| Multi-pass blur for grain | Single-pass procedural grain in fragment shader |
| Slider thrash | Dirty-uniform updates; no texture re-upload |
| 4K source | Preview FBO = screen pixel size; full-res on export only |
| CPU color math | Keep stack in one Metal/GLES fragment (or 2-pass: tone→LUT) |

**Target:** ≥60 FPS on mid-range SoC at viewport resolution with continuous slider drag.