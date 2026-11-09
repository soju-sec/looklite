# GPU Pipeline Logic

## Pass graph (preview)

```
Source Texture (full decode, cached)
        │
        ▼
 Preview FBO (screen pixel size, color-managed)
        │
        ▼
 LookLite Fragment (uber-shader)
   uniforms ← composed_sliders (dirty on slider move only)
   uLut     ← preset 3D texture (swap on preset change only)
        │
        ▼
 MTKView / GLSurfaceView present
```

## Why one pass

- Mid-range mobile GPUs are bandwidth-bound. Multiple fullscreen blits for each slider family cost more than ALU for this math.
- Split to **2 passes** only if LUT is early *and* you need intermediate blur for premium grain (LookLite skips that).

## 60 FPS contract

1. Upload source texture once per image.  
2. Upload LUT texture once per preset.  
3. On slider drag: update uniforms + `glDrawArrays` / Metal encode — no realloc.  
4. Debounce export: full-res FBO only on share/save.  
5. Optional: drop grain while dragging (`uGrainIntensity=0`), restore on release.

## LUT assets

- Prefer **33³ `.cube`** → convert offline to `GL_RGB8` / `MTLPixelFormatRGBA8Unorm` 3D texture.  
- HaldCLUT PNG: decode to 3D on load (same GPU path).  
- Neutral identity LUT shipped for “no preset” mode.

## Before / After

`uShowBefore=1` short-circuits to source sample (hold gesture). Alternative: second FBO of original for wipe slider.