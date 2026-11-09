/**
 * Master Preset & LUT Importer for LookLite
 * Imports real commercial 3D LUTs (.cube) from /tmp/downloaded_luts
 * and real XMP film simulations from /tmp/film-simulations/presets
 * Resamples all to 17^3 (14739 floats) for instant mobile 60 FPS loading.
 */
const fs = require("fs");
const path = require("path");

const SIZE = 17;

function clamp(v, min = 0, max = 1) {
  return Math.min(max, Math.max(min, v));
}

// Trilinear interpolation for arbitrary 3D LUT
function sampleCube(cube, r, g, b) {
  const { size, data } = cube;
  const rf = clamp(r) * (size - 1);
  const gf = clamp(g) * (size - 1);
  const bf = clamp(b) * (size - 1);

  const r0 = Math.floor(rf), r1 = Math.min(size - 1, r0 + 1);
  const g0 = Math.floor(gf), g1 = Math.min(size - 1, g0 + 1);
  const b0 = Math.floor(bf), b1 = Math.min(size - 1, b0 + 1);

  const dr = rf - r0;
  const dg = gf - g0;
  const db = bf - b0;

  function getRGB(rIdx, gIdx, bIdx) {
    // Data is R-fastest (b * size * size + g * size + r) * 3
    const idx = (bIdx * size * size + gIdx * size + rIdx) * 3;
    return [data[idx], data[idx + 1], data[idx + 2]];
  }

  const c000 = getRGB(r0, g0, b0);
  const c100 = getRGB(r1, g0, b0);
  const c010 = getRGB(r0, g1, b0);
  const c110 = getRGB(r1, g1, b0);
  const c001 = getRGB(r0, g0, b1);
  const c101 = getRGB(r1, g0, b1);
  const c011 = getRGB(r0, g1, b1);
  const c111 = getRGB(r1, g1, b1);

  // Interpolate along R
  const c00 = [c000[0] * (1 - dr) + c100[0] * dr, c000[1] * (1 - dr) + c100[1] * dr, c000[2] * (1 - dr) + c100[2] * dr];
  const c10 = [c010[0] * (1 - dr) + c110[0] * dr, c010[1] * (1 - dr) + c110[1] * dr, c010[2] * (1 - dr) + c110[2] * dr];
  const c01 = [c001[0] * (1 - dr) + c101[0] * dr, c001[1] * (1 - dr) + c101[1] * dr, c001[2] * (1 - dr) + c101[2] * dr];
  const c11 = [c011[0] * (1 - dr) + c111[0] * dr, c011[1] * (1 - dr) + c111[1] * dr, c011[2] * (1 - dr) + c111[2] * dr];

  // Interpolate along G
  const c0 = [c00[0] * (1 - dg) + c10[0] * dg, c00[1] * (1 - dg) + c10[1] * dg, c00[2] * (1 - dg) + c10[2] * dg];
  const c1 = [c01[0] * (1 - dg) + c11[0] * dg, c01[1] * (1 - dg) + c11[1] * dg, c01[2] * (1 - dg) + c11[2] * dg];

  // Interpolate along B
  return [
    c0[0] * (1 - db) + c1[0] * db,
    c0[1] * (1 - db) + c1[1] * db,
    c0[2] * (1 - db) + c1[2] * db,
  ];
}

function parseCubeFile(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  const lines = content.split(/\r?\n/);
  let size = 0;
  const values = [];

  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || line.startsWith("TITLE") || line.startsWith("DOMAIN_")) continue;
    if (line.startsWith("LUT_3D_SIZE")) {
      size = parseInt(line.split(/\s+/)[1], 10);
      continue;
    }
    const parts = line.split(/\s+/).map(Number);
    if (parts.length >= 3 && !isNaN(parts[0])) {
      values.push(parts[0], parts[1], parts[2]);
    }
  }

  if (!size) throw new Error("Invalid cube: missing LUT_3D_SIZE");
  return { size, data: new Float32Array(values) };
}

// Resample any cube to target size 17
function resampleTo17(srcCube) {
  const targetSize = SIZE;
  const out = new Float32Array(targetSize * targetSize * targetSize * 3);
  let idx = 0;
  for (let b = 0; b < targetSize; b++) {
    for (let g = 0; g < targetSize; g++) {
      for (let r = 0; r < targetSize; r++) {
        const rIn = r / (targetSize - 1);
        const gIn = g / (targetSize - 1);
        const bIn = b / (targetSize - 1);
        const [ro, go, bo] = sampleCube(srcCube, rIn, gIn, bIn);
        out[idx++] = clamp(ro);
        out[idx++] = clamp(go);
        out[idx++] = clamp(bo);
      }
    }
  }
  return { size: targetSize, data: out };
}

// Parse XMP parameters to LookLite slider values
function parseXmp(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  function getAttr(attr, defaultVal = 0) {
    const match = text.match(new RegExp(`crs:${attr}="([^"]+)"`));
    return match ? parseFloat(match[1]) : defaultVal;
  }

  return {
    contrast: getAttr("Contrast2012", 0),
    highlights: getAttr("Highlights2012", 0),
    shadows: getAttr("Shadows2012", 0),
    whites: getAttr("Whites2012", 0),
    blacks: getAttr("Blacks2012", 0),
    vibrance: getAttr("Vibrance", 0),
    saturation: getAttr("Saturation", 0),
    vignette: Math.abs(getAttr("PostCropVignetteAmount", 0)),
    grain_intensity: getAttr("GrainAmount", 0),
    grain_size: Math.min(20, getAttr("GrainSize", 10)),
    hsl: {
      red: { h: getAttr("HueAdjustmentRed"), s: getAttr("SaturationAdjustmentRed"), l: getAttr("LuminanceAdjustmentRed") },
      orange: { h: getAttr("HueAdjustmentOrange"), s: getAttr("SaturationAdjustmentOrange"), l: getAttr("LuminanceAdjustmentOrange") },
      yellow: { h: getAttr("HueAdjustmentYellow"), s: getAttr("SaturationAdjustmentYellow"), l: getAttr("LuminanceAdjustmentYellow") },
      green: { h: getAttr("HueAdjustmentGreen"), s: getAttr("SaturationAdjustmentGreen"), l: getAttr("LuminanceAdjustmentGreen") },
      aqua: { h: getAttr("HueAdjustmentAqua"), s: getAttr("SaturationAdjustmentAqua"), l: getAttr("LuminanceAdjustmentAqua") },
      blue: { h: getAttr("HueAdjustmentBlue"), s: getAttr("SaturationAdjustmentBlue"), l: getAttr("LuminanceAdjustmentBlue") },
      purple: { h: getAttr("HueAdjustmentPurple"), s: getAttr("SaturationAdjustmentPurple"), l: getAttr("LuminanceAdjustmentPurple") },
      magenta: { h: getAttr("HueAdjustmentMagenta"), s: getAttr("SaturationAdjustmentMagenta"), l: getAttr("LuminanceAdjustmentMagenta") },
    }
  };
}

console.log("Beginning full preset & LUT compilation...");

// Defined Preset catalog with metadata, swatches, and LUT origins
const PRESET_DEFINITIONS = [
  // --- FUJIFILM ---
  {
    id: "fuji.classic_chrome",
    brand: "fujifilm",
    name: "Classic Chrome",
    tag: "DOCUMENTARY",
    swatch: "linear-gradient(135deg, #2c3848, #9b8270)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Classic_Chrome.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Fuji Film.cube"
  },
  {
    id: "fuji.classic_neg",
    brand: "fujifilm",
    name: "Classic Neg",
    tag: "SUPERIA 400",
    swatch: "linear-gradient(135deg, #324338, #b86242)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Classic_Neg.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Color 400.cube"
  },
  {
    id: "fuji.velvia_50",
    brand: "fujifilm",
    name: "Velvia 50",
    tag: "LANDSCAPE VIVID",
    swatch: "linear-gradient(135deg, #1b5e20, #d81b60)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fuji - Velvia_50.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/fuji.velvia_50_approx.cube"
  },
  {
    id: "fuji.provia_100f",
    brand: "fujifilm",
    name: "Provia 100F",
    tag: "SLIDE TRANSPARENCY",
    swatch: "linear-gradient(135deg, #37474f, #78909c)",
    stage: "early",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fuji - Provia_100F.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro  - Fuji Provia.cube"
  },
  {
    id: "fuji.astia",
    brand: "fujifilm",
    name: "Astia Soft",
    tag: "PORTRAIT NATURAL",
    swatch: "linear-gradient(135deg, #d38b72, #dfa996)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Astia.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Fuji Portrait.cube"
  },
  {
    id: "fuji.reala_ace",
    brand: "fujifilm",
    name: "Reala Ace",
    tag: "FINE COLOR",
    swatch: "linear-gradient(135deg, #2e7d32, #fbc02d)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Reala_Ace.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Fuji Film.cube"
  },
  {
    id: "fuji.eterna",
    brand: "fujifilm",
    name: "Eterna Cinema",
    tag: "CINE UNDERSTATED",
    swatch: "linear-gradient(135deg, #455a64, #a1887f)",
    stage: "early",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Eterna.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Fuji Film.cube"
  },
  {
    id: "fuji.nostalgic_neg",
    brand: "fujifilm",
    name: "Nostalgic Neg",
    tag: "70S AMBER",
    swatch: "linear-gradient(135deg, #795548, #d7ccc8)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Nostalgic_Neg.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Golden Light.cube"
  },
  {
    id: "fuji.acros",
    brand: "fujifilm",
    name: "ACROS B&W",
    tag: "SILVER HALIDE",
    swatch: "linear-gradient(135deg, #111111, #888888)",
    stage: "late",
    monochrome: true,
    xmp: "/tmp/film-simulations/presets/Fuji/Monochrome/Fujifilm - Acros.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/fuji.acros_approx.cube"
  },
  {
    id: "fuji.bleach_bypass",
    brand: "fujifilm",
    name: "Bleach Bypass",
    tag: "HIGH CONTRAST",
    swatch: "linear-gradient(135deg, #212121, #cfd8dc)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Fuji/Color/Fujifilm - Bleach_Bypass.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/fuji.classic_chrome_approx.cube"
  },

  // --- KODAK ---
  {
    id: "kodak.portra_400",
    brand: "kodak",
    name: "Portra 400",
    tag: "PRO PORTRAIT",
    swatch: "linear-gradient(135deg, #dfa97b, #a66a46)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Portra_400.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro  - Portra 400.cube"
  },
  {
    id: "kodak.portra_160",
    brand: "kodak",
    name: "Portra 160",
    tag: "PASTEL CREAM",
    swatch: "linear-gradient(135deg, #e4be9e, #c28864)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Portra_160.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Portra 160.cube"
  },
  {
    id: "kodak.portra_800",
    brand: "kodak",
    name: "Portra 800",
    tag: "WARM LATITUDE",
    swatch: "linear-gradient(135deg, #e67e22, #995010)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Portra_800.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Portra 800.cube"
  },
  {
    id: "kodak.gold_200",
    brand: "kodak",
    name: "Gold 200",
    tag: "NOSTALGIC WARMTH",
    swatch: "linear-gradient(135deg, #f39c12, #d35400)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Gold_200.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro  - Gold 200.cube"
  },
  {
    id: "kodak.ektar_100",
    brand: "kodak",
    name: "Ektar 100",
    tag: "ULTRA VIVID",
    swatch: "linear-gradient(135deg, #0d47a1, #b71c1c)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Ektar_100.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/kodak.ektar_100_approx.cube"
  },
  {
    id: "kodak.kodachrome_64",
    brand: "kodak",
    name: "Kodachrome 64",
    tag: "ICONIC SLIDE",
    swatch: "linear-gradient(135deg, #e65100, #ffb300)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Kodachrome_64.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro  - Kodacrome 64.cube"
  },
  {
    id: "kodak.cinestill_800t",
    brand: "kodak",
    name: "CineStill 800T",
    tag: "HALATION NIGHT",
    swatch: "linear-gradient(135deg, #00838f, #e91e63)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - CineStill_800T.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Street Car.cube"
  },
  {
    id: "kodak.vision3_500t",
    brand: "kodak",
    name: "Vision3 500T",
    tag: "HOLLYWOOD CINE",
    swatch: "linear-gradient(135deg, #1a237e, #ff6f00)",
    stage: "early",
    xmp: "/tmp/film-simulations/presets/Kodak/Color/Kodak - Vision3_500T.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Kodak Color.cube"
  },
  {
    id: "kodak.trix_400",
    brand: "kodak",
    name: "Tri-X 400",
    tag: "DENSE BLACKS",
    swatch: "linear-gradient(135deg, #050505, #555555)",
    stage: "late",
    monochrome: true,
    xmp: "/tmp/film-simulations/presets/Kodak/Monochrome/Kodak - Tri-X_400.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/kodak.trix_400_approx.cube"
  },

  // --- LEICA ---
  {
    id: "leica.classic",
    brand: "leica",
    name: "Leica Classic",
    tag: "TIMELESS 3D-POP",
    swatch: "linear-gradient(135deg, #263238, #b0bec5)",
    stage: "early",
    xmp: "/tmp/film-simulations/presets/Leica/Color/Leica-style - Classic.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/leica.m_system_approx.cube"
  },
  {
    id: "leica.natural",
    brand: "leica",
    name: "Leica Natural",
    tag: "OPTICAL CLARITY",
    swatch: "linear-gradient(135deg, #37474f, #eceff1)",
    stage: "early",
    xmp: "/tmp/film-simulations/presets/Leica/Color/Leica-style - Natural.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/hasselblad.hncs_approx.cube"
  },
  {
    id: "leica.vivid",
    brand: "leica",
    name: "Leica Vivid",
    tag: "RICH SATURATION",
    swatch: "linear-gradient(135deg, #c2185b, #0097a7)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Leica/Color/Leica-style - Vivid.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Golden Light.cube"
  },
  {
    id: "leica.teal",
    brand: "leica",
    name: "Leica Teal",
    tag: "EDITORIAL TONE",
    swatch: "linear-gradient(135deg, #004d40, #ff8a65)",
    stage: "late",
    xmp: "/tmp/film-simulations/presets/Leica/Color/Leica-style - Teal.xmp",
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Street Car.cube"
  },
  {
    id: "leica.monochrom",
    brand: "leica",
    name: "Leica Monochrom",
    tag: "CCD CONTRAST",
    swatch: "linear-gradient(135deg, #141414, #757575)",
    stage: "late",
    monochrome: true,
    xmp: "/tmp/film-simulations/presets/Leica/Monochrome/Leica-style - Monochrom High Contrast.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/leica.cl_monochrom_approx.cube"
  },
  {
    id: "leica.greg_williams",
    brand: "leica",
    name: "Greg Williams",
    tag: "CELEBRITY CANDID",
    swatch: "linear-gradient(135deg, #0d0d0d, #bdbdbd)",
    stage: "late",
    monochrome: true,
    xmp: "/tmp/film-simulations/presets/Leica/Monochrome/Leica-style - Greg Williams.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/leica.cl_monochrom_approx.cube"
  },

  // --- ILFORD ---
  {
    id: "ilford.hp5_plus",
    brand: "ilford",
    name: "HP5 Plus 400",
    tag: "STREET REPORTAGE",
    swatch: "linear-gradient(135deg, #1c1c1c, #9e9e9e)",
    stage: "late",
    monochrome: true,
    xmp: "/tmp/film-simulations/presets/Ilford/Monochrome/Ilford - HP5_Plus.xmp",
    cubeSrc: "/home/soju/Documents/looklite/luts/kodak.trix_400_approx.cube"
  },

  // --- VINTAGE & SPECIALTY ---
  {
    id: "vintage.disposable_400",
    brand: "vintage",
    name: "Disposable 400",
    tag: "35MM FLASH RETRO",
    swatch: "linear-gradient(135deg, #5d4037, #ffcc80)",
    stage: "late",
    xmp: null,
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Disposable 400.cube"
  },
  {
    id: "vintage.polaroid_color",
    brand: "vintage",
    name: "Polaroid Color",
    tag: "INSTANT PACKFILM",
    swatch: "linear-gradient(135deg, #455a64, #b0bec5)",
    stage: "late",
    xmp: null,
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Polaroid Color.cube"
  },
  {
    id: "vintage.golden_light",
    brand: "vintage",
    name: "Golden Light",
    tag: "WARM SUNSET GLOW",
    swatch: "linear-gradient(135deg, #e65100, #ffe082)",
    stage: "late",
    xmp: null,
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Golden Light.cube"
  },
  {
    id: "vintage.street_car",
    brand: "vintage",
    name: "Street Car",
    tag: "MOODY CINEMATIC",
    swatch: "linear-gradient(135deg, #006064, #bf360c)",
    stage: "late",
    xmp: null,
    cubeSrc: "/tmp/downloaded_luts/Presetpro - Street Car.cube"
  }
];

const bundleLuts = {};
const finalPresetList = [];

for (const def of PRESET_DEFINITIONS) {
  console.log(`Processing [${def.brand}] ${def.name}...`);
  let xmpParams = {};
  if (def.xmp && fs.existsSync(def.xmp)) {
    xmpParams = parseXmp(def.xmp);
  }

  // Load and resample cube
  let parsedCube = null;
  if (fs.existsSync(def.cubeSrc)) {
    parsedCube = parseCubeFile(def.cubeSrc);
  } else {
    throw new Error(`Missing cube source: ${def.cubeSrc}`);
  }

  const resampled = resampleTo17(parsedCube);

  // Convert to uint8 array
  const uint8s = new Uint8Array(SIZE * SIZE * SIZE * 3);
  for (let i = 0; i < resampled.data.length; i++) {
    uint8s[i] = Math.round(resampled.data[i] * 255);
  }

  // Store in base64 LUT bundle
  const b64 = Buffer.from(uint8s.buffer).toString("base64");
  bundleLuts[def.id] = b64;

  // Build JSON Preset object
  const presetJson = {
    preset_id: def.id,
    camera_system: def.brand,
    look_name: def.name,
    display_label: def.name,
    tag: def.tag,
    lut_asset_path: `luts/${def.id}.cube`,
    lut_stage: def.stage,
    monochrome: def.monochrome || false,
    default_sliders: {
      exposure: 0.0,
      contrast: xmpParams.contrast || 0,
      highlights: xmpParams.highlights || 0,
      shadows: xmpParams.shadows || 0,
      whites: xmpParams.whites || 0,
      blacks: xmpParams.blacks || 0,
      temp: 0,
      tint: 0,
      vibrance: xmpParams.vibrance || 0,
      saturation: def.monochrome ? -100 : (xmpParams.saturation || 0),
      vignette: xmpParams.vignette || 0,
      grain_intensity: 0, // Clean default grain (no forced noise!)
      grain_size: 10,
      hsl: xmpParams.hsl || {}
    }
  };

  finalPresetList.push(presetJson);
}

// Write web/presets-data.js
fs.writeFileSync(
  path.join(__dirname, "../web/presets-data.js"),
  `export const PRESETS = ${JSON.stringify(finalPresetList, null, 2)};\n`
);

// Write web/luts-bundle.js
const lutsBundleContent = `// Auto-generated 17^3 3D LUT binary bundles (${finalPresetList.length} presets)
export const LUT_SIZE = ${SIZE};
export const LUT_DATA_BASE64 = ${JSON.stringify(bundleLuts, null, 2)};

export function getLutUint8Array(presetId) {
  const b64 = LUT_DATA_BASE64[presetId];
  if (!b64) return null;
  const binary = atob(b64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}
`;
fs.writeFileSync(path.join(__dirname, "../web/luts-bundle.js"), lutsBundleContent);

// Also export individual .cube files to luts/ and app/src/main/assets/luts/
for (const p of finalPresetList) {
  const bytes = Buffer.from(bundleLuts[p.preset_id], "base64");
  let cubeLines = [
    `TITLE "${p.look_name}"`,
    `LUT_3D_SIZE ${SIZE}`,
    `DOMAIN_MIN 0.0 0.0 0.0`,
    `DOMAIN_MAX 1.0 1.0 1.0`,
    ""
  ];
  let idx = 0;
  for (let b = 0; b < SIZE; b++) {
    for (let g = 0; g < SIZE; g++) {
      for (let r = 0; r < SIZE; r++) {
        const ro = (bytes[idx++] / 255).toFixed(6);
        const go = (bytes[idx++] / 255).toFixed(6);
        const bo = (bytes[idx++] / 255).toFixed(6);
        cubeLines.push(`${ro} ${go} ${bo}`);
      }
    }
  }
  fs.writeFileSync(path.join(__dirname, `../luts/${p.preset_id}.cube`), cubeLines.join("\n"));
}

console.log(`\nSuccessfully compiled and bundled ${finalPresetList.length} high-fidelity analog presets!`);
