#version 300 es
/** LookLite uber-shader — preview path (GLES 3.0 / WebGL2-compatible).
 *  Order (lut_stage == late): WB → Tone → Vibrance/Sat → HSL → Balance → LUT → Vignette → Grain
 *  Order (lut_stage == early): WB → LUT → Tone → ...
 *  Uniforms are composed absolute slider values (base + deltas), not raw deltas.
 */
precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uImage;
uniform sampler3D uLut;          // 17^3 or 33^3 RGB LUT
uniform float uLutSize;          // e.g. 33.0
uniform int uLutStage;           // 0=early, 1=late
uniform int uShowBefore;         // 1 = bypass grading

uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uTemp;             // -100..100
uniform float uTint;
uniform float uVibrance;
uniform float uSaturation;
uniform float uVignette;
uniform float uGrainIntensity;
uniform float uGrainSize;
uniform vec2  uResolution;

// HSL: packed as vec3 per hue bucket (h,s,l) in -100..100
uniform vec3 uHslRed, uHslOrange, uHslYellow, uHslGreen;
uniform vec3 uHslAqua, uHslBlue, uHslPurple, uHslMagenta;

uniform vec3 uBalShadows, uBalMids, uBalHighlights; // -100..100

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 applyWB(vec3 c, float temp, float tint) {
  float t = temp / 100.0;
  float g = tint / 100.0;
  c.r *= 1.0 + 0.12 * t;
  c.b *= 1.0 - 0.12 * t;
  c.g *= 1.0 + 0.08 * g;
  return clamp(c, 0.0, 1.0);
}

vec3 applyExposure(vec3 c, float ev) {
  return c * pow(2.0, ev);
}

vec3 applyContrast(vec3 c, float amount) {
  float a = amount / 100.0;
  return clamp((c - 0.5) * (1.0 + a) + 0.5, 0.0, 1.0);
}

// Soft pivot highlights/shadows (mobile-friendly approx of ACR pivots)
vec3 applyHiSh(vec3 c, float hi, float sh) {
  float l = lum(c);
  float hiW = smoothstep(0.4, 1.0, l);
  float shW = 1.0 - smoothstep(0.0, 0.45, l);
  c += hiW * (hi / 100.0) * 0.35 * vec3(1.0);
  c += shW * (sh / 100.0) * 0.35 * vec3(1.0);
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
  float s = sat / 100.0;
  c = mix(gray, c, 1.0 + s);
  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float chroma = mx - mn;
  float v = vib / 100.0;
  float vibMask = 1.0 - smoothstep(0.0, 0.5, chroma);
  c = mix(gray, c, 1.0 + v * vibMask);
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
  // Soft membership of 8 hue buckets
  float hubs[8];
  hubs[0] = 0.0; hubs[1] = 0.08; hubs[2] = 0.16; hubs[3] = 0.33;
  hubs[4] = 0.50; hubs[5] = 0.66; hubs[6] = 0.75; hubs[7] = 0.91;
  vec3 adj[8];
  adj[0] = uHslRed; adj[1] = uHslOrange; adj[2] = uHslYellow; adj[3] = uHslGreen;
  adj[4] = uHslAqua; adj[5] = uHslBlue; adj[6] = uHslPurple; adj[7] = uHslMagenta;

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
  // Domain scale for texel centers
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
  // Stronger in shadows (Tri-X / ACROS style option baked lightly)
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
}