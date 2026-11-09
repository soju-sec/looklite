// core/compose.js
function isPlainObject(v) {
  return v !== null && typeof v === "object" && !Array.isArray(v);
}
function zeroSlidersLike(base) {
  const out = {};
  for (const key of Object.keys(base)) {
    const b = base[key];
    out[key] = isPlainObject(b) ? zeroSlidersLike(b) : 0;
  }
  return out;
}
function composeSliders(base, deltas, ranges = null) {
  const out = {};
  for (const key of Object.keys(base)) {
    const b = base[key];
    const d = deltas?.[key];
    if (isPlainObject(b)) {
      out[key] = composeSliders(b, d ?? {}, nestedRanges(ranges, key));
    } else {
      const sum = Number(b) + Number(d ?? 0);
      out[key] = clampKey(sum, ranges, key);
    }
  }
  return out;
}
function nestedRanges(ranges, key) {
  if (!ranges || ranges[key] == null)
    return null;
  if (ranges[key].min !== undefined)
    return null;
  return ranges[key];
}
function clampKey(value, ranges, key) {
  if (!ranges || ranges[key] == null)
    return value;
  const { min, max } = ranges[key];
  return Math.min(max, Math.max(min, value));
}

// core/cube.js
function identityCube(size) {
  const data = new Float32Array(size * size * size * 3);
  let i = 0;
  for (let b = 0;b < size; b++) {
    for (let g = 0;g < size; g++) {
      for (let r = 0;r < size; r++) {
        data[i++] = r / (size - 1);
        data[i++] = g / (size - 1);
        data[i++] = b / (size - 1);
      }
    }
  }
  return { size, data };
}
function cubeToRgb8(cube) {
  const { size, data } = cube;
  const out = new Uint8Array(size * size * size * 3);
  for (let i = 0;i < data.length; i++) {
    out[i] = Math.min(255, Math.max(0, Math.round(data[i] * 255)));
  }
  return { size, rgb8: out };
}

// web/shaders.js
var VERT = `#version 300 es
in vec2 aPos;
out vec2 vUv;
void main() {
  vUv = aPos * 0.5 + 0.5;
  gl_Position = vec4(aPos, 0.0, 1.0);
}`;
var FRAG = `#version 300 es
precision highp float;
precision highp sampler3D;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uImage;
uniform sampler3D uLut;
uniform float uLutSize;
uniform int uLutStage;
uniform int uShowBefore;

uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uTemp;
uniform float uTint;
uniform float uVibrance;
uniform float uSaturation;
uniform float uVignette;
uniform float uGrainIntensity;
uniform float uGrainSize;
uniform vec2 uResolution;

uniform vec3 uHslRed, uHslOrange, uHslYellow, uHslGreen;
uniform vec3 uHslAqua, uHslBlue, uHslPurple, uHslMagenta;
uniform vec3 uBalShadows, uBalMids, uBalHighlights;

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 applyWB(vec3 c, float temp, float tint) {
  float t = temp / 100.0;
  float g = tint / 100.0;
  c.r *= 1.0 + 0.12 * t;
  c.b *= 1.0 - 0.12 * t;
  c.g *= 1.0 + 0.08 * g;
  return clamp(c, 0.0, 1.0);
}

vec3 applyExposure(vec3 c, float ev) { return c * pow(2.0, ev); }

vec3 applyContrast(vec3 c, float amount) {
  float a = amount / 100.0;
  return clamp((c - 0.5) * (1.0 + a) + 0.5, 0.0, 1.0);
}

vec3 applyHiSh(vec3 c, float hi, float sh) {
  float l = lum(c);
  float hiW = smoothstep(0.4, 1.0, l);
  float shW = 1.0 - smoothstep(0.0, 0.45, l);
  c += hiW * (hi / 100.0) * 0.35;
  c += shW * (sh / 100.0) * 0.35;
  return clamp(c, 0.0, 1.0);
}

vec3 applyWhitesBlacks(vec3 c, float wh, float blk) {
  float l = lum(c);
  c += smoothstep(0.7, 1.0, l) * (wh / 100.0) * 0.25;
  c += (1.0 - smoothstep(0.0, 0.25, l)) * (blk / 100.0) * 0.25;
  return clamp(c, 0.0, 1.0);
}

vec3 saturateVibrance(vec3 c, float sat, float vib) {
  float l = lum(c);
  vec3 gray = vec3(l);
  c = mix(gray, c, 1.0 + sat / 100.0);
  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float chroma = mx - mn;
  float vibMask = 1.0 - smoothstep(0.0, 0.5, chroma);
  c = mix(gray, c, 1.0 + (vib / 100.0) * vibMask);
  return clamp(c, 0.0, 1.0);
}

vec3 rgb2hsv(vec3 c) {
  float cMax = max(c.r, max(c.g, c.b));
  float cMin = min(c.r, min(c.g, c.b));
  float d = cMax - cMin;
  float h = 0.0;
  if (d > 1e-5) {
    if (cMax == c.r) h = mod((c.g - c.b) / d, 6.0);
    else if (cMax == c.g) h = (c.b - c.r) / d + 2.0;
    else h = (c.r - c.g) / d + 4.0;
    h /= 6.0;
  }
  float s = cMax <= 0.0 ? 0.0 : d / cMax;
  return vec3(h, s, cMax);
}

vec3 hsv2rgb(vec3 c) {
  float h = c.x * 6.0;
  float s = c.y;
  float v = c.z;
  float f = fract(h);
  float p = v * (1.0 - s);
  float q = v * (1.0 - f * s);
  float t = v * (1.0 - (1.0 - f) * s);
  if (h < 1.0) return vec3(v, t, p);
  if (h < 2.0) return vec3(q, v, p);
  if (h < 3.0) return vec3(p, v, t);
  if (h < 4.0) return vec3(p, q, v);
  if (h < 5.0) return vec3(t, p, v);
  return vec3(v, p, q);
}

vec3 hslAdjust(vec3 c) {
  vec3 hsv = rgb2hsv(c);
  float h = hsv.x;
  float hubs[8];
  hubs[0]=0.0; hubs[1]=0.08; hubs[2]=0.16; hubs[3]=0.33;
  hubs[4]=0.50; hubs[5]=0.66; hubs[6]=0.75; hubs[7]=0.91;
  vec3 adj[8];
  adj[0]=uHslRed; adj[1]=uHslOrange; adj[2]=uHslYellow; adj[3]=uHslGreen;
  adj[4]=uHslAqua; adj[5]=uHslBlue; adj[6]=uHslPurple; adj[7]=uHslMagenta;
  vec3 sum = vec3(0.0);
  float wsum = 0.0;
  for (int i = 0; i < 8; i++) {
    float dh = abs(h - hubs[i]);
    dh = min(dh, 1.0 - dh);
    float w = exp(-dh * dh * 80.0);
    sum += adj[i] * w;
    wsum += w;
  }
  vec3 a = sum / max(wsum, 1e-3);
  hsv.x = fract(hsv.x + a.x / 200.0);
  hsv.y = clamp(hsv.y * (1.0 + a.y / 100.0), 0.0, 1.0);
  hsv.z = clamp(hsv.z * (1.0 + a.z / 200.0), 0.0, 1.0);
  return hsv2rgb(hsv);
}

vec3 colorBalance(vec3 c, vec3 sh, vec3 mid, vec3 hi) {
  float l = lum(c);
  float wSh = 1.0 - smoothstep(0.0, 0.5, l);
  float wHi = smoothstep(0.5, 1.0, l);
  float wMid = 1.0 - wSh - wHi;
  c += (sh / 100.0) * 0.15 * wSh;
  c += (mid / 100.0) * 0.15 * wMid;
  c += (hi / 100.0) * 0.15 * wHi;
  return clamp(c, 0.0, 1.0);
}

vec3 applyLut(vec3 c) {
  float scale = (uLutSize - 1.0) / uLutSize;
  float offset = 0.5 / uLutSize;
  return texture(uLut, c * scale + offset).rgb;
}

vec3 vignette(vec3 c, float amount) {
  vec2 p = vUv * 2.0 - 1.0;
  float r = dot(p, p);
  float v = 1.0 - (amount / 100.0) * smoothstep(0.2, 1.4, r);
  return c * v;
}

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 filmGrain(vec3 c, float intensity, float size) {
  float s = max(size, 1.0);
  vec2 gUv = floor(vUv * uResolution / s);
  float n = hash(gUv) - 0.5;
  float l = lum(c);
  float shade = mix(1.4, 0.6, l);
  return clamp(c + n * (intensity / 100.0) * 0.35 * shade, 0.0, 1.0);
}

vec3 toneBlock(vec3 c) {
  c = applyExposure(c, uExposure);
  c = applyContrast(c, uContrast);
  c = applyHiSh(c, uHighlights, uShadows);
  c = applyWhitesBlacks(c, uWhites, uBlacks);
  return c;
}

vec3 colorBlock(vec3 c) {
  c = saturateVibrance(c, uSaturation, uVibrance);
  c = hslAdjust(c);
  c = colorBalance(c, uBalShadows, uBalMids, uBalHighlights);
  return c;
}

void main() {
  vec3 c = texture(uImage, vUv).rgb;
  if (uShowBefore == 1) {
    fragColor = vec4(c, 1.0);
    return;
  }
  c = applyWB(c, uTemp, uTint);
  if (uLutStage == 0) {
    c = applyLut(c);
    c = toneBlock(c);
    c = colorBlock(c);
  } else {
    c = toneBlock(c);
    c = colorBlock(c);
    c = applyLut(c);
  }
  c = vignette(c, uVignette);
  c = filmGrain(c, uGrainIntensity, uGrainSize);
  fragColor = vec4(c, 1.0);
}`;

// web/presets-data.js
var PRESETS = [
  {
    preset_id: "fuji.acros_approx",
    camera_system: "fujifilm",
    look_name: "ACROS Approx",
    display_label: "ACROS",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: true,
    parent_preset_id: null,
    tags: [
      "fujifilm",
      "punchy"
    ],
    color_science: {
      color_shifts: "B&W with rich midtone separation; slight yellow filter bias for skin.",
      dynamic_range: "Deep blacks with retained highlight texture.",
      tone_curve: "Extended toe; strong mid contrast.",
      film_grain: "Structured: coarser in shadows, finer in highlights (ACROS-like).",
      skin_bias: "n/a",
      contrast_character: "punchy"
    },
    default_sliders: {
      exposure: 0,
      contrast: 28,
      highlights: -20,
      shadows: -5,
      whites: 5,
      blacks: -30,
      temp: 0,
      tint: 0,
      vibrance: -100,
      saturation: -100,
      vignette: 12,
      grain_intensity: 35,
      grain_size: 40,
      hsl: {
        red: {
          h: 0,
          s: 0,
          l: 8
        },
        orange: {
          h: 0,
          s: 0,
          l: 10
        },
        yellow: {
          h: 0,
          s: 0,
          l: 12
        },
        green: {
          h: 0,
          s: 0,
          l: -5
        },
        aqua: {
          h: 0,
          s: 0,
          l: 0
        },
        blue: {
          h: 0,
          s: 0,
          l: -10
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: 0,
          l: 5
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 0
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "fuji.astia_approx",
    camera_system: "fujifilm",
    look_name: "Astia / Soft Approx",
    display_label: "Astia / Soft",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "fujifilm",
      "soft"
    ],
    color_science: {
      color_shifts: "Soft pastels; gentle greens; flattering skin luminance.",
      dynamic_range: "Highlights compressed softly; lifted shadow floor.",
      tone_curve: "Low contrast S; smooth highlight shoulder.",
      film_grain: "Very fine, soft.",
      skin_bias: "warm_cream",
      contrast_character: "soft"
    },
    default_sliders: {
      exposure: 0.15,
      contrast: -10,
      highlights: -25,
      shadows: 20,
      whites: -10,
      blacks: 10,
      temp: 8,
      tint: 3,
      vibrance: 8,
      saturation: -5,
      vignette: 4,
      grain_intensity: 8,
      grain_size: 22,
      hsl: {
        red: {
          h: 5,
          s: -5,
          l: 5
        },
        orange: {
          h: 0,
          s: -5,
          l: 8
        },
        yellow: {
          h: 0,
          s: -8,
          l: 5
        },
        green: {
          h: 0,
          s: -12,
          l: 5
        },
        aqua: {
          h: 0,
          s: -5,
          l: 0
        },
        blue: {
          h: 0,
          s: -8,
          l: 5
        },
        purple: {
          h: 0,
          s: -5,
          l: 0
        },
        magenta: {
          h: 0,
          s: -5,
          l: 5
        }
      },
      color_balance: {
        shadows: {
          r: 3,
          g: 1,
          b: 0
        },
        midtones: {
          r: 4,
          g: 2,
          b: 0
        },
        highlights: {
          r: 5,
          g: 3,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "fuji.classic_chrome_approx",
    camera_system: "fujifilm",
    look_name: "Classic Chrome Approx",
    display_label: "Classic Chrome",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "fujifilm",
      "hard_shadows"
    ],
    color_science: {
      color_shifts: "Muted overall chroma; blues pushed deep/cyan-leaning for skies; greens slightly desaturated toward olive; reds restrained.",
      dynamic_range: "Protect midtones; crush shadow toe slightly for documentary edge.",
      tone_curve: "Harder contrast in lower third; restrained highlight shoulder.",
      film_grain: "Fine, even grain; subtle in midtones.",
      skin_bias: "desaturated",
      contrast_character: "hard_shadows"
    },
    default_sliders: {
      exposure: 0,
      contrast: 18,
      highlights: -15,
      shadows: -10,
      whites: -5,
      blacks: -20,
      temp: -5,
      tint: 0,
      vibrance: -10,
      saturation: -18,
      vignette: 8,
      grain_intensity: 12,
      grain_size: 25,
      hsl: {
        red: {
          h: -5,
          s: -12,
          l: -2
        },
        orange: {
          h: 0,
          s: -8,
          l: 0
        },
        yellow: {
          h: 5,
          s: -10,
          l: 0
        },
        green: {
          h: -8,
          s: -20,
          l: -5
        },
        aqua: {
          h: 5,
          s: -5,
          l: 0
        },
        blue: {
          h: -12,
          s: 15,
          l: -8
        },
        purple: {
          h: 0,
          s: -10,
          l: 0
        },
        magenta: {
          h: 0,
          s: -8,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: -2,
          g: 0,
          b: 4
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 2,
          g: 1,
          b: -2
        }
      }
    }
  },
  {
    preset_id: "fuji.provia_approx",
    camera_system: "fujifilm",
    look_name: "Provia / Standard Approx",
    display_label: "Provia / Standard",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "early",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "fujifilm",
      "balanced"
    ],
    color_science: {
      color_shifts: "Near-neutral, true-to-life; mild green accuracy; balanced skin.",
      dynamic_range: "Wide, photographic; gentle highlight roll-off.",
      tone_curve: "Mild S; midtone linearity prioritized.",
      film_grain: "Minimal.",
      skin_bias: "neutral",
      contrast_character: "balanced"
    },
    default_sliders: {
      exposure: 0,
      contrast: 8,
      highlights: -8,
      shadows: 5,
      whites: 0,
      blacks: -5,
      temp: 0,
      tint: 0,
      vibrance: 5,
      saturation: 0,
      vignette: 0,
      grain_intensity: 4,
      grain_size: 20,
      hsl: {
        red: {
          h: 0,
          s: 0,
          l: 0
        },
        orange: {
          h: 0,
          s: 2,
          l: 2
        },
        yellow: {
          h: 0,
          s: 0,
          l: 0
        },
        green: {
          h: 0,
          s: 5,
          l: 0
        },
        aqua: {
          h: 0,
          s: 0,
          l: 0
        },
        blue: {
          h: 0,
          s: 5,
          l: 0
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: 0,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 0
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "fuji.velvia_50_approx",
    camera_system: "fujifilm",
    look_name: "Velvia 50 Approx",
    display_label: "Velvia 50",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "fujifilm",
      "punchy"
    ],
    color_science: {
      color_shifts: "High saturation landscape palette; greens vivid; reds/oranges punchy; deep blue skies.",
      dynamic_range: "Narrower usable DR feel; clipped speculars OK for drama.",
      tone_curve: "Steep S-curve; sharp local contrast.",
      film_grain: "Very fine; mostly invisible at web sizes.",
      skin_bias: "cool",
      contrast_character: "punchy"
    },
    default_sliders: {
      exposure: 0,
      contrast: 35,
      highlights: -5,
      shadows: -15,
      whites: 10,
      blacks: -25,
      temp: 5,
      tint: -5,
      vibrance: 25,
      saturation: 30,
      vignette: 5,
      grain_intensity: 6,
      grain_size: 15,
      hsl: {
        red: {
          h: 5,
          s: 25,
          l: 0
        },
        orange: {
          h: 0,
          s: 15,
          l: 0
        },
        yellow: {
          h: -5,
          s: 20,
          l: 5
        },
        green: {
          h: 8,
          s: 30,
          l: 0
        },
        aqua: {
          h: 0,
          s: 10,
          l: 0
        },
        blue: {
          h: -10,
          s: 25,
          l: -5
        },
        purple: {
          h: 0,
          s: 10,
          l: 0
        },
        magenta: {
          h: 0,
          s: 15,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 2,
          b: 4
        },
        midtones: {
          r: 2,
          g: 0,
          b: -2
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "hasselblad.hncs_approx",
    camera_system: "hasselblad",
    look_name: "HNCS Approx",
    display_label: "HNCS",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "early",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "hasselblad",
      "soft"
    ],
    color_science: {
      color_shifts: "Ultra-natural; absolute color accuracy goal; minimal cast on skin.",
      dynamic_range: "Very smooth highlight roll-off; medium-format feel.",
      tone_curve: "Gentle; tonal smoothness over punch.",
      film_grain: "None / negligible.",
      skin_bias: "neutral",
      contrast_character: "soft"
    },
    default_sliders: {
      exposure: 0,
      contrast: 5,
      highlights: -22,
      shadows: 12,
      whites: -5,
      blacks: 0,
      temp: 0,
      tint: 0,
      vibrance: 0,
      saturation: -3,
      vignette: 0,
      grain_intensity: 0,
      grain_size: 0,
      hsl: {
        red: {
          h: 0,
          s: -2,
          l: 2
        },
        orange: {
          h: 0,
          s: -3,
          l: 4
        },
        yellow: {
          h: 0,
          s: 0,
          l: 0
        },
        green: {
          h: 0,
          s: 0,
          l: 0
        },
        aqua: {
          h: 0,
          s: 0,
          l: 0
        },
        blue: {
          h: 0,
          s: 0,
          l: 0
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: -2,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 0
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "kodak.ektar_100_approx",
    camera_system: "kodak",
    look_name: "Ektar 100 Approx",
    display_label: "Ektar 100",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "kodak",
      "micro_contrast"
    ],
    color_science: {
      color_shifts: "Ultra-vivid reds and blues; clean greens; high chroma without mud.",
      dynamic_range: "Tight, snappy; micro-contrast emphasized.",
      tone_curve: "Punchy S; crisp midtones.",
      film_grain: "Ultra-fine; nearly invisible.",
      skin_bias: "cool",
      contrast_character: "micro_contrast"
    },
    default_sliders: {
      exposure: 0,
      contrast: 30,
      highlights: -8,
      shadows: -12,
      whites: 12,
      blacks: -18,
      temp: 5,
      tint: -3,
      vibrance: 20,
      saturation: 22,
      vignette: 4,
      grain_intensity: 5,
      grain_size: 12,
      hsl: {
        red: {
          h: 5,
          s: 30,
          l: 0
        },
        orange: {
          h: 0,
          s: 15,
          l: 0
        },
        yellow: {
          h: -5,
          s: 10,
          l: 0
        },
        green: {
          h: 0,
          s: 18,
          l: 0
        },
        aqua: {
          h: 0,
          s: 10,
          l: 0
        },
        blue: {
          h: -8,
          s: 28,
          l: -3
        },
        purple: {
          h: 0,
          s: 12,
          l: 0
        },
        magenta: {
          h: 0,
          s: 15,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 3
        },
        midtones: {
          r: 2,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "kodak.gold_200_approx",
    camera_system: "kodak",
    look_name: "Gold 200 Approx",
    display_label: "Gold 200",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "kodak",
      "balanced"
    ],
    color_science: {
      color_shifts: "Golden warmth; amber/yellow saturation; nostalgic consumer-print feel.",
      dynamic_range: "Mid DR; sunny-day bias.",
      tone_curve: "Moderate contrast; warm highlight bias.",
      film_grain: "Moderate consumer grain.",
      skin_bias: "warm_cream",
      contrast_character: "balanced"
    },
    default_sliders: {
      exposure: 0.1,
      contrast: 15,
      highlights: -10,
      shadows: 5,
      whites: 5,
      blacks: -8,
      temp: 28,
      tint: 6,
      vibrance: 15,
      saturation: 12,
      vignette: 10,
      grain_intensity: 28,
      grain_size: 35,
      hsl: {
        red: {
          h: 8,
          s: 10,
          l: 0
        },
        orange: {
          h: 5,
          s: 15,
          l: 5
        },
        yellow: {
          h: 0,
          s: 25,
          l: 8
        },
        green: {
          h: 10,
          s: -5,
          l: 0
        },
        aqua: {
          h: 0,
          s: -15,
          l: 0
        },
        blue: {
          h: 8,
          s: -10,
          l: 0
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 5,
          s: 5,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 6,
          g: 2,
          b: -4
        },
        midtones: {
          r: 12,
          g: 6,
          b: -6
        },
        highlights: {
          r: 16,
          g: 10,
          b: -8
        }
      }
    }
  },
  {
    preset_id: "kodak.portra_160_approx",
    camera_system: "kodak",
    look_name: "Portra 160 Approx",
    display_label: "Portra 160",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "kodak",
      "soft"
    ],
    color_science: {
      color_shifts: "Warm pastel highlights; yellow-red bias; creamy skin; restrained greens.",
      dynamic_range: "Very wide latitude; soft highlight bloom.",
      tone_curve: "Gentle S; lifted blacks slightly.",
      film_grain: "Extremely fine.",
      skin_bias: "warm_cream",
      contrast_character: "soft"
    },
    default_sliders: {
      exposure: 0.1,
      contrast: -5,
      highlights: -20,
      shadows: 15,
      whites: -5,
      blacks: 8,
      temp: 18,
      tint: 5,
      vibrance: -5,
      saturation: -8,
      vignette: 6,
      grain_intensity: 10,
      grain_size: 18,
      hsl: {
        red: {
          h: 8,
          s: -5,
          l: 5
        },
        orange: {
          h: 5,
          s: -8,
          l: 10
        },
        yellow: {
          h: 5,
          s: 5,
          l: 8
        },
        green: {
          h: -5,
          s: -15,
          l: 0
        },
        aqua: {
          h: 0,
          s: -10,
          l: 0
        },
        blue: {
          h: 5,
          s: -12,
          l: 5
        },
        purple: {
          h: 0,
          s: -5,
          l: 0
        },
        magenta: {
          h: 5,
          s: -5,
          l: 5
        }
      },
      color_balance: {
        shadows: {
          r: 4,
          g: 2,
          b: 0
        },
        midtones: {
          r: 8,
          g: 3,
          b: -2
        },
        highlights: {
          r: 12,
          g: 6,
          b: -4
        }
      }
    }
  },
  {
    preset_id: "kodak.portra_400_approx",
    camera_system: "kodak",
    look_name: "Portra 400 Approx",
    display_label: "Portra 400",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "kodak",
      "balanced"
    ],
    color_science: {
      color_shifts: "Same warm cream family as 160 with slightly more midtone density.",
      dynamic_range: "Wide; forgiving; soft speculars.",
      tone_curve: "Gentle S with a touch more mid contrast than 160.",
      film_grain: "Visible fine grain; organic.",
      skin_bias: "warm_cream",
      contrast_character: "balanced"
    },
    default_sliders: {
      exposure: 0.05,
      contrast: 5,
      highlights: -18,
      shadows: 12,
      whites: -5,
      blacks: 5,
      temp: 16,
      tint: 4,
      vibrance: -3,
      saturation: -5,
      vignette: 8,
      grain_intensity: 22,
      grain_size: 28,
      hsl: {
        red: {
          h: 6,
          s: -3,
          l: 3
        },
        orange: {
          h: 4,
          s: -5,
          l: 8
        },
        yellow: {
          h: 4,
          s: 4,
          l: 5
        },
        green: {
          h: -5,
          s: -12,
          l: -2
        },
        aqua: {
          h: 0,
          s: -8,
          l: 0
        },
        blue: {
          h: 4,
          s: -10,
          l: 2
        },
        purple: {
          h: 0,
          s: -5,
          l: 0
        },
        magenta: {
          h: 4,
          s: -3,
          l: 3
        }
      },
      color_balance: {
        shadows: {
          r: 3,
          g: 1,
          b: 0
        },
        midtones: {
          r: 7,
          g: 2,
          b: -2
        },
        highlights: {
          r: 10,
          g: 5,
          b: -3
        }
      }
    }
  },
  {
    preset_id: "kodak.trix_400_approx",
    camera_system: "kodak",
    look_name: "Tri-X 400 Approx",
    display_label: "Tri-X 400",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: true,
    parent_preset_id: null,
    tags: [
      "kodak",
      "punchy"
    ],
    color_science: {
      color_shifts: "Classic silver halide B&W; deep blacks; midtone bite.",
      dynamic_range: "Pushable shadows; hard black point.",
      tone_curve: "Aggressive toe + strong mid slope.",
      film_grain: "Iconic coarse grain; stronger in shadows.",
      skin_bias: "n/a",
      contrast_character: "punchy"
    },
    default_sliders: {
      exposure: 0,
      contrast: 40,
      highlights: -15,
      shadows: -20,
      whites: 8,
      blacks: -40,
      temp: 0,
      tint: 0,
      vibrance: -100,
      saturation: -100,
      vignette: 18,
      grain_intensity: 55,
      grain_size: 55,
      hsl: {
        red: {
          h: 0,
          s: 0,
          l: 5
        },
        orange: {
          h: 0,
          s: 0,
          l: 8
        },
        yellow: {
          h: 0,
          s: 0,
          l: 10
        },
        green: {
          h: 0,
          s: 0,
          l: -8
        },
        aqua: {
          h: 0,
          s: 0,
          l: -5
        },
        blue: {
          h: 0,
          s: 0,
          l: -15
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: 0,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 0
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "leica.cl_monochrom_approx",
    camera_system: "leica",
    look_name: "Leica CL / Vintage Monochrom Approx",
    display_label: "Leica CL / Vintage Monochrom",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: true,
    parent_preset_id: null,
    tags: [
      "leica",
      "soft"
    ],
    color_science: {
      color_shifts: "Deep tonal blacks; creamy midtone roll-off; soft highlight bloom.",
      dynamic_range: "Expanded midtones; gentle highlight shoulder.",
      tone_curve: "Creamy mid slope; deep black point without hard clip feel.",
      film_grain: "Fine vintage mono grain.",
      skin_bias: "n/a",
      contrast_character: "soft"
    },
    default_sliders: {
      exposure: 0.05,
      contrast: 12,
      highlights: -28,
      shadows: 10,
      whites: -8,
      blacks: -25,
      temp: 0,
      tint: 0,
      vibrance: -100,
      saturation: -100,
      vignette: 20,
      grain_intensity: 25,
      grain_size: 32,
      hsl: {
        red: {
          h: 0,
          s: 0,
          l: 5
        },
        orange: {
          h: 0,
          s: 0,
          l: 8
        },
        yellow: {
          h: 0,
          s: 0,
          l: 5
        },
        green: {
          h: 0,
          s: 0,
          l: 0
        },
        aqua: {
          h: 0,
          s: 0,
          l: 0
        },
        blue: {
          h: 0,
          s: 0,
          l: -8
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: 0,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 0
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 0,
          g: 0,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "leica.m_system_approx",
    camera_system: "leica",
    look_name: "Leica M System Approx",
    display_label: "Leica M System",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "early",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "leica",
      "micro_contrast"
    ],
    color_science: {
      color_shifts: "Rich but controlled; high micro-contrast; optical 'separation' feel.",
      dynamic_range: "High DR with crisp highlight edge.",
      tone_curve: "Strong local contrast; deep but open shadows.",
      film_grain: "Digital-clean; optional micro grain only.",
      skin_bias: "neutral",
      contrast_character: "micro_contrast"
    },
    default_sliders: {
      exposure: 0,
      contrast: 22,
      highlights: -12,
      shadows: 8,
      whites: 8,
      blacks: -15,
      temp: 2,
      tint: 0,
      vibrance: 5,
      saturation: -5,
      vignette: 15,
      grain_intensity: 8,
      grain_size: 20,
      hsl: {
        red: {
          h: 0,
          s: 5,
          l: 0
        },
        orange: {
          h: 0,
          s: 0,
          l: 2
        },
        yellow: {
          h: 0,
          s: -5,
          l: 0
        },
        green: {
          h: -5,
          s: -10,
          l: -3
        },
        aqua: {
          h: 0,
          s: 0,
          l: 0
        },
        blue: {
          h: -5,
          s: 8,
          l: -3
        },
        purple: {
          h: 0,
          s: 0,
          l: 0
        },
        magenta: {
          h: 0,
          s: 0,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 2
        },
        midtones: {
          r: 0,
          g: 0,
          b: 0
        },
        highlights: {
          r: 2,
          g: 1,
          b: 0
        }
      }
    }
  },
  {
    preset_id: "leica.street_look_approx",
    camera_system: "leica",
    look_name: "Leica Street Look Approx",
    display_label: "Leica Street Look",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "leica",
      "balanced"
    ],
    color_science: {
      color_shifts: "Muted earthy greens; subtle warm highlights; desaturated urban palette.",
      dynamic_range: "Documentary DR; midtone priority.",
      tone_curve: "Moderate contrast; slight fade optional via lifted blacks.",
      film_grain: "Light street grain.",
      skin_bias: "warm_cream",
      contrast_character: "balanced"
    },
    default_sliders: {
      exposure: 0,
      contrast: 12,
      highlights: -15,
      shadows: 5,
      whites: 0,
      blacks: 5,
      temp: 10,
      tint: 2,
      vibrance: -12,
      saturation: -15,
      vignette: 12,
      grain_intensity: 18,
      grain_size: 30,
      hsl: {
        red: {
          h: 5,
          s: -5,
          l: 0
        },
        orange: {
          h: 3,
          s: -5,
          l: 3
        },
        yellow: {
          h: 5,
          s: -8,
          l: 0
        },
        green: {
          h: -10,
          s: -25,
          l: -5
        },
        aqua: {
          h: 0,
          s: -15,
          l: 0
        },
        blue: {
          h: 0,
          s: -10,
          l: 0
        },
        purple: {
          h: 0,
          s: -8,
          l: 0
        },
        magenta: {
          h: 0,
          s: -5,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 0,
          b: 3
        },
        midtones: {
          r: 4,
          g: 1,
          b: -2
        },
        highlights: {
          r: 8,
          g: 4,
          b: -2
        }
      }
    }
  },
  {
    preset_id: "sony.fl_approx",
    camera_system: "sony",
    look_name: "Creative Look FL Approx",
    display_label: "Creative Look FL",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "sony",
      "matte"
    ],
    color_science: {
      color_shifts: "Film-like: desaturated greens; warm cyan highlights; matte blacks.",
      dynamic_range: "Compressed toe (matte); soft highlights.",
      tone_curve: "Lifted black point; gentle mid slope.",
      film_grain: "Light cinematic grain.",
      skin_bias: "warm_cream",
      contrast_character: "matte"
    },
    default_sliders: {
      exposure: 0,
      contrast: -5,
      highlights: -18,
      shadows: 8,
      whites: -5,
      blacks: 18,
      temp: 8,
      tint: -4,
      vibrance: -8,
      saturation: -12,
      vignette: 8,
      grain_intensity: 20,
      grain_size: 28,
      hsl: {
        red: {
          h: 5,
          s: -5,
          l: 0
        },
        orange: {
          h: 0,
          s: -5,
          l: 3
        },
        yellow: {
          h: 0,
          s: -8,
          l: 0
        },
        green: {
          h: -5,
          s: -28,
          l: -5
        },
        aqua: {
          h: 10,
          s: 5,
          l: 5
        },
        blue: {
          h: 5,
          s: -5,
          l: 5
        },
        purple: {
          h: 0,
          s: -5,
          l: 0
        },
        magenta: {
          h: 0,
          s: -5,
          l: 0
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 2,
          b: 4
        },
        midtones: {
          r: 2,
          g: 0,
          b: 2
        },
        highlights: {
          r: 4,
          g: 6,
          b: 8
        }
      }
    }
  },
  {
    preset_id: "sony.in_approx",
    camera_system: "sony",
    look_name: "Creative Look IN Approx",
    display_label: "Creative Look IN",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "sony",
      "soft"
    ],
    color_science: {
      color_shifts: "Instant-film: low contrast, pastel, soft whites.",
      dynamic_range: "Heavily compressed; dreamy highlights.",
      tone_curve: "Flat; lifted blacks and crushed top end softly.",
      film_grain: "Instant-print texture.",
      skin_bias: "warm_cream",
      contrast_character: "soft"
    },
    default_sliders: {
      exposure: 0.2,
      contrast: -25,
      highlights: -30,
      shadows: 25,
      whites: -15,
      blacks: 25,
      temp: 12,
      tint: 5,
      vibrance: 5,
      saturation: -10,
      vignette: 15,
      grain_intensity: 30,
      grain_size: 40,
      hsl: {
        red: {
          h: 5,
          s: -8,
          l: 8
        },
        orange: {
          h: 0,
          s: -10,
          l: 10
        },
        yellow: {
          h: 0,
          s: -5,
          l: 8
        },
        green: {
          h: 0,
          s: -15,
          l: 5
        },
        aqua: {
          h: 0,
          s: -8,
          l: 5
        },
        blue: {
          h: 5,
          s: -12,
          l: 8
        },
        purple: {
          h: 0,
          s: -5,
          l: 5
        },
        magenta: {
          h: 0,
          s: -5,
          l: 8
        }
      },
      color_balance: {
        shadows: {
          r: 4,
          g: 2,
          b: 2
        },
        midtones: {
          r: 6,
          g: 3,
          b: 0
        },
        highlights: {
          r: 8,
          g: 6,
          b: 2
        }
      }
    }
  },
  {
    preset_id: "sony.scinetone_mobile_approx",
    camera_system: "sony",
    look_name: "S-Cinetone Mobile Approx",
    display_label: "S-Cinetone Mobile",
    version: 1,
    lut_asset_path: "luts/identity_33.cube",
    lut_format: "cube_3d",
    lut_size: 33,
    lut_stage: "late",
    monochrome: false,
    parent_preset_id: null,
    tags: [
      "sony",
      "balanced"
    ],
    color_science: {
      color_shifts: "Cinematic midtone roll-off; pleasing portrait tones; controlled saturation.",
      dynamic_range: "Soft highlight shoulder; broadcast-safe feel.",
      tone_curve: "Cinema-inspired gentle S; midtone priority.",
      film_grain: "Optional light cinema grain.",
      skin_bias: "warm_cream",
      contrast_character: "balanced"
    },
    default_sliders: {
      exposure: 0,
      contrast: 5,
      highlights: -25,
      shadows: 10,
      whites: -8,
      blacks: 5,
      temp: 6,
      tint: 2,
      vibrance: 5,
      saturation: -8,
      vignette: 5,
      grain_intensity: 12,
      grain_size: 22,
      hsl: {
        red: {
          h: 3,
          s: -5,
          l: 3
        },
        orange: {
          h: 0,
          s: -3,
          l: 6
        },
        yellow: {
          h: 0,
          s: -5,
          l: 0
        },
        green: {
          h: -5,
          s: -15,
          l: -3
        },
        aqua: {
          h: 5,
          s: -5,
          l: 0
        },
        blue: {
          h: 0,
          s: -5,
          l: 0
        },
        purple: {
          h: 0,
          s: -5,
          l: 0
        },
        magenta: {
          h: 0,
          s: -3,
          l: 2
        }
      },
      color_balance: {
        shadows: {
          r: 0,
          g: 1,
          b: 3
        },
        midtones: {
          r: 4,
          g: 1,
          b: 0
        },
        highlights: {
          r: 5,
          g: 3,
          b: 0
        }
      }
    }
  }
];

// web/app.js
var BRANDS = ["fujifilm", "kodak", "leica", "hasselblad", "sony"];
var LIGHT_KEYS = [
  ["exposure", "Exposure", -3, 3, 0.05],
  ["contrast", "Contrast", -100, 100, 1],
  ["highlights", "Highlights", -100, 100, 1],
  ["shadows", "Shadows", -100, 100, 1],
  ["whites", "Whites", -100, 100, 1],
  ["blacks", "Blacks", -100, 100, 1]
];
var COLOR_KEYS = [
  ["temp", "Temp", -100, 100, 1],
  ["tint", "Tint", -100, 100, 1],
  ["vibrance", "Vibrance", -100, 100, 1],
  ["saturation", "Saturation", -100, 100, 1]
];
var FX_KEYS = [
  ["vignette", "Vignette", 0, 100, 1],
  ["grain_intensity", "Grain", 0, 100, 1],
  ["grain_size", "Grain Size", 0, 100, 1]
];
var PRESET_SWATCHES = {
  "fuji.classic_chrome_approx": "linear-gradient(135deg, #2b394a, #9c8470)",
  "fuji.velvia_50_approx": "linear-gradient(135deg, #1b5e20, #e91e63)",
  "fuji.provia_approx": "linear-gradient(135deg, #455a64, #90a4ae)",
  "fuji.astia_approx": "linear-gradient(135deg, #d48b72, #e0a996)",
  "fuji.acros_approx": "linear-gradient(135deg, #111111, #888888)",
  "kodak.portra_160_approx": "linear-gradient(135deg, #e4be9e, #c28864)",
  "kodak.portra_400_approx": "linear-gradient(135deg, #dfa97b, #a66a46)",
  "kodak.gold_200_approx": "linear-gradient(135deg, #e5a93b, #b3541e)",
  "kodak.ektar_100_approx": "linear-gradient(135deg, #0d47a1, #b71c1c)",
  "kodak.trix_400_approx": "linear-gradient(135deg, #050505, #555555)",
  "leica.m_system_approx": "linear-gradient(135deg, #212121, #b0bec5)",
  "leica.cl_monochrom_approx": "linear-gradient(135deg, #141414, #757575)",
  "leica.street_look_approx": "linear-gradient(135deg, #37474f, #6d4c41)",
  "hasselblad.hncs_approx": "linear-gradient(135deg, #37474f, #eceff1)",
  "sony.fl_approx": "linear-gradient(135deg, #1f2d3d, #8d6e63)",
  "sony.in_approx": "linear-gradient(135deg, #546e7a, #cfd8dc)",
  "sony.scinetone_mobile_approx": "linear-gradient(135deg, #263238, #ff8a65)"
};
var state = {
  presets: PRESETS,
  brand: "fujifilm",
  active: null,
  base: null,
  deltas: null,
  composed: null,
  tab: "light",
  mode: "presets",
  showBefore: false
};
var el = {
  viewport: document.getElementById("viewport"),
  canvas: document.getElementById("gl"),
  status: document.getElementById("status"),
  presetBadge: document.getElementById("preset-badge"),
  beforeBadge: document.getElementById("before-badge"),
  canvasHint: document.getElementById("canvas-hint"),
  brands: document.getElementById("brands"),
  presets: document.getElementById("presets"),
  tabs: document.getElementById("tabs"),
  sliders: document.getElementById("sliders"),
  file: document.getElementById("file"),
  reset: document.getElementById("reset"),
  export: document.getElementById("export"),
  before: document.getElementById("before"),
  modePresets: document.getElementById("mode-presets"),
  modeAdjust: document.getElementById("mode-adjust"),
  panelPresets: document.getElementById("panel-presets"),
  panelAdjust: document.getElementById("panel-adjust")
};
var gl = el.canvas.getContext("webgl2", { antialias: false, alpha: false, preserveDrawingBuffer: true });
if (!gl)
  throw new Error("WebGL2 required");
var prog = createProgram(gl, VERT, FRAG);
var locs = cacheUniforms(gl, prog);
var quad = createQuad(gl, prog);
var imageTex = null;
var lutTex = null;
var lutSize = 17;
var originalSourceCanvas = null;
function createShader(type, src) {
  const s = gl.createShader(type);
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    throw new Error(gl.getShaderInfoLog(s) || "shader compile failed");
  }
  return s;
}
function createProgram(glCtx, vs, fs) {
  const p = glCtx.createProgram();
  glCtx.attachShader(p, createShader(glCtx.VERTEX_SHADER, vs));
  glCtx.attachShader(p, createShader(glCtx.FRAGMENT_SHADER, fs));
  glCtx.linkProgram(p);
  if (!glCtx.getProgramParameter(p, glCtx.LINK_STATUS)) {
    throw new Error(glCtx.getProgramInfoLog(p) || "link failed");
  }
  return p;
}
function createQuad(glCtx, program) {
  const buf = glCtx.createBuffer();
  glCtx.bindBuffer(glCtx.ARRAY_BUFFER, buf);
  glCtx.bufferData(glCtx.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), glCtx.STATIC_DRAW);
  const loc = glCtx.getAttribLocation(program, "aPos");
  glCtx.enableVertexAttribArray(loc);
  glCtx.vertexAttribPointer(loc, 2, glCtx.FLOAT, false, 0, 0);
  return buf;
}
function cacheUniforms(glCtx, program) {
  const names = [
    "uImage",
    "uLut",
    "uLutSize",
    "uLutStage",
    "uShowBefore",
    "uExposure",
    "uContrast",
    "uHighlights",
    "uShadows",
    "uWhites",
    "uBlacks",
    "uTemp",
    "uTint",
    "uVibrance",
    "uSaturation",
    "uVignette",
    "uGrainIntensity",
    "uGrainSize",
    "uResolution",
    "uHslRed",
    "uHslOrange",
    "uHslYellow",
    "uHslGreen",
    "uHslAqua",
    "uHslBlue",
    "uHslPurple",
    "uHslMagenta",
    "uBalShadows",
    "uBalMids",
    "uBalHighlights"
  ];
  const out = {};
  for (const n of names)
    out[n] = glCtx.getUniformLocation(program, n);
  return out;
}
function uploadImageFromCanvas(srcCanvas) {
  originalSourceCanvas = srcCanvas;
  if (!imageTex)
    imageTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, imageTex);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, srcCanvas);
  fitCanvas();
  draw();
}
function uploadLut(cube) {
  const { size, rgb8 } = cubeToRgb8(cube);
  lutSize = size;
  if (!lutTex)
    lutTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_3D, lutTex);
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE);
  gl.texImage3D(gl.TEXTURE_3D, 0, gl.RGB8, size, size, size, 0, gl.RGB, gl.UNSIGNED_BYTE, rgb8);
}
function fitCanvas() {
  if (!originalSourceCanvas)
    return;
  const vpRect = el.viewport.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const aspect = originalSourceCanvas.width / originalSourceCanvas.height;
  let w = vpRect.width;
  let h = w / aspect;
  if (h > vpRect.height) {
    h = vpRect.height;
    w = h * aspect;
  }
  el.canvas.width = Math.round(w * dpr);
  el.canvas.height = Math.round(h * dpr);
  el.canvas.style.width = `${w}px`;
  el.canvas.style.height = `${h}px`;
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
}
function hslVec(triplet) {
  return triplet ? new Float32Array(triplet) : new Float32Array([0, 0, 0]);
}
function balVec(pair) {
  return pair ? new Float32Array([pair[0], pair[1], 0]) : new Float32Array([0, 0, 0]);
}
function draw() {
  if (!state.composed)
    return;
  const s = state.composed;
  gl.useProgram(prog);
  gl.uniform1i(locs.uImage, 0);
  gl.uniform1i(locs.uLut, 1);
  gl.uniform1f(locs.uLutSize, lutSize);
  gl.uniform1i(locs.uLutStage, state.active?.lut_stage === "early" ? 0 : 1);
  gl.uniform1i(locs.uShowBefore, state.showBefore ? 1 : 0);
  gl.uniform1f(locs.uExposure, s.exposure);
  gl.uniform1f(locs.uContrast, s.contrast);
  gl.uniform1f(locs.uHighlights, s.highlights);
  gl.uniform1f(locs.uShadows, s.shadows);
  gl.uniform1f(locs.uWhites, s.whites);
  gl.uniform1f(locs.uBlacks, s.blacks);
  gl.uniform1f(locs.uTemp, s.temp);
  gl.uniform1f(locs.uTint, s.tint);
  gl.uniform1f(locs.uVibrance, s.vibrance);
  gl.uniform1f(locs.uSaturation, s.saturation);
  gl.uniform1f(locs.uVignette, s.vignette);
  gl.uniform1f(locs.uGrainIntensity, s.grain_intensity);
  gl.uniform1f(locs.uGrainSize, s.grain_size);
  gl.uniform2f(locs.uResolution, el.canvas.width, el.canvas.height);
  const h = s.hsl || {};
  gl.uniform3fv(locs.uHslRed, hslVec(h.red));
  gl.uniform3fv(locs.uHslOrange, hslVec(h.orange));
  gl.uniform3fv(locs.uHslYellow, hslVec(h.yellow));
  gl.uniform3fv(locs.uHslGreen, hslVec(h.green));
  gl.uniform3fv(locs.uHslAqua, hslVec(h.aqua));
  gl.uniform3fv(locs.uHslBlue, hslVec(h.blue));
  gl.uniform3fv(locs.uHslPurple, hslVec(h.purple));
  gl.uniform3fv(locs.uHslMagenta, hslVec(h.magenta));
  const cb = s.color_balance || {};
  gl.uniform3fv(locs.uBalShadows, balVec(cb.shadows));
  gl.uniform3fv(locs.uBalMids, balVec(cb.midtones));
  gl.uniform3fv(locs.uBalHighlights, balVec(cb.highlights));
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
}
function recompose() {
  state.composed = composeSliders(state.base, state.deltas, state.active?.slider_ranges);
  draw();
  const label = state.active?.display_label || state.active?.look_name;
  el.presetBadge.textContent = `${label} (${state.active?.camera_system.toUpperCase()})`;
  el.status.textContent = `${label} · ${state.active?.lut_stage.toUpperCase()} LUT`;
}
function applyPreset(preset) {
  state.active = preset;
  state.base = structuredClone(preset.default_sliders);
  state.deltas = zeroSlidersLike(state.base);
  recompose();
  renderPresetChips();
  renderSliders();
}
function renderBrands() {
  el.brands.innerHTML = "";
  for (const b of BRANDS) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = b;
    btn.className = `brand-chip ${b === state.brand ? "active" : ""}`;
    btn.onclick = () => {
      state.brand = b;
      renderBrands();
      renderPresetChips();
      const firstInBrand = state.presets.find((p) => p.camera_system === b);
      if (firstInBrand)
        applyPreset(firstInBrand);
    };
    el.brands.appendChild(btn);
  }
}
function renderPresetChips() {
  el.presets.innerHTML = "";
  const list = state.presets.filter((p) => p.camera_system === state.brand);
  for (const p of list) {
    const card = document.createElement("div");
    card.className = `preset-card ${state.active?.preset_id === p.preset_id ? "active" : ""}`;
    const swatch = document.createElement("div");
    swatch.className = "preset-swatch";
    swatch.style.background = PRESET_SWATCHES[p.preset_id] || "linear-gradient(135deg, #444, #777)";
    const name = document.createElement("span");
    name.className = "preset-card-name";
    name.textContent = p.display_label || p.look_name;
    const sub = document.createElement("span");
    sub.className = "preset-card-sub";
    sub.textContent = `${p.lut_stage} LUT`;
    card.appendChild(swatch);
    card.appendChild(name);
    card.appendChild(sub);
    card.onclick = () => applyPreset(p);
    el.presets.appendChild(card);
  }
}
function renderSliders() {
  const keys = state.tab === "light" ? LIGHT_KEYS : state.tab === "color" ? COLOR_KEYS : FX_KEYS;
  el.sliders.innerHTML = "";
  for (const [key, label, min, max, step] of keys) {
    const row = document.createElement("div");
    row.className = "slider-row";
    const abs = state.composed[key];
    const lbl = document.createElement("span");
    lbl.className = "slider-label";
    lbl.textContent = label;
    const wrap = document.createElement("div");
    wrap.className = "slider-control-wrap";
    const input = document.createElement("input");
    input.type = "range";
    input.className = "slider-input";
    input.min = String(min);
    input.max = String(max);
    input.step = String(step);
    input.value = String(abs);
    const val = document.createElement("span");
    val.className = "slider-val";
    val.textContent = Number(abs).toFixed(step < 1 ? 2 : 0);
    input.oninput = () => {
      const absolute = Number(input.value);
      state.deltas[key] = absolute - (state.base[key] ?? 0);
      recompose();
      val.textContent = absolute.toFixed(step < 1 ? 2 : 0);
    };
    input.ondblclick = () => {
      state.deltas[key] = 0;
      recompose();
      input.value = String(state.composed[key]);
      val.textContent = Number(state.composed[key]).toFixed(step < 1 ? 2 : 0);
    };
    wrap.appendChild(input);
    row.appendChild(lbl);
    row.appendChild(wrap);
    row.appendChild(val);
    el.sliders.appendChild(row);
  }
}
function setMode(mode) {
  state.mode = mode;
  el.modePresets.classList.toggle("active", mode === "presets");
  el.modeAdjust.classList.toggle("active", mode === "adjust");
  el.panelPresets.classList.toggle("active", mode === "presets");
  el.panelAdjust.classList.toggle("active", mode === "adjust");
}
el.modePresets.onclick = () => setMode("presets");
el.modeAdjust.onclick = () => setMode("adjust");
el.tabs.addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-tab]");
  if (!btn)
    return;
  state.tab = btn.dataset.tab;
  for (const b of el.tabs.querySelectorAll("button")) {
    b.classList.toggle("active", b === btn);
  }
  renderSliders();
});
function setBeforeActive(active) {
  state.showBefore = active;
  el.beforeBadge.classList.toggle("visible", active);
  el.before.classList.toggle("holding", active);
  draw();
}
el.before.addEventListener("pointerdown", () => setBeforeActive(true));
el.before.addEventListener("pointerup", () => setBeforeActive(false));
el.before.addEventListener("pointerleave", () => setBeforeActive(false));
el.viewport.addEventListener("pointerdown", (e) => {
  if (e.target === el.canvas)
    setBeforeActive(true);
});
el.viewport.addEventListener("pointerup", () => setBeforeActive(false));
el.viewport.addEventListener("pointercancel", () => setBeforeActive(false));
el.reset.onclick = () => {
  if (!state.base)
    return;
  state.deltas = zeroSlidersLike(state.base);
  recompose();
  renderSliders();
};
el.file.onchange = async () => {
  const file = el.file.files?.[0];
  if (!file)
    return;
  const img = new Image;
  img.onload = () => {
    const c = document.createElement("canvas");
    c.width = img.width;
    c.height = img.height;
    c.getContext("2d").drawImage(img, 0, 0);
    uploadImageFromCanvas(c);
    el.canvasHint.style.display = "none";
  };
  img.src = URL.createObjectURL(file);
};
el.export.onclick = () => {
  if (!originalSourceCanvas)
    return;
  el.status.textContent = "Rendering full-resolution export…";
  const origW = el.canvas.width;
  const origH = el.canvas.height;
  el.canvas.width = originalSourceCanvas.width;
  el.canvas.height = originalSourceCanvas.height;
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
  draw();
  const dataUrl = el.canvas.toDataURL("image/jpeg", 0.95);
  el.canvas.width = origW;
  el.canvas.height = origH;
  gl.viewport(0, 0, origW, origH);
  draw();
  const filename = `looklite_${state.active?.look_name.toLowerCase().replace(/[^a-z0-9]/g, "_")}_${Date.now()}.jpg`;
  if (window.LookLiteNative && typeof window.LookLiteNative.saveImage === "function") {
    window.LookLiteNative.saveImage(dataUrl, filename);
    el.status.textContent = "Saved to Gallery!";
    return;
  }
  const a = document.createElement("a");
  a.href = dataUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  el.status.textContent = "Export downloaded";
};
window.addEventListener("resize", () => {
  fitCanvas();
  draw();
});
function makeDemoImage() {
  const c = document.createElement("canvas");
  c.width = 1200;
  c.height = 900;
  const ctx = c.getContext("2d");
  const sky = ctx.createLinearGradient(0, 0, 0, 500);
  sky.addColorStop(0, "#2b4c6f");
  sky.addColorStop(0.4, "#857265");
  sky.addColorStop(0.8, "#d9823b");
  sky.addColorStop(1, "#f4b251");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, 1200, 500);
  ctx.fillStyle = "#3a3845";
  ctx.beginPath();
  ctx.moveTo(0, 500);
  ctx.lineTo(350, 320);
  ctx.lineTo(650, 420);
  ctx.lineTo(950, 290);
  ctx.lineTo(1200, 500);
  ctx.closePath();
  ctx.fill();
  const lake = ctx.createLinearGradient(0, 500, 0, 750);
  lake.addColorStop(0, "#4a4237");
  lake.addColorStop(0.5, "#30383b");
  lake.addColorStop(1, "#1f2a30");
  ctx.fillStyle = lake;
  ctx.fillRect(0, 500, 1200, 250);
  ctx.fillStyle = "#2d3827";
  ctx.fillRect(0, 750, 1200, 150);
  return c;
}
async function main() {
  uploadLut(identityCube(17));
  uploadImageFromCanvas(makeDemoImage());
  renderBrands();
  const first = state.presets.find((p) => p.camera_system === state.brand) || state.presets[0];
  applyPreset(first);
  setMode("presets");
  el.status.textContent = "Ready · tap preset or open photo";
}
main().catch((err) => {
  console.error(err);
  el.status.textContent = String(err.message || err);
});
