#version 150

// Earth's cloud layer seen from above: the vanilla cloud pattern on a shell round the planet
// close up, so it lines up with the clouds overhead; from further out, real-looking cloud: the
// small clouds gathered into fields and streets, and the storms as billowing decks with ragged,
// soft edges instead of the weather map's blocky cells.

uniform sampler2D Sampler0;   // cloud map (equirectangular, one texel per cloud cell)
uniform sampler2D Sampler1;   // where the storms are (red: cover), equirectangular, not drifting
uniform vec3 SunDir;
uniform vec3 CameraPos;       // camera position (shell radius = 1)
uniform float Drift;          // how far the clouds have drifted east, in texture widths
uniform float Opacity;

in vec3 bodyPos;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p, int octaves) {
    float total = 0.0;
    float amp = 0.5;
    for (int i = 0; i < octaves; i++) {
        total += amp * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return total;
}

// The storm map, smoothly between its texels (whatever filtering the texture was given).
float smoothMap(vec2 uv, vec2 size) {
    vec2 p = uv * size - 0.5;
    vec2 i = floor(p);
    vec2 f = fract(p);
    ivec2 s = ivec2(size);
    int x0 = int(mod(i.x, size.x));
    int x1 = int(mod(i.x + 1.0, size.x));
    int y0 = clamp(int(i.y), 0, s.y - 1);
    int y1 = clamp(int(i.y) + 1, 0, s.y - 1);
    float a = mix(texelFetch(Sampler1, ivec2(x0, y0), 0).r, texelFetch(Sampler1, ivec2(x1, y0), 0).r, f.x);
    float b = mix(texelFetch(Sampler1, ivec2(x0, y1), 0).r, texelFetch(Sampler1, ivec2(x1, y1), 0).r, f.x);
    return mix(a, b, f.y);
}

void main() {
    vec3 n = normalize(bodyPos);
    vec3 v = normalize(CameraPos - bodyPos);
    if (dot(n, v) < 0.0) {
        discard; // the far side
    }
    // How far out we are: close in, the blocky vanilla pattern; far out, natural cloud.
    float far = smoothstep(0.004, 0.04, length(CameraPos) - 1.0);
    // Lined up with the clouds overhead: texel = world x / 12 (+ drift), world z / 12.
    vec4 cloud = texture(Sampler0, texCoord0 + vec2(Drift - 0.5, -0.5));
    // The cloud's own shapes drift with it.
    float c = cos(Drift * 6.2831853);
    float s = sin(Drift * 6.2831853);
    vec3 q = vec3(c * n.x - s * n.y, s * n.x + c * n.y, n.z);
    float billow = fbm(q * 45.0, 5);
    float puff = fbm(q * 160.0, 3);

    // Fair weather: close in, the vanilla cells; from far off, soft clouds gathered into fields.
    float field = smoothstep(0.45, 0.7, fbm(q * 22.0, 4) * 0.65 + billow * 0.35);
    float fairNear = cloud.a * 0.45;
    float fairFar = field * 0.5 * smoothstep(0.4, 0.62, puff * 0.6 + billow * 0.4);
    float fair = mix(fairNear, fairFar, far);

    // Storms: the weather map says where. Read smoothly (the map is coarse), and a little off the
    // true spot by swirling amounts, so the systems have ragged, curling edges instead of discs.
    vec2 size = vec2(textureSize(Sampler1, 0));
    vec2 warp = (vec2(fbm(q * 11.0, 4), fbm(q * 11.0 + vec3(5.2, 1.3, 7.7), 4)) - 0.5) * 9.0 / size
            + (vec2(fbm(q * 40.0, 3), fbm(q * 40.0 + vec3(2.9, 8.1, 4.4), 3)) - 0.5) * 3.0 / size;
    float storm = smoothMap(texCoord0 + warp * far, size);
    // Heaped up in clumps with gaps between (towers and the clear air round them), not one even sheet.
    float clumps = fbm(q * 90.0, 3);
    float deckNear = step(0.5, storm + 0.3 * (texture(Sampler0, texCoord0 * 3.0 + vec2(Drift * 3.0 + 0.37, 0.11)).a - 0.5)
            + 0.15 * (texture(Sampler0, texCoord0 * 7.0 + vec2(Drift * 7.0 + 0.71, 0.53)).a - 0.5) + 0.2 * cloud.a);
    float deckFar = smoothstep(0.42, 0.58, storm + 0.45 * (billow - 0.5) + 0.2 * (puff - 0.5) + 0.25 * (clumps - 0.5) * (1.0 - storm));
    float deck = mix(deckNear, deckFar, far);

    float a = max(fair, deck);
    if (a < 0.02) {
        discard;
    }
    float ndl = dot(n, SunDir);
    float day = smoothstep(-0.12, 0.15, ndl);
    float light = 0.04 + 0.96 * day * (0.6 + 0.4 * max(ndl, 0.0));
    // Towering tops catch more light, the gaps between them less.
    light *= mix(1.0, 0.72 + 0.4 * (0.5 * billow + 0.25 * puff + 0.25 * clumps), max(deck, far * 0.5));
    fragColor = vec4(vec3(light), a * 0.85 * Opacity);
}
