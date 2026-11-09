# Tahap 2 — Color Science Summary

Numeric defaults live in [`../data/color-science-matrix.json`](../data/color-science-matrix.json). All IDs use `*_approx` suffixes — artistic approximations, not vendor LUTs.

## Quick matrix

| System | Look | Contrast | Sat / Mono | Grain | Signature |
|---|---|---|---|---|---|
| Fujifilm | Classic Chrome | hard_shadows | sat −18 | fine 12 | Deep sky blues, muted chroma |
| Fujifilm | Velvia 50 | punchy | sat +30 | fine 6 | Vivid landscape RGB |
| Fujifilm | Provia | balanced | sat 0 | min 4 | True-to-life, early LUT |
| Fujifilm | Astia | soft | sat −5 | soft 8 | Gentle highlight skin |
| Fujifilm | ACROS | punchy | mono | structured 35 | Highlight≠shadow grain |
| Kodak | Portra 160 | soft | sat −8 | ultra-fine 10 | Warm cream skin |
| Kodak | Portra 400 | balanced | sat −5 | organic 22 | Cream + visible grain |
| Kodak | Gold 200 | balanced | sat +12 | consumer 28 | Amber/yellow nostalgia |
| Kodak | Ektar 100 | micro_contrast | sat +22 | ultra-fine 5 | Punchy reds/blues |
| Kodak | Tri-X 400 | punchy | mono | coarse 55 | Deep blacks |
| Leica | M System | micro_contrast | sat −5 | micro 8 | Optical separation |
| Leica | CL Monochrom | soft | mono | vintage 25 | Creamy mid roll-off |
| Leica | Street Look | balanced | sat −15 | street 18 | Muted earthy greens |
| Hasselblad | HNCS | soft | sat −3 | none | Neutral, smooth highlights |
| Sony | FL | matte | sat −12 | cinema 20 | Matte blacks, cyan hi |
| Sony | IN | soft | sat −10 | instant 30 | Low contrast pastel |
| Sony | S-Cinetone | balanced | sat −8 | light 12 | Soft cinematic mids |

## Locking Base Look

1. Ship a dedicated 3D LUT per look (identity of the preset).  
2. Put characteristic HSL + balance into `default_sliders` (editable).  
3. Optionally set `slider_ranges` so users cannot push sat/contrast far enough to erase the look.  
4. `lut_stage: "early"` for camera-profile science (Provia, HNCS, Leica M); `"late"` for film-print stocks.