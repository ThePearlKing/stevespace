#version 150

// A puff of cloud, smoke or dust, drawn as a lit ball with a billowing edge: lit from the Sun's
// side, dark underneath where the cloud above shades it, glowing from inside if it's hot, and
// fading into the haze with distance (the far side of a big cloud is paler, as in real air).

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
in vec3 glow;
in float viewDistance;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

float billow(vec2 p) {
    return noise(p) * 0.55 + noise(p * 2.3 + 7.1) * 0.3 + noise(p * 5.1 + 3.3) * 0.15;
}

void main() {
    float r = length(uv);
    // Turned a different way for each puff, and slowly churning.
    float a = seed * 2.399 + AnimTime * 0.02 * (fract(seed * 0.618) - 0.5);
    vec2 q = mat2(cos(a), -sin(a), sin(a), cos(a)) * uv;
    float edge = billow(q * 2.2 + seed * 3.7);
    float reach = 0.72 + 0.38 * edge;
    if (r > reach) {
        discard;
    }
    // The surface of the ball at this pixel, facing the camera.
    float z = sqrt(max(0.0, 1.0 - min(1.0, r / reach) * min(1.0, r / reach)));
    vec3 n = normalize(vec3(q / reach * vec2(1.0, 1.0), z * 0.8 + 0.2));
    // Wrapped sunlight (cloud scatters light round into its shadowed side), the sky's fill,
    // and only a gentle darkening underneath; the big shading comes from the cloud's colours.
    float lit = clamp(dot(n, SunView) * 0.45 + 0.55, 0.0, 1.0);
    float under = mix(0.72, 1.0, clamp(dot(n, UpView) * 0.5 + 0.5, 0.0, 1.0));
    float lumps = 0.86 + 0.24 * billow(q * 4.0 + seed);
    vec3 colour = albedo.rgb * (Ambient + SunLight * lit) * under * lumps;
    // Hot inside: glow shows most through the thin middle, less at the limb.
    // Hot inside: the glow outshines the lit surface, strongest through the thick middle.
    float hot = clamp(max(glow.r, max(glow.g, glow.b)) * 1.4, 0.0, 1.0);
    colour = mix(colour, vec3(0.0), hot * 0.6) + glow * glow * (0.7 + 0.8 * z) * 3.0;
    float alpha = albedo.a * smoothstep(reach, reach * 0.8, r);
    // Distance haze.
    float haze = 1.0 - exp(-viewDistance / HazeDistance);
    colour = mix(colour, HazeColor, haze * 0.85);
    fragColor = vec4(colour, alpha) * ColorModulator;
}
