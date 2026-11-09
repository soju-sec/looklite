# LookLite

**Preset First, Fine-Tune Second** — mobile photo editor architecture for camera/film color looks, reverse-engineered from industry pipelines (Lightroom Mobile / VSCO class) into a non-bloated Lite stack.

## Evidence posture (REA)

| Claim class | Meaning |
|---|---|
| **Inferred** | From public ACR / mobile GPU docs and common editor UX |
| **Reconstructed** | Clean-room Lite architecture (not proprietary assets) |
| **Static (VSCO APK)** | Archive/SQLite/JNI evidence from a supplied APK — see below |

### VSCO APK on disk

Target linked at `targets/VSCO_489.apk` (source: `~/Downloads/VSCO_489_Apkdaddy.apk`).  
Full write-up: [`docs/04-vsco489-rea-evidence.md`](docs/04-vsco489-rea-evidence.md).

**Headline static facts:** SQLite preset catalog (127 looks + 30 tools), encrypted `.xray` cubes, one plaintext **17³** LUT (`handycam85`), GLES3 `KernelRenderer`, `LibColorCubes` + `LibHSL` in `libfragglerock`. APK is an **ApkDaddy repack** (`libapkdaddy.so`) — not Play-authentic.

We do **not** decrypt protected `.xray` / CIP payloads.

## Deliverables

1. [`docs/01-rea-deconstruction.md`](docs/01-rea-deconstruction.md) — target pipeline deconstruction & prune list  
2. [`docs/02-architecture.md`](docs/02-architecture.md) — Lite system architecture & UI flow  
3. [`schemas/`](schemas/) — JSON Schema for presets + editing session state  
4. [`data/color-science-matrix.json`](data/color-science-matrix.json) — approximated camera/film look parameters  
5. [`shaders/looklite.frag`](shaders/looklite.frag) — real-time LUT + slider GPU pipeline  

## Legal

Profile names that match commercial film/camera “looks” are **descriptive targets for approximation**. Ship under original LookLite IDs (`fuji.classic_chrome_approx`, etc.). Do not redistribute proprietary `.cube` / `.xmp` / vendor LUTs.