/**
 * Color Science 3D LUT Generator for LookLite
 * Generates 17^3 (4913 points) 3D Color Cubes (.cube and JS Uint8 bundle)
 * for each camera and film stock profile.
 */
const fs = require("fs");
const path = require("path");

const SIZE = 17; // 17x17x17 lattice

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

function sCurve(x, contrast) {
  // Classic film S-curve centered around 0.5
  const c = contrast;
  return 1 / (1 + Math.exp(-c * (x - 0.5)));
}

function normalizeSCurve(x, contrast) {
  const y0 = sCurve(0, contrast);
  const y1 = sCurve(1, contrast);
  return (sCurve(x, contrast) - y0) / (y1 - y0);
}

// Color science transformations per profile
const PROFILES = {
  "fuji.classic_chrome_approx": (r, g, b) => {
    // Harder contrast in shadows, muted chroma, deep cyan skies
    let [h, s, l] = rgb2hsl(r, g, b);
    // Tone curve: crushed shadows
    let lOut = Math.pow(l, 1.15);
    lOut = normalizeSCurve(lOut, 4.8);
    // Sky blue (h ~ 0.55 - 0.68) -> push slightly towards cyan, deepen
    const blueDist = Math.abs(h - 0.60);
    if (blueDist < 0.15) {
      const w = 1 - blueDist / 0.15;
      h = h - 0.04 * w; // cyan shift
      lOut = lOut * (1 - 0.12 * w); // deeper
      s = s * (1 + 0.10 * w);
    }
    // Greens (h ~ 0.25 - 0.42) -> earthy olive desaturation
    const greenDist = Math.abs(h - 0.33);
    if (greenDist < 0.12) {
      const w = 1 - greenDist / 0.12;
      h = h - 0.05 * w;
      s = s * (1 - 0.25 * w);
    }
    s *= 0.82; // general muted chroma
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    // Subtle cool shadow tint
    const shW = Math.max(0, 1 - l * 2.5);
    bo += 0.03 * shW;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "fuji.velvia_50_approx": (r, g, b) => {
    // Punchy landscape film, high contrast, vivid greens and deep blues
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 5.5);
    // High saturation boost
    s = Math.min(1, s * 1.35);
    // Warm yellows/greens boost
    if (h > 0.15 && h < 0.45) {
      s = Math.min(1, s * 1.15);
    }
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    ro = Math.pow(ro, 0.95);
    go = Math.pow(go, 0.92);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "fuji.provia_approx": (r, g, b) => {
    // Neutral reference, linear fidelity
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 3.8);
    let [ro, go, bo] = hsl2rgb(h, s * 1.02, lOut);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "fuji.astia_approx": (r, g, b) => {
    // Gentle portrait, smooth highlights, soft skin
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = Math.pow(l, 0.95); // lifted soft mids
    // Protect orange/skin (h ~ 0.05 - 0.12)
    const skinDist = Math.abs(h - 0.08);
    if (skinDist < 0.08) {
      const w = 1 - skinDist / 0.08;
      s = s * (1 - 0.08 * w);
      lOut = lOut * (1 + 0.04 * w);
    }
    let [ro, go, bo] = hsl2rgb(h, s * 0.92, lOut);
    ro += 0.02 * (1 - l);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "fuji.acros_approx": (r, g, b) => {
    // High-resolution B&W, green-channel sensitivity, rich shadow gradients
    const lum = 0.22 * r + 0.65 * g + 0.13 * b; // green sensitive
    let lOut = normalizeSCurve(lum, 5.2);
    return [clamp(lOut), clamp(lOut), clamp(lOut)];
  },

  "kodak.portra_160_approx": (r, g, b) => {
    // Warm pastel highlights, creamy skin tones, gentle contrast
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = Math.pow(l, 0.93); // wide latitude
    lOut = normalizeSCurve(lOut, 3.6);
    // Warm peach/yellow-red bias for skin
    if (h < 0.15) {
      h = h * 0.92 + 0.005; // slight yellow-warm shift
      s = s * 0.95;
    }
    s *= 0.90;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    // Warm highlight shoulder
    const hiW = Math.max(0, (l - 0.5) * 2);
    ro += 0.03 * hiW;
    go += 0.015 * hiW;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "kodak.portra_400_approx": (r, g, b) => {
    // Richer mids, warm highlights, organic shadow roll-off
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 4.2);
    s *= 0.93;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    // Characteristic golden base
    ro += 0.025 * l;
    go += 0.012 * l;
    bo *= 0.96;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "kodak.gold_200_approx": (r, g, b) => {
    // Golden warmth, saturated amber/yellow hues, nostalgic consumer print look
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 4.5);
    s *= 1.15;
    // Shift hues towards warm amber
    if (h < 0.25) {
      h = h * 0.88 + 0.02;
    }
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    ro += 0.05 * (1 - l * 0.5);
    go += 0.03 * (1 - l * 0.5);
    bo = bo * 0.88;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "kodak.ektar_100_approx": (r, g, b) => {
    // Punchy micro-contrast, vibrant reds and rich skies
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 5.0);
    s = Math.min(1, s * 1.25);
    // Enhance deep reds and blues
    if (h < 0.05 || h > 0.90) s = Math.min(1, s * 1.2);
    if (h > 0.55 && h < 0.70) s = Math.min(1, s * 1.2);
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "kodak.trix_400_approx": (r, g, b) => {
    // Punchy classic silver halide monochrome, deep blacks
    const lum = 0.299 * r + 0.587 * g + 0.114 * b;
    let lOut = Math.pow(lum, 1.2); // dense shadows
    lOut = normalizeSCurve(lOut, 6.2);
    return [clamp(lOut), clamp(lOut), clamp(lOut)];
  },

  "leica.m_system_approx": (r, g, b) => {
    // High dynamic range, micro-contrast optical separation, cool-neutral shadows
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = normalizeSCurve(l, 4.8);
    let [ro, go, bo] = hsl2rgb(h, s * 0.96, lOut);
    // Crisp clarity: slightly cooler shadows, crisp neutral midtones
    const shW = Math.max(0, 0.4 - l) / 0.4;
    bo += 0.02 * shW;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "leica.cl_monochrom_approx": (r, g, b) => {
    // Creamy midtone roll-off, deep tonal blacks, pure CCD Monochrom feel
    const lum = 0.24 * r + 0.66 * g + 0.10 * b;
    let lOut = Math.pow(lum, 0.95);
    lOut = normalizeSCurve(lOut, 4.6);
    return [clamp(lOut), clamp(lOut), clamp(lOut)];
  },

  "leica.street_look_approx": (r, g, b) => {
    // Signature street tone: muted earthy greens, subtle warm highlights, deep shadows
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = Math.pow(l, 1.1);
    lOut = normalizeSCurve(lOut, 4.9);
    // Greens to olive
    if (h > 0.20 && h < 0.45) {
      h = h - 0.06;
      s = s * 0.70;
    }
    s *= 0.85;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    ro += 0.02 * l;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "hasselblad.hncs_approx": (r, g, b) => {
    // Hasselblad Natural Colour Solution: ultra-natural, smooth highlight roll-off, zero color cast
    let [h, s, l] = rgb2hsl(r, g, b);
    // Smooth highlight compression (no harsh clipping)
    let lOut = l < 0.7 ? l : 0.7 + 0.3 * (1 - Math.exp(-(l - 0.7) / 0.3));
    let [ro, go, bo] = hsl2rgb(h, s * 0.98, lOut);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "sony.fl_approx": (r, g, b) => {
    // Film-like: matte lifted blacks, desaturated greens, warm cyan highlights
    let [h, s, l] = rgb2hsl(r, g, b);
    // Lifted blacks (matte)
    let lOut = 0.05 + 0.95 * l;
    lOut = normalizeSCurve(lOut, 4.6);
    // Mute greens
    if (h > 0.22 && h < 0.45) s *= 0.65;
    s *= 0.88;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    // Cyan highlight bias
    const hiW = Math.max(0, l - 0.5) * 2;
    go += 0.025 * hiW;
    bo += 0.04 * hiW;
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "sony.in_approx": (r, g, b) => {
    // Instant film look: low contrast, soft highlights, lifted shadows
    let [h, s, l] = rgb2hsl(r, g, b);
    let lOut = 0.08 + 0.88 * l; // low contrast
    s *= 0.85;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    return [clamp(ro), clamp(go), clamp(bo)];
  },

  "sony.scinetone_mobile_approx": (r, g, b) => {
    // Cine profile: soft highlights, cinematic midtone roll-off, pleasing portrait tones
    let [h, s, l] = rgb2hsl(r, g, b);
    // Cine gamma compression
    let lOut = Math.pow(l, 0.92);
    lOut = normalizeSCurve(lOut, 4.1);
    // Beautiful portrait tones
    if (h < 0.14) {
      s = s * 0.95;
      lOut = lOut * 1.02;
    }
    s *= 0.92;
    let [ro, go, bo] = hsl2rgb(h, s, lOut);
    return [clamp(ro), clamp(go), clamp(bo)];
  }
};

// Generate for each profile
const bundleObject = {};
const lutsDir = path.join(__dirname, "../luts");
if (!fs.existsSync(lutsDir)) fs.mkdirSync(lutsDir, { recursive: true });

for (const [id, transform] of Object.entries(PROFILES)) {
  const floats = new Float32Array(SIZE * SIZE * SIZE * 3);
  const uint8s = new Uint8Array(SIZE * SIZE * SIZE * 3);
  let cubeLines = [
    `TITLE "${id}"`,
    `LUT_3D_SIZE ${SIZE}`,
    `DOMAIN_MIN 0.0 0.0 0.0`,
    `DOMAIN_MAX 1.0 1.0 1.0`,
    ""
  ];

  let idx = 0;
  for (let bIdx = 0; bIdx < SIZE; bIdx++) {
    for (let gIdx = 0; gIdx < SIZE; gIdx++) {
      for (let rIdx = 0; rIdx < SIZE; rIdx++) {
        const rIn = rIdx / (SIZE - 1);
        const gIn = gIdx / (SIZE - 1);
        const bIn = bIdx / (SIZE - 1);

        const [rOut, gOut, bOut] = transform(rIn, gIn, bIn);

        floats[idx] = rOut;
        floats[idx + 1] = gOut;
        floats[idx + 2] = bOut;

        uint8s[idx] = Math.round(rOut * 255);
        uint8s[idx + 1] = Math.round(gOut * 255);
        uint8s[idx + 2] = Math.round(bOut * 255);

        cubeLines.push(`${rOut.toFixed(6)} ${gOut.toFixed(6)} ${bOut.toFixed(6)}`);
        idx += 3;
      }
    }
  }

  // Write .cube file
  const cubePath = path.join(lutsDir, `${id}.cube`);
  fs.writeFileSync(cubePath, cubeLines.join("\n"));

  // Store in bundle object as base64 string for zero-cost, instant loading
  const buffer = Buffer.from(uint8s.buffer);
  bundleObject[id] = buffer.toString("base64");
}

// Generate web/luts-bundle.js
const lutsBundleContent = `// Auto-generated 17^3 3D LUT binary bundles for all camera profiles
export const LUT_SIZE = ${SIZE};
export const LUT_DATA_BASE64 = ${JSON.stringify(bundleObject, null, 2)};

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
console.log(`Successfully generated 17 3D LUTs (.cube and web/luts-bundle.js) of size ${SIZE}^3 (${SIZE*SIZE*SIZE*3} values each).`);
