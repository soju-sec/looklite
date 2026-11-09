/**
 * Parse Adobe .cube 3D LUT (TITLE / LUT_3D_SIZE / DOMAIN / RGB lines).
 * Returns { size, data: Float32Array length size^3*3 } in R-fastest order.
 */
export function parseCube(text) {
  const lines = text.split(/\r?\n/);
  let size = 0;
  const values = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    if (line.startsWith("TITLE") || line.startsWith("DOMAIN_")) continue;
    if (line.startsWith("LUT_3D_SIZE")) {
      size = parseInt(line.split(/\s+/)[1], 10);
      continue;
    }
    if (line.startsWith("LUT_1D_SIZE")) {
      throw new Error("1D LUT not supported");
    }
    const parts = line.split(/\s+/).map(Number);
    if (parts.length >= 3 && parts.every((n) => Number.isFinite(n))) {
      values.push(parts[0], parts[1], parts[2]);
    }
  }
  if (!size) throw new Error("Missing LUT_3D_SIZE");
  const expected = size * size * size * 3;
  if (values.length !== expected) {
    throw new Error(`Expected ${expected} floats, got ${values.length}`);
  }
  return { size, data: new Float32Array(values) };
}

/** Identity cube as Float32Array RGB */
export function identityCube(size) {
  const data = new Float32Array(size * size * size * 3);
  let i = 0;
  for (let b = 0; b < size; b++) {
    for (let g = 0; g < size; g++) {
      for (let r = 0; r < size; r++) {
        data[i++] = r / (size - 1);
        data[i++] = g / (size - 1);
        data[i++] = b / (size - 1);
      }
    }
  }
  return { size, data };
}

/**
 * Pack RGB float cube into Uint8 RGB for WebGL2 TEXTURE_3D (lossy preview OK).
 */
export function cubeToRgb8(cube) {
  const { size, data } = cube;
  const out = new Uint8Array(size * size * size * 3);
  for (let i = 0; i < data.length; i++) {
    out[i] = Math.min(255, Math.max(0, Math.round(data[i] * 255)));
  }
  return { size, rgb8: out };
}