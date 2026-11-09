const fs = require("fs");
const path = require("path");
const { PRESETS } = require("../web/presets-data.js");
const { LUT_DATA_BASE64, LUT_SIZE, getLutUint8Array } = require("../web/luts-bundle.js");

console.log(`Verifying ${PRESETS.length} presets against real 3D LUTs...`);

let passed = 0;
let failed = 0;

for (const p of PRESETS) {
  const b64 = LUT_DATA_BASE64[p.preset_id];
  if (!b64) {
    console.error(`[FAIL] Missing 3D LUT binary for ${p.preset_id}`);
    failed++;
    continue;
  }
  const bytes = getLutUint8Array(p.preset_id);
  const expectedBytes = LUT_SIZE * LUT_SIZE * LUT_SIZE * 3;
  if (bytes.length !== expectedBytes) {
    console.error(`[FAIL] Bad byte length for ${p.preset_id}: got ${bytes.length}, expected ${expectedBytes}`);
    failed++;
    continue;
  }

  // Sample center pixel (neutral gray 0.5, 0.5, 0.5 in 17^3 lattice -> index 8, 8, 8)
  const idx = (8 * LUT_SIZE * LUT_SIZE + 8 * LUT_SIZE + 8) * 3;
  const r = bytes[idx];
  const g = bytes[idx + 1];
  const b = bytes[idx + 2];

  // Sample blue sky coordinate (0.3, 0.6, 0.9 -> indices 5, 10, 15)
  const skyIdx = (15 * LUT_SIZE * LUT_SIZE + 10 * LUT_SIZE + 5) * 3;
  const skyR = bytes[skyIdx];
  const skyG = bytes[skyIdx + 1];
  const skyB = bytes[skyIdx + 2];

  console.log(`[PASS] ${p.preset_id.padEnd(28)} | ${p.camera_system.toUpperCase().padEnd(9)} | Mid Gray: (${String(r).padStart(3)}, ${String(g).padStart(3)}, ${String(b).padStart(3)}) | Sky: (${String(skyR).padStart(3)}, ${String(skyG).padStart(3)}, ${String(skyB).padStart(3)})`);
  passed++;
}

console.log(`\nVerification Summary: ${passed} PASSED, ${failed} FAILED.`);
if (failed > 0) process.exit(1);
