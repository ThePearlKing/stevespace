#version 150

// A heaped-up cloud seen side on: a flat base, a cauliflower top of lumps on lumps, ragged
// billowing edges, dark underneath and bright where the Sun catches the tops. Thunderheads
// spread out flat at the top into an anvil.

uniform vec4 ColorModulator;
uniform vec3 SunView;
uniform vec3 UpView;
uniform vec3 SunLight;
uniform vec3 Ambient;
uniform vec3 HazeColor;
uniform float HazeDistance;
uniform float AnimTime;

in vec2 uv;
in float seed;
in vec4 albedo;
in float aspect;
in float anvil;
in float viewDistance;

out vec4 fragColor;

// A hash without sin (sin of big numbers goes to grain on some cards).
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// How fine a detail can be before it only shows as grain (half-widths per pixel).
float pixel;
// Its size in the units of the noise being drawn.
float grain;

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

// Billows: rounded lumps rather than smooth noise.
float billow(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    float scale = 1.0;
    for (int i = 0; i < 5; i++) {
        // Finer lumps than a couple of pixels fade to their average, rather than to grain.
        float sharp = 1.0 - smoothstep(0.25, 0.6, grain * scale);
        sum += amp * mix(0.6, 1.0 - abs(noise(p) * 2.0 - 1.0), sharp);
        scale *= 2.03;
        p = p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// Round bulges: how far a point is from the nearest of a scatter of points (0 at them).
float bulges(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float nearest = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 cell = vec2(float(x), float(y));
            vec2 at = cell + vec2(hash(i + cell), hash(i + cell + 17.0)) * 0.8 + 0.1;
            nearest = min(nearest, length(at - f));
        }
    }
    return nearest;
}

float rand(float n) {
    return hash(vec2(n * 7.31 + 1.0, seed * 0.913 + 3.0));
}

// How far inside the cloud a point is (x across -1..1, y up in half-widths); below 0 is outside.
float inside(vec2 p) {
    // The top: a heap of round domes, tallest in the middle.
    float top = 0.0;
    // A broad body under them, so there's never a gap in the middle.
    if (abs(p.x) < 0.9) {
        float bx = p.x / 0.9;
        top = aspect * 0.3 * sqrt(1.0 - bx * bx);
    }
    float spread = anvil > 0.5 ? 0.45 : 0.85;
    // A thunderhead's tower goes all the way up into its anvil.
    if (anvil > 0.5 && abs(p.x) < 0.3) {
        float tx = p.x / 0.3;
        top = max(top, aspect * 0.9 - 0.3 * (1.0 - sqrt(1.0 - tx * tx)));
    }
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float c = (rand(fi) * 2.0 - 1.0) * spread;
        float w = (0.25 + 0.3 * rand(fi + 10.0)) * (1.0 - 0.4 * abs(c));
        float h = aspect * (0.45 + 0.55 * rand(fi + 20.0)) * (1.0 - 0.6 * abs(c));
        float dx = (p.x - c) / w;
        if (abs(dx) < 1.0) {
            top = max(top, h - w * (1.0 - sqrt(1.0 - dx * dx)) * 1.2);
        }
    }
    float d = min(top - p.y, p.y);
    // A thunderhead flattens out against the top of the sky and spreads wide.
    if (anvil > 0.5) {
        float low = aspect * 0.74 + 0.08 * abs(p.x);
        float high = aspect * 0.97;
        float shelf = min(min(p.y - low, high - p.y), (0.95 - abs(p.x)) * 0.5) * 0.6;
        d = max(d, shelf);
    }
    // Ragged, lumpy edges (the big lumps on the edge make it read as cloud, not a shape).
    vec2 q = p * 2.5 + fract(seed * 0.0137) * 61.0 + vec2(AnimTime * 0.004, 0.0);
    grain = pixel * 2.5;
    float big = billow(q);
    grain = pixel * 7.75;
    d += (big - 0.55) * 0.16 + (billow(q * 3.1) - 0.5) * 0.06 * (1.0 - smoothstep(0.1, 0.3, pixel * 7.75));
    // Cauliflower: round bulges heaped on the sides and top, bigger higher up, none at the flat base.
    float heap = smoothstep(0.0, 0.25, p.y);
    vec2 b = p * 3.0 + fract(seed * 0.023) * 37.0;
    d += ((0.45 - bulges(b)) * 0.2 + (0.45 - bulges(b * 2.3 + 5.0)) * 0.07 * (1.0 - smoothstep(0.1, 0.3, pixel * 7.0))) * heap;
    // Near the base the edge is flat and only a little torn.
    d = min(d, p.y + (noise(p * 9.0 + fract(seed * 0.071) * 40.0) - 0.5) * 0.03);
    return d;
}

void main() {
    vec2 p = vec2(uv.x, uv.y * aspect);
    pixel = length(fwidth(p));
    float d = inside(p);
    if (d <= 0.0) {
        discard;
    }
    // Facing out of the cloud, from how the inside deepens: the edges turn away, the middle faces us.
    float e = max(0.04, pixel * 4.0);
    vec2 g = vec2(inside(p + vec2(e, 0.0)) - d, inside(p + vec2(0.0, e)) - d) / e;
    vec3 right = normalize(cross(UpView, vec3(0.0, 0.0, 1.0)));
    vec3 up = normalize(cross(vec3(0.0, 0.0, 1.0), right));
    float face = clamp(d * 6.0, 0.0, 1.0);
    vec3 n = normalize(-(right * g.x + up * g.y) * 0.25 * (1.0 - face) + vec3(0.0, 0.0, 0.4 + face));
    float lit = clamp(dot(n, SunView) * 0.5 + 0.5, 0.0, 1.0);
    // Light comes down from the top: the base is dark, the tops bright.
    float height = clamp(p.y / aspect, 0.0, 1.0);
    float depth = mix(0.42, 1.18, smoothstep(0.0, 0.8, height));
    // Shadowed folds between the lumps.
    grain = pixel * 4.0;
    float folds = 0.78 + 0.32 * billow(p * 4.0 + fract(seed * 0.031) * 50.0);
    vec3 colour = albedo.rgb * (Ambient * 0.9 + SunLight * lit * 0.75) * depth * folds;
    // A soft, thin edge: the outside wisps let light through.
    float alpha = albedo.a * smoothstep(0.0, max(0.07, pixel * 3.0), d);
    float haze = 1.0 - exp(-viewDistance / HazeDistance);
    colour = mix(colour, HazeColor, haze * 0.85);
    fragColor = vec4(colour, alpha) * ColorModulator;
}
