#version 300 es
precision highp float;
precision highp sampler3D;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uImage;
uniform sampler3D uLut;
uniform float uLutSize;
uniform float uLutIntensity;
uniform int uLutStage;
uniform int uShowBefore;
uniform float uSplitPos;
uniform int uAutoColor;
uniform vec3 uAutoMin;
uniform vec3 uAutoMax;
uniform vec3 uAutoWb;

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
uniform float uSkinSmooth;
uniform float uSkinRadiance;
uniform float uNaturalSkin;
uniform float uClarity;
uniform float uGlow;
uniform float uSharpness;
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
    vec3 lutColor = texture(uLut, c * scale + offset).rgb;
    return mix(c, lutColor, clamp(uLutIntensity, 0.0, 1.0));
}

vec3 vignette(vec3 c, float amount) {
    vec2 p = vUv * 2.0 - 1.0;
    float r = dot(p, p);
    float v = 1.0 - (amount / 100.0) * smoothstep(0.2, 1.4, r);
    return c * v;
}

float getSkinMask(vec3 rgb) {
    vec3 hsv = rgb2hsv(rgb);
    float h = hsv.x;
    float dh = abs(h - 0.075);
    dh = min(dh, 1.0 - dh);
    float hueMask = smoothstep(0.085, 0.02, dh);
    float satMask = smoothstep(0.08, 0.25, hsv.y) * (1.0 - smoothstep(0.75, 0.95, hsv.y));
    float valMask = smoothstep(0.08, 0.35, hsv.z);
    return clamp(hueMask * satMask * valMask, 0.0, 1.0);
}

vec3 applySkinPolish(vec3 c, vec2 uv, float smoothAmt, float radianceAmt) {
    if (smoothAmt <= 0.0 && radianceAmt <= 0.0) return c;
    float skin = getSkinMask(c);
    if (skin <= 0.01) return c;

    vec3 result = c;
    if (smoothAmt > 0.0) {
        vec2 px = (1.2 + smoothAmt * 0.025) / uResolution;
        vec3 c0 = texture(uImage, uv + vec2(-px.x, 0.0)).rgb;
        vec3 c1 = texture(uImage, uv + vec2( px.x, 0.0)).rgb;
        vec3 c2 = texture(uImage, uv + vec2(0.0, -px.y)).rgb;
        vec3 c3 = texture(uImage, uv + vec2(0.0,  px.y)).rgb;

        float w0 = exp(-dot(c - c0, c - c0) * 30.0);
        float w1 = exp(-dot(c - c1, c - c1) * 30.0);
        float w2 = exp(-dot(c - c2, c - c2) * 30.0);
        float w3 = exp(-dot(c - c3, c - c3) * 30.0);
        float totalW = 1.0 + w0 + w1 + w2 + w3;
        vec3 blurred = (c + c0 * w0 + c1 * w1 + c2 * w2 + c3 * w3) / totalW;

        float blendFactor = (smoothAmt / 100.0) * 0.65 * skin;
        result = mix(result, blurred, blendFactor);
    }

    if (radianceAmt > 0.0) {
        float rad = (radianceAmt / 100.0) * 0.85 * skin;
        vec3 lifted = 1.0 - (1.0 - result) * (1.0 - vec3(0.20, 0.17, 0.15));
        result = mix(result, lifted, rad);
    }

    return clamp(result, 0.0, 1.0);
}

vec3 applyNaturalSkin(vec3 c, vec2 uv, float amount) {
    if (amount <= 0.0) return c;
    float skin = getSkinMask(c);
    if (skin <= 0.01) return c;

    float t = clamp(amount / 100.0, 0.0, 1.0) * skin;
    // 1. Frequency-preserving micro-evenness (evens blotches while keeping pores/detail)
    vec2 px = 1.8 / uResolution;
    vec3 c0 = texture(uImage, uv + vec2(-px.x, 0.0)).rgb;
    vec3 c1 = texture(uImage, uv + vec2( px.x, 0.0)).rgb;
    vec3 c2 = texture(uImage, uv + vec2(0.0, -px.y)).rgb;
    vec3 c3 = texture(uImage, uv + vec2(0.0,  px.y)).rgb;
    vec3 localMean = (c + c0 + c1 + c2 + c3) * 0.2;
    vec3 detail = c - localMean;
    vec3 evened = mix(c, localMean + detail * 0.55, t * 0.65);

    // 2. Neutralize dull yellow-green cast + gentle healthy ivory lift
    evened.b = clamp(evened.b + 0.025 * t, 0.0, 1.0);
    evened.g = clamp(evened.g - 0.008 * t, 0.0, 1.0);
    vec3 healthyLift = 1.0 - (1.0 - evened) * (1.0 - vec3(0.14, 0.12, 0.11));
    return clamp(mix(evened, healthyLift, t * 0.70), 0.0, 1.0);
}

vec3 applyClarityGlowSharp(vec3 c, vec2 uv, float clarity, float glow, float sharpness) {
    if (clarity == 0.0 && glow <= 0.0 && sharpness <= 0.0) return c;

    vec3 centerOrig = texture(uImage, uv).rgb;
    vec2 px = 2.0 / uResolution;
    vec3 c0 = texture(uImage, uv + vec2(-px.x, 0.0)).rgb;
    vec3 c1 = texture(uImage, uv + vec2( px.x, 0.0)).rgb;
    vec3 c2 = texture(uImage, uv + vec2(0.0, -px.y)).rgb;
    vec3 c3 = texture(uImage, uv + vec2(0.0,  px.y)).rgb;
    vec3 localAvg = (c0 + c1 + c2 + c3) * 0.25;

    // Clarity (pure high-pass midtone micro-contrast from uImage)
    if (clarity != 0.0) {
        float l = lum(c);
        float midWeight = 4.0 * l * (1.0 - l);
        vec3 detail = centerOrig - localAvg;
        c += detail * (clarity / 100.0) * 0.65 * midWeight;
    }

    // Soft Glow / Halation on graded highlights
    if (glow > 0.0) {
        float hi = smoothstep(0.55, 1.0, lum(c));
        vec3 glowColor = c * hi * (glow / 100.0) * 0.35;
        c = 1.0 - (1.0 - c) * (1.0 - glowColor);
    }

    // Sharpness (pure Laplacian from uImage)
    if (sharpness > 0.0) {
        vec2 pxS = 1.0 / uResolution;
        vec3 s0 = texture(uImage, uv + vec2(-pxS.x, 0.0)).rgb;
        vec3 s1 = texture(uImage, uv + vec2( pxS.x, 0.0)).rgb;
        vec3 s2 = texture(uImage, uv + vec2(0.0, -pxS.y)).rgb;
        vec3 s3 = texture(uImage, uv + vec2(0.0,  pxS.y)).rgb;
        vec3 lap = centerOrig * 4.0 - (s0 + s1 + s2 + s3);
        c += lap * (sharpness / 100.0) * 0.30;
    }

    return clamp(c, 0.0, 1.0);
}

float filmGrainNoise(vec2 uv) {
    vec2 p = uv * vec2(12.9898, 78.233);
    return fract(sin(dot(p, vec2(1.0, 1.0))) * 43758.5453123) * 2.0 - 1.0;
}

vec3 filmGrain(vec3 c, float intensity, float size) {
    if (intensity <= 0.0) return c;
    float scale = max(1.0, size * 0.08);
    vec2 grainCoord = (vUv * uResolution) / scale;
    float n = filmGrainNoise(grainCoord);
    float l = lum(c);
    float grainWeight = clamp(4.0 * l * (1.0 - l), 0.0, 1.0);
    return clamp(c + n * (intensity / 100.0) * 0.12 * grainWeight, 0.0, 1.0);
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

vec3 applyAutoColor(vec3 c) {
    if (uAutoColor == 0) return c;
    vec3 span = max(uAutoMax - uAutoMin, vec3(0.15));
    vec3 leveled = clamp((c - uAutoMin) / span, 0.0, 1.0);
    vec3 wbCorrected = clamp(leveled * uAutoWb, 0.0, 1.0);
    float mx = max(wbCorrected.r, max(wbCorrected.g, wbCorrected.b));
    float mn = min(wbCorrected.r, min(wbCorrected.g, wbCorrected.b));
    float sat = mx - mn;
    float l = dot(wbCorrected, vec3(0.2126, 0.7152, 0.0722));
    vec3 vibranced = clamp(mix(vec3(l), wbCorrected, 1.0 + 0.18 * (1.0 - sat)), 0.0, 1.0);
    return mix(c, vibranced, 0.65);
}

void main() {
    vec3 orig = texture(uImage, vUv).rgb;
    if (uShowBefore == 1) {
        fragColor = vec4(orig, 1.0);
        return;
    }

    // Interactive Split Screen Comparison (Sebelum vs Sesudah)
    if (uSplitPos >= 0.0) {
        float d = vUv.x - uSplitPos;
        float halfLine = 1.5 / uResolution.x;
        if (abs(d) < halfLine) {
            fragColor = vec4(1.0, 1.0, 1.0, 1.0);
            return;
        }
        if (vUv.x < uSplitPos) {
            fragColor = vec4(orig, 1.0);
            return;
        }
    }

    vec3 c = orig;
    c = applyAutoColor(c);
    c = applySkinPolish(c, vUv, uSkinSmooth, uSkinRadiance);
    c = applyNaturalSkin(c, vUv, uNaturalSkin);
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
    c = applyClarityGlowSharp(c, vUv, uClarity, uGlow, uSharpness);
    c = vignette(c, uVignette);
    c = filmGrain(c, uGrainIntensity, uGrainSize);
    fragColor = vec4(c, 1.0);
}
