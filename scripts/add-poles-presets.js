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

// 8 Brand New Poles & Trending Presets inspired directly by user references
const POLES_PRESETS = [
  {
    id: "poles.fnatix_flash",
    name: "Fnatix Moody Flash",
    tag: "MOODY FLASH",
    swatch: "linear-gradient(135deg, #1a2a3a, #e0b48a)",
    desc: "Viral aesthetic moody flash with dark cyan ocean, cool skies, and bright porcelain skin pop.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      // Skin detection
      let isSkin = (h >= 0.02 && h <= 0.12 && s >= 0.15 && l >= 0.2);
      if (isSkin) {
        l = Math.pow(l, 0.88); // brighten skin
        s = s * 0.92;          // creamy fair skin
      }
      // Ocean / Sky blues: shift towards cyan/steel-blue & deepen
      if (h >= 0.50 && h <= 0.65) {
        h = 0.54; // steel cyan
        s = s * 0.85;
        l = l * 0.78; // deeper moody sky/water
      }
      // Greens: dark moody emerald
      if (h >= 0.22 && h <= 0.42) {
        h = 0.38;
        s = s * 0.70;
        l = l * 0.75;
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      // S-curve contrast
      nr = nr < 0.5 ? 2.0 * nr * nr : 1.0 - 2.0 * (1.0 - nr) * (1.0 - nr);
      ng = ng < 0.5 ? 2.0 * ng * ng : 1.0 - 2.0 * (1.0 - ng) * (1.0 - ng);
      nb = nb < 0.5 ? 2.0 * nb * nb : 1.0 - 2.0 * (1.0 - nb) * (1.0 - nb);
      // Cool blue shadow cast
      nb += (1.0 - l) * 0.04;
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.1,
      contrast: 15,
      highlights: -10,
      shadows: -15,
      whites: 10,
      blacks: -12,
      temp: -4,
      tint: 2,
      vibrance: 5,
      saturation: -5,
      vignette: 15,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 35,
      skin_radiance: 30,
      clarity: 20,
      glow: 15,
      sharpness: 20
    }
  },
  {
    id: "poles.korean_porcelain",
    name: "Korean Porcelain",
    tag: "PORCELAIN",
    swatch: "linear-gradient(135deg, #dbe2ef, #f7d6bf)",
    desc: "Fair Korean idol skin with creamy bright highlights and muted pastel background.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      let isSkin = (h >= 0.02 && h <= 0.13 && s >= 0.12 && l >= 0.25);
      if (isSkin) {
        l = Math.pow(l, 0.82); // radiant bright skin
        s = s * 0.88;          // fair porcelain
        h = 0.07;              // perfect neutral peach
      }
      // Greens muted soft sage
      if (h >= 0.22 && h <= 0.45) {
        s = s * 0.65;
        l = l * 0.85;
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      nr = Math.pow(nr, 0.92);
      ng = Math.pow(ng, 0.92);
      nb = Math.pow(nb, 0.90);
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.2,
      contrast: 8,
      highlights: -15,
      shadows: 10,
      whites: 15,
      blacks: -5,
      temp: -2,
      tint: 4,
      vibrance: 8,
      saturation: -2,
      vignette: 0,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 40,
      skin_radiance: 35,
      clarity: 10,
      glow: 25,
      sharpness: 15
    }
  },
  {
    id: "poles.emerald_moody",
    name: "Emerald Moody",
    tag: "EMERALD",
    swatch: "linear-gradient(135deg, #0e2a22, #b8860b)",
    desc: "Deep rich emerald greens and moody dark tones with glowing subject illumination.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      if (h >= 0.20 && h <= 0.48) {
        h = 0.40; // emerald hue
        s = s * 1.15; // rich saturation
        l = l * 0.70; // darkened foliage
      }
      let isSkin = (h >= 0.02 && h <= 0.12 && s >= 0.15 && l >= 0.25);
      if (isSkin) {
        l = Math.pow(l, 0.85);
        s = s * 1.05;
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      nr = nr < 0.5 ? 2.0 * nr * nr : 1.0 - 2.0 * (1.0 - nr) * (1.0 - nr);
      ng = ng < 0.5 ? 2.0 * ng * ng : 1.0 - 2.0 * (1.0 - ng) * (1.0 - ng);
      nb = nb < 0.5 ? 2.0 * nb * nb : 1.0 - 2.0 * (1.0 - nb) * (1.0 - nb);
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.0,
      contrast: 18,
      highlights: -12,
      shadows: -18,
      whites: 8,
      blacks: -15,
      temp: -3,
      tint: 2,
      vibrance: 12,
      saturation: 4,
      vignette: 18,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 30,
      skin_radiance: 25,
      clarity: 25,
      glow: 10,
      sharpness: 25
    }
  },
  {
    id: "poles.night_ocean",
    name: "Night Ocean",
    tag: "CYBER MOOD",
    swatch: "linear-gradient(135deg, #0f2027, #203a43)",
    desc: "Cinematic night ambiance with deep oceanic teals and warm crisp portrait focus.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      if (h >= 0.48 && h <= 0.68) {
        h = 0.52; // oceanic cyan
        s = s * 1.1;
        l = l * 0.75;
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      // Teal shadows, warm highlights
      nb += (1.0 - l) * 0.08;
      ng += (1.0 - l) * 0.03;
      nr += l * 0.05;
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.0,
      contrast: 22,
      highlights: -8,
      shadows: -22,
      whites: 12,
      blacks: -18,
      temp: -6,
      tint: 0,
      vibrance: 10,
      saturation: 0,
      vignette: 22,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 25,
      skin_radiance: 25,
      clarity: 28,
      glow: 12,
      sharpness: 28
    }
  },
  {
    id: "poles.soft_glow_dream",
    name: "Pro-Mist Dream",
    tag: "SOFT GLOW",
    swatch: "linear-gradient(135deg, #fbc2eb, #a6c1ee)",
    desc: "Dreamy diffusion filter halation with creamy skin texture and romantic highlights.",
    transform: (r, g, b) => {
      let nr = Math.pow(r, 0.90);
      let ng = Math.pow(g, 0.90);
      let nb = Math.pow(b, 0.92);
      // Soft pastel compression
      nr = nr * 0.92 + 0.06;
      ng = ng * 0.92 + 0.06;
      nb = nb * 0.92 + 0.08;
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.15,
      contrast: -8,
      highlights: -20,
      shadows: 20,
      whites: -5,
      blacks: 8,
      temp: 2,
      tint: 3,
      vibrance: 10,
      saturation: -4,
      vignette: 0,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 45,
      skin_radiance: 30,
      clarity: -10,
      glow: 40,
      sharpness: 10
    }
  },
  {
    id: "poles.studio_flash",
    name: "Studio Flash",
    tag: "COMMERCIAL",
    swatch: "linear-gradient(135deg, #2c3e50, #bdc3c7)",
    desc: "Clean studio flash portrait with neutral grey-white backdrop and sharp subject punch.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      let isSkin = (h >= 0.02 && h <= 0.13 && s >= 0.15 && l >= 0.25);
      if (isSkin) {
        l = Math.pow(l, 0.86);
        h = 0.072;
      } else {
        // Desaturate background slightly for studio separation
        s = s * 0.82;
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      nr = nr < 0.5 ? 2.0 * nr * nr : 1.0 - 2.0 * (1.0 - nr) * (1.0 - nr);
      ng = ng < 0.5 ? 2.0 * ng * ng : 1.0 - 2.0 * (1.0 - ng) * (1.0 - ng);
      nb = nb < 0.5 ? 2.0 * nb * nb : 1.0 - 2.0 * (1.0 - nb) * (1.0 - nb);
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.1,
      contrast: 16,
      highlights: -10,
      shadows: -12,
      whites: 14,
      blacks: -10,
      temp: -2,
      tint: 1,
      vibrance: 6,
      saturation: -3,
      vignette: 10,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 30,
      skin_radiance: 30,
      clarity: 30,
      glow: 12,
      sharpness: 30
    }
  },
  {
    id: "poles.golden_radiance",
    name: "Golden Radiance",
    tag: "SUNSET GLOW",
    swatch: "linear-gradient(135deg, #ff8c00, #ffd700)",
    desc: "Warm sun-drenched golden hour with amber skin radiance and glowing highlights.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      h = h * 0.95 + 0.03 * (1.0 - l); // warm shift
      s = s * 1.1;
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      nr += l * 0.08;
      ng += l * 0.04;
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: 0.1,
      contrast: 10,
      highlights: -8,
      shadows: 8,
      whites: 10,
      blacks: -4,
      temp: 14,
      tint: -3,
      vibrance: 16,
      saturation: 8,
      vignette: 12,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 35,
      skin_radiance: 35,
      clarity: 15,
      glow: 28,
      sharpness: 18
    }
  },
  {
    id: "poles.dark_aesthetic",
    name: "Dark Aesthetic",
    tag: "LOW KEY",
    swatch: "linear-gradient(135deg, #141414, #434343)",
    desc: "Moody stormy low-key drama with dark desaturated surroundings and focused subject light.",
    transform: (r, g, b) => {
      let [h, s, l] = rgb2hsl(r, g, b);
      l = Math.pow(l, 1.25); // crush exposure organically
      let isSkin = (h >= 0.02 && h <= 0.12 && s >= 0.15);
      if (isSkin) {
        l = l * 1.35; // recover subject skin
        s = s * 1.1;
      } else {
        s = s * 0.75; // desaturate surrounding
      }
      let [nr, ng, nb] = hsl2rgb(h, s, l);
      return [clamp(nr), clamp(ng), clamp(nb)];
    },
    sliders: {
      exposure: -0.15,
      contrast: 25,
      highlights: -15,
      shadows: -25,
      whites: 15,
      blacks: -28,
      temp: -4,
      tint: 1,
      vibrance: 8,
      saturation: -6,
      vignette: 28,
      grain_intensity: 0,
      grain_size: 10,
      skin_smooth: 30,
      skin_radiance: 25,
      clarity: 25,
      glow: 10,
      sharpness: 25
    }
  }
];

// Read existing presets-data.js and luts-bundle.js
const presetsPath = path.join(__dirname, "../web/presets-data.js");
const lutsPath = path.join(__dirname, "../web/luts-bundle.js");

const currentPresetsCode = fs.readFileSync(presetsPath, "utf8");
const currentPresets = JSON.parse(currentPresetsCode.replace(/^export const PRESETS = /, "").replace(/;\s*$/, ""));

// Filter out any existing poles.* to avoid duplicates
const cleanList = currentPresets.filter(p => !p.preset_id.startsWith("poles."));

const bundleLuts = {};

// Load existing LUT base64
const lutsCode = fs.readFileSync(lutsPath, "utf8");
const match = lutsCode.match(/export const LUT_DATA_BASE64 = ({[\s\S]*?});/);
let existingLutMap = {};
if (match) {
  existingLutMap = JSON.parse(match[1]);
}

const newPolesList = [];

for (const p of POLES_PRESETS) {
  const bytes = new Uint8Array(SIZE * SIZE * SIZE * 3);
  let idx = 0;
  for (let b = 0; b < SIZE; b++) {
    for (let g = 0; g < SIZE; g++) {
      for (let r = 0; r < SIZE; r++) {
        const rf = r / (SIZE - 1);
        const gf = g / (SIZE - 1);
        const bf = b / (SIZE - 1);
        const [ro, go, bo] = p.transform(rf, gf, bf);
        bytes[idx++] = Math.round(ro * 255);
        bytes[idx++] = Math.round(go * 255);
        bytes[idx++] = Math.round(bo * 255);
      }
    }
  }

  const b64 = Buffer.from(bytes).toString("base64");
  existingLutMap[p.id] = b64;

  // Also write .cube file
  let cubeLines = [
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

  newPolesList.push({
    preset_id: p.id,
    camera_system: "poles",
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

// Put Poles presets at the front of the list
const combinedList = [...newPolesList, ...cleanList];

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

console.log(`Successfully generated and added ${newPolesList.length} Poles & Aesthetic presets! Total presets now: ${combinedList.length}`);
