import { composeSliders, zeroSlidersLike } from "../core/compose.js";
import { parseCube, cubeToRgb8, identityCube } from "../core/cube.js";
import { VERT, FRAG } from "./shaders.js";
import { PRESETS } from "./presets-data.js";
import { getLutUint8Array, LUT_SIZE } from "./luts-bundle.js";

const BRANDS = ["poles", "fujifilm", "kodak", "leica", "ilford", "vintage"];

const BRAND_LABELS = {
  poles: "POLES",
  fujifilm: "FUJI",
  kodak: "KODAK",
  leica: "LEICA",
  ilford: "ILFORD",
  vintage: "VINTAGE",
};

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

const POLES_KEYS = [
  ["skin_smooth", "Skin Smooth", 0, 100, 1],
  ["skin_radiance", "Skin Radiance", 0, 100, 1],
  ["clarity", "Clarity / Pop", -100, 100, 1],
  ["glow", "Soft Glow", 0, 100, 1],
  ["sharpness", "Sharpening", 0, 100, 1],
];

const FX_KEYS = [
  ["vignette", "Vignette", 0, 100, 1],
  ["grain_intensity", "Grain", 0, 100, 1],
  ["grain_size", "Grain Size", 0, 100, 1],
];

const PRESET_SWATCHES = {
  // Poles & Trending
  "poles.fnatix_flash": "linear-gradient(135deg, #1a2a3a, #e0b48a)",
  "poles.korean_porcelain": "linear-gradient(135deg, #dbe2ef, #f7d6bf)",
  "poles.emerald_moody": "linear-gradient(135deg, #0e2a22, #b8860b)",
  "poles.night_ocean": "linear-gradient(135deg, #0f2027, #203a43)",
  "poles.soft_glow_dream": "linear-gradient(135deg, #fbc2eb, #a6c1ee)",
  "poles.studio_flash": "linear-gradient(135deg, #2c3e50, #bdc3c7)",
  "poles.golden_radiance": "linear-gradient(135deg, #ff8c00, #ffd700)",
  "poles.dark_aesthetic": "linear-gradient(135deg, #141414, #434343)",

  // Fujifilm
  "fuji.classic_chrome": "linear-gradient(135deg, #2c3848, #9b8270)",
  "fuji.classic_neg": "linear-gradient(135deg, #324338, #b86242)",
  "fuji.velvia_50": "linear-gradient(135deg, #1b5e20, #d81b60)",
  "fuji.provia_100f": "linear-gradient(135deg, #37474f, #78909c)",
  "fuji.astia": "linear-gradient(135deg, #d38b72, #dfa996)",
  "fuji.reala_ace": "linear-gradient(135deg, #2e7d32, #fbc02d)",
  "fuji.eterna": "linear-gradient(135deg, #455a64, #a1887f)",
  "fuji.nostalgic_neg": "linear-gradient(135deg, #795548, #d7ccc8)",
  "fuji.acros": "linear-gradient(135deg, #111111, #888888)",
  "fuji.bleach_bypass": "linear-gradient(135deg, #212121, #cfd8dc)",

  // Kodak
  "kodak.portra_400": "linear-gradient(135deg, #dfa97b, #a66a46)",
  "kodak.portra_160": "linear-gradient(135deg, #e4be9e, #c28864)",
  "kodak.portra_800": "linear-gradient(135deg, #e67e22, #995010)",
  "kodak.gold_200": "linear-gradient(135deg, #f39c12, #d35400)",
  "kodak.ektar_100": "linear-gradient(135deg, #0d47a1, #b71c1c)",
  "kodak.kodachrome_64": "linear-gradient(135deg, #e65100, #ffb300)",
  "kodak.cinestill_800t": "linear-gradient(135deg, #00838f, #e91e63)",
  "kodak.vision3_500t": "linear-gradient(135deg, #1a237e, #ff6f00)",
  "kodak.trix_400": "linear-gradient(135deg, #050505, #555555)",

  // Leica
  "leica.classic": "linear-gradient(135deg, #263238, #b0bec5)",
  "leica.natural": "linear-gradient(135deg, #37474f, #eceff1)",
  "leica.vivid": "linear-gradient(135deg, #c2185b, #0097a7)",
  "leica.teal": "linear-gradient(135deg, #004d40, #ff8a65)",
  "leica.monochrom": "linear-gradient(135deg, #141414, #757575)",
  "leica.greg_williams": "linear-gradient(135deg, #0d0d0d, #bdbdbd)",

  // Ilford
  "ilford.hp5_plus": "linear-gradient(135deg, #1c1c1c, #9e9e9e)",

  // Vintage & Specialty
  "vintage.disposable_400": "linear-gradient(135deg, #5d4037, #ffcc80)",
  "vintage.polaroid_color": "linear-gradient(135deg, #455a64, #b0bec5)",
  "vintage.golden_light": "linear-gradient(135deg, #e65100, #ffe082)",
  "vintage.street_car": "linear-gradient(135deg, #006064, #bf360c)",
};

const state = {
  presets: PRESETS,
  brand: "poles",
  active: null,
  base: null,
  deltas: null,
  composed: null,
  intensity: 100,
  tab: "light",
  mode: "presets", // 'presets' | 'adjust'
  showBefore: false,
  splitActive: false,
  splitPos: 0.5,
};

const el = {
  viewport: document.getElementById("viewport"),
  canvas: document.getElementById("gl"),
  status: document.getElementById("status"),
  presetBadge: document.getElementById("preset-badge"),
  beforeBadge: document.getElementById("before-badge"),
  canvasHint: document.getElementById("canvas-hint"),
  splitOverlay: document.getElementById("split-overlay"),
  splitLine: document.getElementById("split-line"),
  splitHandle: document.getElementById("split-handle"),
  sebelumBadge: document.getElementById("sebelum-badge"),
  sesudahBadge: document.getElementById("sesudah-badge"),
  btnSplit: document.getElementById("btn-split"),
  brands: document.getElementById("brands"),
  presets: document.getElementById("presets"),
  presetIntensityBar: document.getElementById("preset-intensity-bar"),
  intensityVal: document.getElementById("intensity-val"),
  filterIntensity: document.getElementById("filter-intensity"),
  btnIntensityMinus: document.getElementById("btn-intensity-minus"),
  btnIntensityPlus: document.getElementById("btn-intensity-plus"),
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
    "uImage", "uLut", "uLutSize", "uLutIntensity", "uLutStage", "uShowBefore", "uSplitPos",
    "uExposure", "uContrast", "uHighlights", "uShadows", "uWhites", "uBlacks",
    "uTemp", "uTint", "uVibrance", "uSaturation", "uVignette",
    "uGrainIntensity", "uGrainSize", "uSkinSmooth", "uSkinRadiance",
    "uClarity", "uGlow", "uSharpness", "uResolution",
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

function uploadLutFromUint8Array(rgb8, size = 17) {
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

function uploadLut(cube) {
  const { size, rgb8 } = cubeToRgb8(cube);
  uploadLutFromUint8Array(rgb8, size);
}

function fitCanvas() {
  if (!originalSourceCanvas) return;
  const vpRect = el.viewport.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2.0);
  const aspect = originalSourceCanvas.width / originalSourceCanvas.height;
  
  // Apple HIG framing: reserve space for floating header and bottom dock
  const dockH = el.panelAdjust?.classList.contains("active") ? 310 : 275;
  const topPad = 72;
  const availH = Math.max(160, vpRect.height - topPad - dockH);
  const availW = Math.max(180, vpRect.width - 24);

  let w = availW;
  let h = w / aspect;
  if (h > availH) {
    h = availH;
    w = h * aspect;
  }
  
  el.canvas.width = Math.round(w * dpr);
  el.canvas.height = Math.round(h * dpr);
  el.canvas.style.width = `${Math.round(w)}px`;
  el.canvas.style.height = `${Math.round(h)}px`;
  el.canvas.style.marginTop = `${Math.round(topPad + (availH - h) / 2)}px`;
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
}

function hslVec(val) {
  if (!val) return new Float32Array([0, 0, 0]);
  if (Array.isArray(val)) return new Float32Array(val);
  return new Float32Array([val.h || 0, val.s || 0, val.l || 0]);
}

function balVec(val) {
  if (!val) return new Float32Array([0, 0, 0]);
  if (Array.isArray(val)) return new Float32Array(val);
  return new Float32Array([val.r || 0, val.g || 0, val.b || 0]);
}

function draw() {
  if (!state.composed) return;
  const s = state.composed;
  gl.useProgram(prog);
  gl.uniform1i(locs.uImage, 0);
  gl.uniform1i(locs.uLut, 1);
  gl.uniform1f(locs.uLutSize, lutSize);
  gl.uniform1f(locs.uLutIntensity, (state.intensity ?? 100) / 100.0);
  gl.uniform1i(locs.uLutStage, state.active?.lut_stage === "early" ? 0 : 1);
  gl.uniform1i(locs.uShowBefore, state.showBefore ? 1 : 0);
  gl.uniform1f(locs.uSplitPos, state.splitActive ? state.splitPos : -1.0);
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
  gl.uniform1f(locs.uSkinSmooth, s.skin_smooth ?? 0);
  gl.uniform1f(locs.uSkinRadiance, s.skin_radiance ?? 0);
  gl.uniform1f(locs.uClarity, s.clarity ?? 0);
  gl.uniform1f(locs.uGlow, s.glow ?? 0);
  gl.uniform1f(locs.uSharpness, s.sharpness ?? 0);
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

function updateIntensityUI() {
  if (el.filterIntensity) {
    el.filterIntensity.value = String(state.intensity);
  }
  if (el.intensityVal) {
    el.intensityVal.textContent = `${state.intensity}%`;
  }
}

function recompose() {
  state.composed = composeSliders(
    state.base,
    state.deltas,
    state.active?.slider_ranges,
    (state.intensity ?? 100) / 100.0
  );
  draw();
  const label = state.active?.display_label || state.active?.look_name;
  el.presetBadge.textContent = label;
  el.status.textContent = `${label} · ${state.intensity}% · ${state.active?.lut_stage.toUpperCase()} LUT`;
}

function applyPreset(preset) {
  if (state.active?.preset_id !== preset.preset_id) {
    state.intensity = 100;
  }
  state.active = preset;
  state.base = structuredClone(preset.default_sliders);
  state.deltas = zeroSlidersLike(state.base);

  // Upload the specific 17^3 Color Cube 3D LUT for this look
  const lutBytes = getLutUint8Array(preset.preset_id);
  if (lutBytes) {
    uploadLutFromUint8Array(lutBytes, LUT_SIZE);
  } else {
    uploadLut(identityCube(17));
  }

  updateIntensityUI();
  recompose();
  renderPresetChips();
  renderSliders();
}

function renderBrands() {
  el.brands.innerHTML = "";
  for (const b of BRANDS) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = BRAND_LABELS[b] || b.toUpperCase();
    btn.className = `brand-chip ${b === state.brand ? "active" : ""}`;
    btn.onclick = () => {
      if (state.brand === b) return;
      state.brand = b;
      renderBrands();
      renderPresetChips();
      if (el.presets) el.presets.scrollLeft = 0;
    };
    el.brands.appendChild(btn);
  }
}

function renderPresetChips() {
  el.presets.innerHTML = "";
  // Strictly bounded to the active brand - prevents leaking into other film stocks
  const list = state.presets.filter((p) => p.camera_system === state.brand);

  for (const p of list) {
    const card = document.createElement("div");
    const isCurrentActive = state.active?.preset_id === p.preset_id;
    card.className = `preset-card ${isCurrentActive ? "active" : ""}`;
    
    const swatch = document.createElement("div");
    swatch.className = "preset-swatch";
    swatch.style.background = PRESET_SWATCHES[p.preset_id] || "linear-gradient(135deg, #37474f, #78909c)";
    
    const name = document.createElement("span");
    name.className = "preset-card-name";
    name.textContent = p.display_label || p.look_name;
    
    const sub = document.createElement("span");
    sub.className = "preset-card-sub";
    sub.textContent = p.tag || p.camera_system.toUpperCase();

    card.appendChild(swatch);
    card.appendChild(name);
    card.appendChild(sub);
    card.onclick = () => applyPreset(p);
    el.presets.appendChild(card);

    if (isCurrentActive) {
      setTimeout(() => {
        card.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
      }, 40);
    }
  }
}

function renderSliders() {
  const keys = state.tab === "light" ? LIGHT_KEYS : state.tab === "color" ? COLOR_KEYS : state.tab === "poles" ? POLES_KEYS : FX_KEYS;
  el.sliders.innerHTML = "";
  for (const [key, label, min, max, step] of keys) {
    const row = document.createElement("div");
    row.className = "slider-row";
    const abs = state.composed[key];
    
    const lbl = document.createElement("span");
    lbl.className = "slider-label";
    lbl.textContent = label;

    const minusBtn = document.createElement("button");
    minusBtn.type = "button";
    minusBtn.className = "slider-step-btn";
    minusBtn.textContent = "−";

    const wrap = document.createElement("div");
    wrap.className = "slider-control-wrap";

    const input = document.createElement("input");
    input.type = "range";
    input.className = "slider-input";
    input.min = String(min);
    input.max = String(max);
    input.step = String(step);
    input.value = String(abs);

    const plusBtn = document.createElement("button");
    plusBtn.type = "button";
    plusBtn.className = "slider-step-btn";
    plusBtn.textContent = "+";

    const val = document.createElement("span");
    val.className = "slider-val";
    val.textContent = Number(abs).toFixed(step < 1 ? 2 : 0);

    function updateValue(newVal) {
      const clamped = Math.min(max, Math.max(min, newVal));
      input.value = String(clamped);
      state.deltas[key] = clamped - (state.base[key] ?? 0);
      recompose();
      val.textContent = clamped.toFixed(step < 1 ? 2 : 0);
    }

    input.oninput = () => updateValue(Number(input.value));
    minusBtn.onclick = () => updateValue(Number(input.value) - step);
    plusBtn.onclick = () => updateValue(Number(input.value) + step);

    // Double tap to reset slider to default preset value
    input.ondblclick = () => {
      state.deltas[key] = 0;
      recompose();
      input.value = String(state.composed[key]);
      val.textContent = Number(state.composed[key]).toFixed(step < 1 ? 2 : 0);
    };

    wrap.appendChild(input);
    row.appendChild(lbl);
    row.appendChild(minusBtn);
    row.appendChild(wrap);
    row.appendChild(plusBtn);
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
  fitCanvas();
  draw();
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
  if (state.splitActive) return;
  state.showBefore = active;
  el.beforeBadge.classList.toggle("visible", active);
  el.before.classList.toggle("holding", active);
  draw();
}

// Split-screen comparison mode (Sebelum vs Sesudah)
function setSplitActive(active) {
  state.splitActive = active;
  el.btnSplit?.classList.toggle("active", active);
  el.splitOverlay?.classList.toggle("active", active);
  updateSplitPositionUI();
  draw();
}

function updateSplitPositionUI() {
  if (!el.splitLine) return;
  const pct = Math.max(0.04, Math.min(0.96, state.splitPos)) * 100;
  el.splitLine.style.left = `${pct}%`;
}

if (el.btnSplit) {
  el.btnSplit.onclick = () => {
    setSplitActive(!state.splitActive);
  };
}

let isDraggingSplit = false;

function handleSplitMove(clientX) {
  if (!state.splitActive) return;
  const rect = el.canvas.getBoundingClientRect();
  if (rect.width <= 0) return;
  const relX = (clientX - rect.left) / rect.width;
  state.splitPos = Math.max(0.02, Math.min(0.98, relX));
  updateSplitPositionUI();
  draw();
}

el.before.addEventListener("pointerdown", () => setBeforeActive(true));
el.before.addEventListener("pointerup", () => setBeforeActive(false));
el.before.addEventListener("pointerleave", () => setBeforeActive(false));

// Touch & Hold directly on canvas viewport or drag split handle
el.viewport.addEventListener("pointerdown", (e) => {
  if (state.splitActive) {
    isDraggingSplit = true;
    handleSplitMove(e.clientX);
    e.preventDefault();
  } else if (e.target === el.canvas) {
    setBeforeActive(true);
  }
});

window.addEventListener("pointermove", (e) => {
  if (isDraggingSplit) {
    handleSplitMove(e.clientX);
  }
});

window.addEventListener("pointerup", () => {
  if (isDraggingSplit) isDraggingSplit = false;
  setBeforeActive(false);
});

window.addEventListener("pointercancel", () => {
  if (isDraggingSplit) isDraggingSplit = false;
  setBeforeActive(false);
});

// Filter Intensity Direct Controls (0-100)
if (el.filterIntensity) {
  el.filterIntensity.addEventListener("input", (e) => {
    state.intensity = Number(e.target.value);
    if (el.intensityVal) el.intensityVal.textContent = `${state.intensity}%`;
    recompose();
  });
}

if (el.btnIntensityMinus) {
  el.btnIntensityMinus.addEventListener("click", () => {
    state.intensity = Math.max(0, state.intensity - 5);
    updateIntensityUI();
    recompose();
  });
}

if (el.btnIntensityPlus) {
  el.btnIntensityPlus.addEventListener("click", () => {
    state.intensity = Math.min(100, state.intensity + 5);
    updateIntensityUI();
    recompose();
  });
}

if (el.intensityVal) {
  el.intensityVal.addEventListener("dblclick", () => {
    state.intensity = 100;
    updateIntensityUI();
    recompose();
  });
}

// Reset all deltas and reset filter intensity to 100
el.reset.onclick = () => {
  if (!state.base) return;
  state.intensity = 100;
  state.deltas = zeroSlidersLike(state.base);
  setSplitActive(false);
  updateIntensityUI();
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
  el.status.textContent = "Rendering export…";
  
  const origW = el.canvas.width;
  const origH = el.canvas.height;
  const wasSplit = state.splitActive;
  state.splitActive = false;
  el.canvas.width = originalSourceCanvas.width;
  el.canvas.height = originalSourceCanvas.height;
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
  draw();

  const dataUrl = el.canvas.toDataURL("image/jpeg", 0.95);

  state.splitActive = wasSplit;
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
  const first = state.presets[0];
  applyPreset(first);
  setMode("presets");
  el.status.textContent = "Ready · tap preset or open photo";
}

main().catch((err) => {
  console.error(err);
  el.status.textContent = String(err.message || err);
});
