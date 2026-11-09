# VSCO 489 — Static Evidence Report (REA-style)

**Target:** `/home/soju/Downloads/VSCO_489_Apkdaddy.apk`  
**Linked as:** `targets/VSCO_489.apk`  
**SHA256:** `92c389add235411d658b986b7515a315e74f38d61d2c2cd8c3daf1166b06a5fc`  
**Package size:** ~103 MB  
**Analysis date:** 2026-11-09  
**Authority:** static archive inventory + SQLite parse + native ELF strings/JNI symbols  
**Also performed:** portable JADX 1.5.1 decompile → `targets/vsco489_jadx/`  
**Not performed:** runtime attach, `.xray` / CIP decryption

## Integrity warning

This APK contains `lib/arm64-v8a/libapkdaddy.so` and JNI `Java_com_apkdaddy_B_getTitle`. It is a **third-party repack**, not a Play Store canonical build. Findings about VSCO imaging likely still hold for bundled `libvscocore` / `libfragglerock` / assets, but authenticity is **unverified**. Prefer an official APK for any claim that must bind to VSCO Inc. shipping identity.

## Decision questions → answers

| Q | Finding | Evidence class | Coverage |
|---|---|---|---|
| How are presets represented? | Catalog in SQLite `effects-db` (`VSCO_EFFECT`); payload files `assets/xrays/<KEY>.xray` | **Static** | High for catalog; medium for payload semantics |
| Are looks 3D LUTs? | Yes: **17³** confirmed by two sources: (1) plaintext `unencrypted_cubes/handycam85.xray` (4,913 RGB lines), (2) JADX decompile `LibColorCubes.java:83` verifying buffer capacity `numCubes * 14739` floats ($17^3 \times 3$). | **Static + JADX** | Confirmed 17³ native cube format |
| Slider model? | Tools in same table with `IS_TOOL=1`; several `HAS_TWO_XRAYS=1` (bipolar intensity) | **Static** | High |
| GPU path? | GLES3 (`libGLESv3`), EGL context, `GLKernelRenderer`, FBO/Texture JNI | **Static** (strings/JNI) | High for stack choice; shader source mostly not recovered |
| HSL? | `LibHSL_nGenerateHslLut` / apply in `libfragglerock` | **Static** | Presence confirmed |
| Geometry? | `LibGeometry_*` + crop/straighten/perspective tools | **Static** | Present (LookLite can still cut) |

## Artifact map

```
APK
├── assets/effects-db              SQLite catalog (157 rows)
├── assets/effects.defs            opaque/encrypted blob (not plaintext)
├── assets/xrays/*.xray            269 encrypted look/tool payloads (most)
├── assets/unencrypted_cubes/      rare plaintext LUT (handycam85 = 17³)
├── assets/*slider*.png            UI icons: exposure, contrast, grain, …
├── assets/curated_categories_catalog.json   Popular/Warm/Cool/… mappings
├── lib/*/libvscocore.so           OpenCV + EGL/GLES KernelRenderer + AV
└── lib/*/libfragglerock.so        Color cubes, HSL LUT, CIP encode/decode, geometry
```

### `VSCO_EFFECT` schema (observed)

```sql
KEY, SHORT_NAME, LONG_NAME, COLOR, IS_TOOL, DISPLAY_TOP_LEVEL,
INITIAL_INTENSITY, ORDER, ENABLED, HAS_TWO_XRAYS
```

- **30 tools** (`IS_TOOL=1`): exposure, contrast, saturation, highlights, shadows, wbtemp, wbtint, skin, vignette, grain, fade, sharpen, crop, straighten, perspectives, highlight/shadow tint & hue tools, …
- **127 presets** (`IS_TOOL=0`): coded keys (`c1`, `a6`, `b1`, …) with family labels in `LONG_NAME` (`ANALOG`, `VIBRANT`, `BW CLASSIC`, `PORTRAITS`, …)
- Default preset intensity often **13** (internal scale, not −100..100)
- `HAS_TWO_XRAYS=1` on exposure/contrast/saturation/wbtemp/wbtint/skin and some B&W/brand presets → **two cubes** for negative vs positive intensity axis

Export: `targets/vsco489_evidence/vsco_effect_catalog.csv`

### JADX Bytecode / Java Decompile Confirmation (17³ Cubes)

Decompiled from `classes15.dex` via JADX (`targets/vsco489_jadx/sources/com/vsco/imaging/nativestack/LibColorCubes.java`):

```java
package com.vsco.imaging.nativestack;

public class LibColorCubes extends NativeLibrary {
    public static class ApplyColorCubesCall extends JniCall {
        public FloatBuffer cubes;
        public int numCubes;
        ...
        @Override
        public void checkArguments() {
            ...
            FloatBuffer r0 = this.cubes;
            Preconditions.checkState(r0.isDirect());
            Preconditions.checkArgumentPositive(this.numCubes, "numCubes");
            int capacity = this.cubes.capacity();
            int required = this.numCubes * 14739; // 17 * 17 * 17 * 3 = 14,739 floats
            Preconditions.checkState(capacity >= required);
        }
    }
}
```

**Math breakdown:**
- $17 \times 17 \times 17 = 4,913$ sample lattice vertices.
- $4,913 \times 3\text{ (RGB floats)} = 14,739\text{ floats}$.
- $14,739 \times 4\text{ bytes} = 58,956\text{ bytes}$ per direct `FloatBuffer` cube.
- Confirms why mobile real-time performance on VSCO uses $17^3$ (ultra-light cache footprint ~58 KB per LUT) compared to desktop $33^3$ (~428 KB) or $65^3$ (~3.4 MB). LookLite adopts $17^3$ for mobile ultra-low memory / fast texture upload, while supporting $33^3$ when desktop/tablet GPU bandwidth allows.

## Native imaging stack (from JNI symbols)

### `libfragglerock.so` — color science core

| JNI | Role (inferred from name) |
|---|---|
| `LibColorCubes_nApplyColorCubes` | Apply 3D color cube(s) |
| `LibColorCubes_nConcatColorCubes` | Concatenate cubes (stack looks) |
| `LibColorCubes_nApplyColorCubesInPlace` | In-place apply |
| `LibHSL_nGenerateHslLut` | Bake HSL adjustments into a LUT |
| `LibHSL_nApplyHslToColor` / RGB↔HSL | HSL math |
| `FraggleRock_nEncodeCip` / `nDecodeCip` | Proprietary CIP container codec for cubes |
| `nInterlaceRgb` / `nDeinterlaceRgb` | Cube packing layout |
| `LibGeometry_*` | Transform/crop matrix |
| `NativeSecure_getCryptoKey` / `Nonce` | Crypto material for protected assets |

### `libvscocore.so` — GL + media

- Package namespace: `com.vsco.core.gl.{Context,FrameBuffer,Texture,KernelRenderer}`
- `KernelRenderer_{init,setTexture,renderTo}` → fullscreen quad style renderer
- Links OpenCV (`FilterEngine`, `bilateralFilter`, …) and `libEGL` / `libGLESv3`
- Video composition instructions: `EffectInstruction`, `FadeEffectInstruction`, `TwoToneInstruction`, …

## Preset mechanism vs LookLite

| VSCO (observed) | LookLite (design) |
|---|---|
| Preset KEY → `.xray` CIP/cube + intensity | `preset_id` → `.cube` + `default_sliders` |
| Tools as separate effects with own xrays / bipolar pairs | Parametric uniforms (no per-slider LUT required) |
| `LibColorCubes_nConcatColorCubes` stacks looks | Single base LUT + slider compose |
| HSL baked to LUT (`nGenerateHslLut`) | HSL live in uber-shader (or bake on export) |
| Categories JSON (Popular/Warm/Cool/…) | Brand carousel (Fuji/Kodak/…) |
| Encrypted payloads + NativeSecure | Open assets we author ourselves |

**Validation:** LookLite’s “Base LUT + slider deltas” is a *simplified* form of VSCO’s cube pipeline. VSCO leans harder on **prebaked cubes per tool/preset** and encryption; LookLite should keep parametric tone for Lite speed, and use LUT primarily for Base Look (matching the unencrypted 17³ evidence).

## What we deliberately did not do

- Decrypt `.xray` / `effects.defs` using `NativeSecure` keys (circumvents protection; not needed for architecture).
- Claim exact shader math for grain/fade without decompilation.
- Treat ApkDaddy build as Play-authentic.

## Next probes (optional)

1. Install JADX + `rea inspect-android-package` for Java class graph around `LibColorCubes` / filter apply order.  
2. Official APK comparison (diff asset digests vs this repack).  
3. Runtime only if authorized device + VSCO account: capture GL frame order for preset→tool apply.

## LookLite action items from this target

1. Keep **17³ or 33³** `.cube` as Base Look (VSCO ships 17³ plaintext example).  
2. Model intensity 0..N with optional **bipolar** tool LUTs later; for Lite, parametric ±100 is enough.  
3. Categories can mirror VSCO’s Warm/Cool/Vibrant *UX idea* without cloning their catalog.  
4. Do **not** ship CIP decryptors or ripped VSCO xrays — author original looks.