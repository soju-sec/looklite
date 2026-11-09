import { composeSliders, zeroSlidersLike } from "../core/compose.js";
import { parseCube, cubeToRgb8, identityCube } from "../core/cube.js";
import { VERT, FRAG } from "./shaders.js";

const BRANDS = ["fujifilm", "kodak", "leica", "hasselblad", "sony"];
const LIGHT_KEYS = [
  ["exposure", "Exposure", -2, 2, 0.01],
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
  ["vignette", "Vignette", -100, 100, 1],
  ["grain_intensity", "Grain", 0, 100, 1],
  ["grain_size", "Grain size", 0, 100, 1],
];

const state = {
  presets: [],
  brand: "fujifilm",
  active: null,
  base: null,
  deltas: null,
  composed: null,
  tab: "light",
  showBefore: false,
};

const el = {
  canvas: document.getElementById("gl"),
  status: document.getElementById("status"),
  brands: document.getElementById("brands"),
  presets: document.getElementById("presets"),
  tabs: document.getElementById("tabs"),
  sliders: document.getElementById("sliders"),
  file: document.getElementById("file"),
  reset: document.getElementById("reset"),
  before: document.getElementById("before"),
};

const gl = el.canvas.getContext("webgl2", { antialias: false, alpha: false });
if (!gl) throw new Error("WebGL2 required");

const prog = createProgram(gl, VERT, FRAG);
const locs = cacheUniforms(gl, prog);
const quad = createQuad(gl, prog);

let imageTex = null;
let lutTex = null;
let lutSize = 17;
let imageSize = { w: 1, h: 1 };

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
    "uBalHighlights",
  ];
  const out = {};
  for (const n of names) out[n] = glCtx.getUniformLocation(program, n);
  return out;
}

function uploadImageFromCanvas(sourceCanvas) {
  if (!imageTex) imageTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, imageTex);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 1);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, sourceCanvas);
  imageSize = { w: sourceCanvas.width, h: sourceCanvas.height };
  fitCanvas();
}

function uploadLut(cube) {
  const packed = cubeToRgb8(cube);
  lutSize = packed.size;
  if (!lutTex) lutTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_3D, lutTex);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE);
  gl.texImage3D(
    gl.TEXTURE_3D,
    0,
    gl.RGB8,
    lutSize,
    lutSize,
    lutSize,
    0,
    gl.RGB,
    gl.UNSIGNED_BYTE,
    packed.rgb8,
  );
}

function fitCanvas() {
  const maxW = el.canvas.parentElement.clientWidth;
  const maxH = el.canvas.parentElement.clientHeight;
  const scale = Math.min(maxW / imageSize.w, maxH / imageSize.h, 1);
  el.canvas.width = Math.max(1, Math.floor(imageSize.w * scale));
  el.canvas.height = Math.max(1, Math.floor(imageSize.h * scale));
  gl.viewport(0, 0, el.canvas.width, el.canvas.height);
  draw();
}

function hslVec(triplet) {
  return [triplet?.h ?? 0, triplet?.s ?? 0, triplet?.l ?? 0];
}

function balVec(b) {
  return [b?.r ?? 0, b?.g ?? 0, b?.b ?? 0];
}

function draw() {
  if (!imageTex || !lutTex || !state.composed) return;
  const s = state.composed;
  gl.useProgram(prog);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, imageTex);
  gl.activeTexture(gl.TEXTURE1);
  gl.bindTexture(gl.TEXTURE_3D, lutTex);
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
  el.status.textContent = `${state.active?.display_label || state.active?.look_name} · ${state.active?.lut_stage} LUT`;
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
    btn.className = b === state.brand ? "active" : "";
    btn.onclick = () => {
      state.brand = b;
      renderBrands();
      renderPresetChips();
    };
    el.brands.appendChild(btn);
  }
}

function renderPresetChips() {
  el.presets.innerHTML = "";
  const list = state.presets.filter((p) => p.camera_system === state.brand);
  for (const p of list) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = p.display_label || p.look_name;
    btn.className = state.active?.preset_id === p.preset_id ? "active" : "";
    btn.onclick = () => applyPreset(p);
    el.presets.appendChild(btn);
  }
}

function renderSliders() {
  const keys =
    state.tab === "light" ? LIGHT_KEYS : state.tab === "color" ? COLOR_KEYS : FX_KEYS;
  el.sliders.innerHTML = "";
  for (const [key, label, min, max, step] of keys) {
    const row = document.createElement("div");
    row.className = "row";
    const abs = state.composed[key];
    const delta = state.deltas[key] ?? 0;
    row.innerHTML = `<label>${label}</label>`;
    const input = document.createElement("input");
    input.type = "range";
    input.min = String(min - (state.base[key] ?? 0));
    input.max = String(max - (state.base[key] ?? 0));
    // Simpler: edit absolute value via delta from base
    input.min = String(min);
    input.max = String(max);
    input.step = String(step);
    input.value = String(abs);
    const val = document.createElement("span");
    val.textContent = Number(abs).toFixed(step < 1 ? 2 : 0);
    input.oninput = () => {
      const absolute = Number(input.value);
      state.deltas[key] = absolute - Number(state.base[key] ?? 0);
      recompose();
      val.textContent = Number(state.composed[key]).toFixed(step < 1 ? 2 : 0);
    };
    row.appendChild(input);
    row.appendChild(val);
    el.sliders.appendChild(row);
  }
}

function makeDemoImage() {
  const c = document.createElement("canvas");
  c.width = 1280;
  c.height = 800;
  const ctx = c.getContext("2d");
  const g = ctx.createLinearGradient(0, 0, c.width, c.height);
  g.addColorStop(0, "#1a3a5c");
  g.addColorStop(0.35, "#c9a27a");
  g.addColorStop(0.55, "#6f8f4e");
  g.addColorStop(0.75, "#d8d2c4");
  g.addColorStop(1, "#2b211c");
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, c.width, c.height);
  // skin-ish patch + sky + foliage for preset evaluation
  ctx.fillStyle = "#e0b69a";
  ctx.beginPath();
  ctx.ellipse(420, 380, 140, 180, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#3d6ea8";
  ctx.fillRect(0, 0, c.width, 220);
  ctx.fillStyle = "#4a6b34";
  ctx.fillRect(700, 300, 420, 320);
  ctx.fillStyle = "#f2f0ea";
  ctx.fillRect(980, 120, 160, 100);
  return c;
}

async function loadPresets() {
  const index = await fetch("../presets/index.json").then((r) => r.json());
  const presets = await Promise.all(
    index.map((id) => fetch(`../presets/${id}.json`).then((r) => r.json())),
  );
  state.presets = presets;
}

async function loadDefaultLut() {
  try {
    const text = await fetch("../luts/identity_17.cube").then((r) => r.text());
    uploadLut(parseCube(text));
  } catch {
    uploadLut(identityCube(17));
  }
}

el.tabs.addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-tab]");
  if (!btn) return;
  state.tab = btn.dataset.tab;
  for (const b of el.tabs.querySelectorAll("button")) {
    b.classList.toggle("active", b === btn);
  }
  renderSliders();
});

el.reset.onclick = () => {
  if (!state.base) return;
  state.deltas = zeroSlidersLike(state.base);
  recompose();
  renderSliders();
};

el.before.addEventListener("pointerdown", () => {
  state.showBefore = true;
  draw();
});
el.before.addEventListener("pointerup", () => {
  state.showBefore = false;
  draw();
});
el.before.addEventListener("pointerleave", () => {
  state.showBefore = false;
  draw();
});

el.file.onchange = async () => {
  const file = el.file.files?.[0];
  if (!file) return;
  const bmp = await createImageBitmap(file);
  const c = document.createElement("canvas");
  c.width = bmp.width;
  c.height = bmp.height;
  c.getContext("2d").drawImage(bmp, 0, 0);
  uploadImageFromCanvas(c);
};

window.addEventListener("resize", fitCanvas);

async function main() {
  await loadDefaultLut();
  uploadImageFromCanvas(makeDemoImage());
  await loadPresets();
  renderBrands();
  const first = state.presets.find((p) => p.camera_system === state.brand) || state.presets[0];
  applyPreset(first);
  el.status.textContent = "Ready · open an image or pick a preset";
}

main().catch((err) => {
  console.error(err);
  el.status.textContent = String(err.message || err);
});