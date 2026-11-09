import { composeSliders, zeroSlidersLike } from "../core/compose.js";
import { parseCube, cubeToRgb8, identityCube } from "../core/cube.js";
import { VERT, FRAG } from "./shaders.js";
import { PRESETS } from "./presets-data.js";

const BRANDS = ["fujifilm", "kodak", "leica", "hasselblad", "sony"];

const LIGHT_KEYS = [
  ["exposure", "Exposure", -3, 3, 0.05],
  ["contrast", "Contrast", -100, 100, 1],
  ["highlights", "Highlights", -100, 100, 1],
  ["shadows", "Shadows", -100, 100, 1],
  ["whites", "Whites", -100, 100, 1],
  ["blacks", "Blacks", -100, 100, 1],
];

const COLOR_KEYS = [
  ["temp", "Temp", -100, 100, 1],
  ["tint", "Tint", -100, 100, 1],
  ["vibrance", "Vibrance", -100, 100, 1],
  ["saturation", "Saturation", -100, 100, 1],
];

const FX_KEYS = [
  ["vignette", "Vignette", 0, 100, 1],
  ["grain_intensity", "Grain", 0, 100, 1],
  ["grain_size", "Grain Size", 0, 100, 1],
];

// Aesthetic swatch mappings for realistic camera look cards
const PRESET_SWATCHES = {
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
  "sony.scinetone_mobile_approx": "linear-gradient(135deg, #263238, #ff8a65)",
};

const state = {
  presets: PRESETS,
  brand: "fujifilm",
  active: null,
  base: null,
  deltas: null,
  composed: null,
  tab: "light",
  mode: "presets", // 'presets' | 'adjust'
  showBefore: false,
};

const el = {
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
  panelAdjust: document.getElementById("panel-adjust"),
};

const gl = el.canvas.getContext("webgl2", { antialias: false, alpha: false, preserveDrawingBuffer: true });
if (!gl) throw new Error("WebGL2 required");

const prog = createProgram(gl, VERT, FRAG);
const locs = cacheUniforms(gl, prog);
const quad = createQuad(gl, prog);

let imageTex = null;
let lutTex = null;
let lutSize = 17;
let originalSourceCanvas = null;

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
  glCtx.bufferData(
    glCtx.ARRAY_BUFFER,
    new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]),
    glCtx.STATIC_DRAW,
  );
  const loc = glCtx.getAttribLocation(program, "aPos");
  glCtx.enableVertexAttribArray(loc);
  glCtx.vertexAttribPointer(loc, 2, glCtx.FLOAT, false, 0, 0);
  return buf;
}

function cacheUniforms(glCtx, program) {
  const names = [
    "uImage", "uLut", "uLutSize", "uLutStage", "uShowBefore",
    "uExposure", "uContrast", "uHighlights", "uShadows", "uWhites", "uBlacks",
    "uTemp", "uTint", "uVibrance", "uSaturation", "uVignette",
    "uGrainIntensity", "uGrainSize", "uResolution",
    "uHslRed", "uHslOrange", "uHslYellow", "uHslGreen",
    "uHslAqua", "uHslBlue", "uHslPurple", "uHslMagenta",
    "uBalShadows", "uBalMids", "uBalHighlights",
  ];
  const out = {};
  for (const n of names) out[n] = glCtx.getUniformLocation(program, n);
  return out;
}

function uploadImageFromCanvas(srcCanvas) {
  originalSourceCanvas = srcCanvas;
  if (!imageTex) imageTex = gl.createTexture();
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
  if (!lutTex) lutTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_3D, lutTex);
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE);
  gl.texImage3D(
    gl.TEXTURE_3D, 0, gl.RGB8, size, size, size, 0,
    gl.RGB, gl.UNSIGNED_BYTE, rgb8,
  );
}

function fitCanvas() {
  if (!originalSourceCanvas) return;
  const vpRect = el.viewport.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2.0);
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
  if (!state.composed) return;
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
      if (firstInBrand) applyPreset(firstInBrand);
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

    // Double tap to reset slider to default preset value
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

// Mode switching
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
  if (!btn) return;
  state.tab = btn.dataset.tab;
  for (const b of el.tabs.querySelectorAll("button")) {
    b.classList.toggle("active", b === btn);
  }
  renderSliders();
});

// Hold to toggle before / after view
function setBeforeActive(active) {
  state.showBefore = active;
  el.beforeBadge.classList.toggle("visible", active);
  el.before.classList.toggle("holding", active);
  draw();
}

el.before.addEventListener("pointerdown", () => setBeforeActive(true));
el.before.addEventListener("pointerup", () => setBeforeActive(false));
el.before.addEventListener("pointerleave", () => setBeforeActive(false));

// Touch & Hold directly on canvas viewport for before/after comparison
el.viewport.addEventListener("pointerdown", (e) => {
  if (e.target === el.canvas) setBeforeActive(true);
});
el.viewport.addEventListener("pointerup", () => setBeforeActive(false));
el.viewport.addEventListener("pointercancel", () => setBeforeActive(false));

// Reset all deltas
el.reset.onclick = () => {
  if (!state.base) return;
  state.deltas = zeroSlidersLike(state.base);
  recompose();
  renderSliders();
};

// File open / camera picker
el.file.onchange = async () => {
  const file = el.file.files?.[0];
  if (!file) return;
  const img = new Image();
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

// Export / Save full resolution image
el.export.onclick = () => {
  if (!originalSourceCanvas) return;
  el.status.textContent = "Rendering full-resolution export…";
  
  // Set canvas to full source image resolution for 1:1 render
  const origW = el.canvas.width;
  const origH = el.canvas.height;
  el.canvas.width = originalSourceCanvas.width;
  el.canvas.height = originalSourceCanvas.height;
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
  draw();

  const dataUrl = el.canvas.toDataURL("image/jpeg", 0.95);

  // Restore preview resolution
  el.canvas.width = origW;
  el.canvas.height = origH;
  gl.viewport(0, 0, origW, origH);
  draw();

  const filename = `looklite_${state.active?.look_name.toLowerCase().replace(/[^a-z0-9]/g, "_")}_${Date.now()}.jpg`;

  // Bridge to Android native host if present
  if (window.LookLiteNative && typeof window.LookLiteNative.saveImage === "function") {
    window.LookLiteNative.saveImage(dataUrl, filename);
    el.status.textContent = "Saved to Gallery!";
    return;
  }

  // Web fallback: download anchor
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

// Demo initial photographic image: realistic golden hour landscape
function makeDemoImage() {
  const c = document.createElement("canvas");
  c.width = 1200;
  c.height = 900;
  const ctx = c.getContext("2d");
  
  // Sky golden hour gradient
  const sky = ctx.createLinearGradient(0, 0, 0, 500);
  sky.addColorStop(0, "#2b4c6f");
  sky.addColorStop(0.4, "#857265");
  sky.addColorStop(0.8, "#d9823b");
  sky.addColorStop(1, "#f4b251");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, 1200, 500);

  // Distant mountain
  ctx.fillStyle = "#3a3845";
  ctx.beginPath();
  ctx.moveTo(0, 500);
  ctx.lineTo(350, 320);
  ctx.lineTo(650, 420);
  ctx.lineTo(950, 290);
  ctx.lineTo(1200, 500);
  ctx.closePath();
  ctx.fill();

  // Lake reflection
  const lake = ctx.createLinearGradient(0, 500, 0, 750);
  lake.addColorStop(0, "#4a4237");
  lake.addColorStop(0.5, "#30383b");
  lake.addColorStop(1, "#1f2a30");
  ctx.fillStyle = lake;
  ctx.fillRect(0, 500, 1200, 250);

  // Foreground foliage
  ctx.fillStyle = "#2d3827";
  ctx.fillRect(0, 750, 1200, 150);
  
  return c;
}

async function main() {
  // Always initialize with 17^3 identity cube
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
