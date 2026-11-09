# Tahap 3–4 — Arsitektur LookLite & UI Pipeline

## System architecture (Lite)

```
┌──────────────────────────────────────────────────────────┐
│                     Presentation (UI)                    │
│  CanvasView │ BrandCarousel │ AdjustSheet │ BeforeHold   │
└─────────────┬────────────────────────────▲───────────────┘
              │ gestures / slider events   │ preview frames
┌─────────────▼────────────────────────────┴───────────────┐
│                 EditingSession (state)                   │
│  originalUri, activePresetId, baseSliders, userDeltas,   │
│  composedParams, history (optional undo stack)           │
└─────────────┬────────────────────────────────────────────┘
              │ ComposedParams + LUT handle
┌─────────────▼────────────────────────────────────────────┐
│              RenderEngine (GPU)                          │
│  TextureCache │ ShaderPipeline │ PreviewFBO │ Exporter   │
└─────────────┬────────────────────────────────────────────┘
              │
┌─────────────▼────────────────────────────────────────────┐
│  Assets: presets/*.json + luts/*.cube|hald.png           │
└──────────────────────────────────────────────────────────┘
```

### Non-destructive data flow

```
Original_Image
    → Decode_to_GPU_Texture (immutable source)
    → Apply_Preset(Base_LUT + default_sliders)   // sets absolute baseline
    → User_Adjustments(slider_deltas)            // compose → uniforms
    → Canvas_Preview (screen-res FBO)
    → Export_Bake (full-res, optional higher LUT quality)
```

### Reset & User Recipes

| Action | Behavior |
|---|---|
| **Reset Adjust** | `userDeltas = 0`; keep preset + LUT |
| **Reset All** | Clear preset → neutral identity LUT + zero sliders |
| **Save Recipe** | New JSON: current absolute params + same `lut_asset_path` + `parent_preset_id` |
| **Switch Preset** | Replace LUT + `default_sliders`; wipe `userDeltas` (or offer “Keep adjustments”) |

---

## Mobile rendering stack recommendation

| Layer | Recommendation | Why |
|---|---|---|
| **Cross-platform core** | **Filament** or custom **OpenGL ES 3.0 / Metal** fragment | Direct LUT 3D + full control |
| **React Native / Flutter bridge** | Skia (Impeller) *or* native TextureView/MTKView | Good for UI; custom shader for color |
| **Avoid** | Legacy RenderScript | Deprecated on Android |
| **Avoid as sole engine** | Pure CPU (Bitmap+Canvas) | Cannot hit 60 FPS slider feel |
| **Grain** | Procedural in shader (hash/simplex) | No noise texture thrashing |
| **Export** | Same shader at full res → JPEG/HEIC encode | WYSIWYG |

**Practical default for greenfield:**

- **iOS:** Metal fragment + `MTKView`  
- **Android:** GLES 3.1 / Vulkan via Filament or custom GL  
- **Shared look math:** GLSL → transpile / dual-author MSL  

Preview strategy: one **uber-shader** (tone → color → LUT → vignette → grain) to minimize bandwidth.

---

## UI/UX flow (minimal)

### Screen composition (single activity / route)

1. **Canvas** — full viewport; pinch zoom / pan; long-press = Before (bypass user+preset LUT → show original or pre-preset).  
2. **Preset bar** — horizontal carousel grouped: Fuji · Kodak · Leica · Hasselblad · Sony · My Recipes.  
3. **Adjust sheet** — revealed only on **Adjust** tab; three groups:  
   - **Light:** Exp, Contrast, Hi/Sh, Wh/Bk  
   - **Color:** Temp, Tint, Vibrance, Sat, HSL, Balance  
   - **Effects:** Vignette, Grain Intensity/Size  

No dashboard chrome. One job per surface.

### Gesture map

| Gesture | Action |
|---|---|
| Pinch / two-finger pan | Zoom/pan canvas |
| Tap preset chip | Apply Base Look |
| Drag slider | Live uniform update |
| Hold canvas | Before/After |
| Swipe down on sheet | Dismiss Adjust |

---

## Module boundaries (suggested packages)

```
looklite/
  core/          # ComposeParams, ColorMath (CPU reference)
  gpu/           # Shader, FBO, texture upload
  presets/       # Loader, schema validate
  session/       # EditingSession, undo
  ui/            # Canvas, carousel, sheets
  export/        # Full-res bake + encode
```