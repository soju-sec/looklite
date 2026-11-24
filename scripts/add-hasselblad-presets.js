const fs = require("fs");
const path = require("path");

const SIZE = 17;

function clamp(v, min = 0, max = 1) {
  return Math.min(max, Math.max(min, v));
}

function rgb2hsl(r, g, b) {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0, s = 0, l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
      case g: h = ((b - r) / d + 2) / 6; break;
      case b: h = ((r - g) / d + 4) / 6; break;
    }
  }
  return [h, s, l];
}

function hue2rgb(p, q, t) {
  if (t < 0) t += 1;
  if (t > 1) t -= 1;
  if (t < 1/6) return p + (q - p) * 6 * t;
  if (t < 1/2) return q;
  if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
  return p;
}

function hsl2rgb(h, s, l) {
  if (s === 0) return [l, l, l];
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  return [
    hue2rgb(p, q, h + 1/3),
    hue2rgb(p, q, h),
    hue2rgb(p, q, h - 1/3),
  ];
}

const HASSELBLAD_PRESETS = [
  {
    id: "hasselblad.hncs_standard",
    name: "Hasselblad HNCS Standard",
    tag: "HNCS MASTER",
    swatch: "linear-gradient(135deg, #2b313a, #cfd8dc)",
    desc: "Hallmark Hasselblad Natural Colour Solution. Pristine color fidelity, ultra-smooth highlight roll-off, and medium-format dynamic balance.",
    sliders: {
      exposure: 0.05,
      contrast: 6,
      highlights: -10,
      shadows: 8,
      whites: -5,
      blacks: 4,
      temp: 0,
      tint: 0,
      vibrance: 6,
      saturation: -2,
      vignette: 4,
      grain_intensity: 0,
      grain_size: 1,
      skin_smooth: 0,
      skin_radiance: 5,
      clarity: 8,
      glow: 0,
      sharpness: 12
    },
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      // Signature HNCS Logarithmic Highlight Roll-off (prevents digital clipping)
      let lOut = l < 0.65 ? l : 0.65 + 0.35 * (1 - Math.exp(-(l - 0.65) / 0.35));
      // Pristine true-to-life color neutrality (gentle saturation natural balance)
      let sOut = s * 0.98;
      let [ro, go, bo] = hsl2rgb(h, sOut, lOut);
      return [clamp(ro), clamp(go), clamp(bo)];
    }
  },
  {
    id: "hasselblad.hncs_portrait",
    name: "Hasselblad X2D Portrait",
    tag: "X2D PORTRAIT",
    swatch: "linear-gradient(135deg, #37474f, #f0d5c0)",
    desc: "Flagship medium-format portraiture with delicate skin luminosity, smooth midtone transitions, and velvety shadow depth.",
    sliders: {
      exposure: 0.1,
      contrast: 4,
      highlights: -14,
      shadows: 10,
      whites: -8,
      blacks: 6,
      temp: 2,
      tint: -2,
      vibrance: 8,
      saturation: -4,
      vignette: 6,
      grain_intensity: 0,
      grain_size: 1,
      skin_smooth: 22,
      skin_radiance: 16,
      clarity: 4,
      glow: 6,
      sharpness: 10
    },
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      let isSkin = (h >= 0.02 && h <= 0.13 && s >= 0.12 && l >= 0.22);
      let lOut = l < 0.65 ? l : 0.65 + 0.35 * (1 - Math.exp(-(l - 0.65) / 0.35));
      let sOut = s;

      if (isSkin) {
        // Luminosity pop for radiant skin without harshness
        lOut = Math.pow(lOut, 0.94);
        // Creamy peach skin tone alignment
        h = 0.065 + (h - 0.065) * 0.55;
        sOut = s * 0.93;
      } else {
        // Subtle background contrast separation
        sOut = s * 0.96;
      }
      let [ro, go, bo] = hsl2rgb(h, sOut, lOut);
      return [clamp(ro), clamp(go), clamp(bo)];
    }
  },
  {
    id: "hasselblad.hncs_landscape",
    name: "Hasselblad Nordic Nature",
    tag: "NORDIC NATURE",
    swatch: "linear-gradient(135deg, #1b382b, #81c784)",
    desc: "Expansive medium-format dynamic range for nature, lush organic greens, and serene natural blue skies.",
    sliders: {
      exposure: 0.0,
      contrast: 10,
      highlights: -16,
      shadows: 12,
      whites: -6,
      blacks: 4,
      temp: -2,
      tint: 2,
      vibrance: 12,
      saturation: 2,
      vignette: 8,
      grain_intensity: 0,
      grain_size: 1,
      skin_smooth: 0,
      skin_radiance: 0,
      clarity: 15,
      glow: 0,
      sharpness: 18
    },
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      let lOut = l < 0.68 ? l : 0.68 + 0.32 * (1 - Math.exp(-(l - 0.68) / 0.32));
      let sOut = s;

      // Verdant foliage greens (h ~ 0.22 - 0.45)
      if (h >= 0.20 && h <= 0.44) {
        h = 0.27 + (h - 0.27) * 0.8; // Deep organic forest green
        sOut = Math.min(1.0, s * 1.12);
        lOut = lOut * 0.96; // Rich shadow in foliage
      }
      // Calm atmospheric Nordic sky blues (h ~ 0.52 - 0.68)
      else if (h >= 0.50 && h <= 0.70) {
        h = 0.58 + (h - 0.58) * 0.75; // Clean natural sky blue without cyan cast
        sOut = Math.min(1.0, s * 1.08);
      }

      let [ro, go, bo] = hsl2rgb(h, sOut, lOut);
      return [clamp(ro), clamp(go), clamp(bo)];
    }
  },
  {
    id: "hasselblad.hncs_monochrome",
    name: "Hasselblad MF Monochrom",
    tag: "MF MONOCHROM",
    swatch: "linear-gradient(135deg, #121212, #cfd8dc)",
    desc: "Architectural medium-format monochrome with rich micro-contrast, deep tonal blacks, and silky highlight gradation.",
    sliders: {
      exposure: 0.0,
      contrast: 14,
      highlights: -12,
      shadows: 8,
      whites: 4,
      blacks: -6,
      temp: 0,
      tint: 0,
      vibrance: -100,
      saturation: -100,
      vignette: 10,
      grain_intensity: 4,
      grain_size: 10,
      skin_smooth: 0,
      skin_radiance: 0,
      clarity: 18,
      glow: 0,
      sharpness: 20
    },
    transform: (r, g, b) => {
      // Perceptual medium format luminance weights
      let lum = 0.299 * r + 0.587 * g + 0.114 * b;
      // S-curve tonal contrast with smooth highlight knee
      let lOut = lum;
      if (lOut < 0.5) {
        lOut = Math.pow(lOut, 1.12); // Deep velvety blacks
      } else {
        lOut = 0.5 + 0.5 * Math.pow((lOut - 0.5) / 0.5, 0.92); // Crisp mid-tones
        lOut = lOut < 0.75 ? lOut : 0.75 + 0.25 * (1 - Math.exp(-(lOut - 0.75) / 0.25));
      }
      return [clamp(lOut), clamp(lOut), clamp(lOut)];
    }
  }
];

// Load existing presets
const presetsPath = path.join(__dirname, "../web/presets-data.js");
const lutsPath = path.join(__dirname, "../web/luts-bundle.js");

const presetsContent = fs.readFileSync(presetsPath, "utf-8");
const existingPresets = JSON.parse(presetsContent.replace(/^export const PRESETS = /, "").replace(/;\s*$/, ""));

// Load existing LUT map
const lutsBundle = fs.readFileSync(lutsPath, "utf-8");
const match = lutsBundle.match(/export const LUT_DATA_BASE64 = (\{[\s\S]*?\});/);
const existingLutMap = match ? JSON.parse(match[1]) : {};

// Filter out old Hasselblad if any
const cleanList = existingPresets.filter(p => !p.preset_id.startsWith("hasselblad."));

const newHasselbladList = [];

for (const p of HASSELBLAD_PRESETS) {
  const bytes = new Uint8Array(SIZE * SIZE * SIZE * 3);
  let idx = 0;
  for (let b = 0; b < SIZE; b++) {
    for (let g = 0; g < SIZE; g++) {
      for (let r = 0; r < SIZE; r++) {
        const rf = r / (SIZE - 1);
        const gf = g / (SIZE - 1);
        const bf = b / (SIZE - 1);
        const [ro, go, bo] = p.transform(rf, gf, bf);
        bytes[idx++] = Math.round(clamp(ro) * 255);
        bytes[idx++] = Math.round(clamp(go) * 255);
        bytes[idx++] = Math.round(clamp(bo) * 255);
      }
    }
  }

  // Base64 encode for web bundle
  const b64 = Buffer.from(bytes).toString("base64");
  existingLutMap[p.id] = b64;

  // Export .cube file
  const cubeLines = [
    `TITLE "${p.name}"`,
    `LUT_3D_SIZE ${SIZE}`,
    `DOMAIN_MIN 0.0 0.0 0.0`,
    `DOMAIN_MAX 1.0 1.0 1.0`,
    ""
  ];
  let cIdx = 0;
  for (let b = 0; b < SIZE; b++) {
    for (let g = 0; g < SIZE; g++) {
      for (let r = 0; r < SIZE; r++) {
        const ro = (bytes[cIdx++] / 255).toFixed(6);
        const go = (bytes[cIdx++] / 255).toFixed(6);
        const bo = (bytes[cIdx++] / 255).toFixed(6);
        cubeLines.push(`${ro} ${go} ${bo}`);
      }
    }
  }
  fs.writeFileSync(path.join(__dirname, `../luts/${p.id}.cube`), cubeLines.join("\n"));

  newHasselbladList.push({
    preset_id: p.id,
    camera_system: "hasselblad",
    look_name: p.name,
    display_label: p.name,
    tag: p.tag,
    lut_asset_path: `luts/${p.id}.cube`,
    lut_stage: "early",
    description: p.desc,
    slider_ranges: {
      exposure: { min: -3, max: 3 },
      contrast: { min: -100, max: 100 },
      highlights: { min: -100, max: 100 },
      shadows: { min: -100, max: 100 },
      whites: { min: -100, max: 100 },
      blacks: { min: -100, max: 100 },
      temp: { min: -100, max: 100 },
      tint: { min: -100, max: 100 },
      vibrance: { min: -100, max: 100 },
      saturation: { min: -100, max: 100 },
      vignette: { min: 0, max: 100 },
      grain_intensity: { min: 0, max: 100 },
      grain_size: { min: 1, max: 100 },
      skin_smooth: { min: 0, max: 100 },
      skin_radiance: { min: 0, max: 100 },
      clarity: { min: -100, max: 100 },
      glow: { min: 0, max: 100 },
      sharpness: { min: 0, max: 100 }
    },
    default_sliders: {
      ...p.sliders,
      hsl: {}
    }
  });
}

// Merge presets: Poles -> Fuji -> Kodak -> Leica -> Hasselblad -> Ilford -> Vintage
const poles = cleanList.filter(p => p.camera_system === "poles");
const fuji = cleanList.filter(p => p.camera_system === "fujifilm");
const kodak = cleanList.filter(p => p.camera_system === "kodak");
const leica = cleanList.filter(p => p.camera_system === "leica");
const ilford = cleanList.filter(p => p.camera_system === "ilford");
const vintage = cleanList.filter(p => p.camera_system === "vintage");

const combinedList = [...poles, ...fuji, ...kodak, ...leica, ...newHasselbladList, ...ilford, ...vintage];

// Write web/presets-data.js
fs.writeFileSync(
  presetsPath,
  `export const PRESETS = ${JSON.stringify(combinedList, null, 2)};\n`
);

// Write web/luts-bundle.js
const lutsBundleContent = `// Auto-generated 17^3 3D LUT binary bundles (${combinedList.length} presets)
export const LUT_SIZE = ${SIZE};
export const LUT_DATA_BASE64 = ${JSON.stringify(existingLutMap, null, 2)};

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
fs.writeFileSync(lutsPath, lutsBundleContent);

console.log(`Successfully generated and added ${newHasselbladList.length} Hasselblad presets! Total presets now: ${combinedList.length}`);
