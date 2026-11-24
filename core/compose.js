/**
 * Compose base preset sliders with user deltas.
 * Nested hsl / color_balance are composed recursively.
 */

function isPlainObject(v) {
  return v !== null && typeof v === "object" && !Array.isArray(v);
}

export function zeroSlidersLike(base) {
  const out = {};
  for (const key of Object.keys(base)) {
    const b = base[key];
    out[key] = isPlainObject(b) ? zeroSlidersLike(b) : 0;
  }
  return out;
}

export function composeSliders(base, deltas, ranges = null, factor = 1.0) {
  const out = {};
  const f = typeof factor === "number" ? Math.max(0, Math.min(1, factor)) : 1.0;
  const allKeys = new Set([...Object.keys(base || {}), ...Object.keys(deltas || {})]);
  for (const key of allKeys) {
    const b = base?.[key] ?? 0;
    const d = deltas?.[key] ?? 0;
    if (isPlainObject(b)) {
      out[key] = composeSliders(b, d || {}, nestedRanges(ranges, key), f);
    } else {
      const sum = Number(b) * f + Number(d);
      out[key] = clampKey(sum, ranges, key);
    }
  }
  return out;
}

function nestedRanges(ranges, key) {
  if (!ranges || ranges[key] == null) return null;
  if (ranges[key].min !== undefined) return null;
  return ranges[key];
}

function clampKey(value, ranges, key) {
  if (!ranges || ranges[key] == null) return value;
  const { min, max } = ranges[key];
  return Math.min(max, Math.max(min, value));
}

export function toRecipePreset(parent, composedSliders, newId, lookName) {
  return {
    ...parent,
    preset_id: newId,
    look_name: lookName,
    display_label: lookName,
    parent_preset_id: parent.preset_id,
    camera_system: "user",
    default_sliders: composedSliders,
    version: (parent.version ?? 1) + 1,
  };
}