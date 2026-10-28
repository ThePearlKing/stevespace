#version 150

// An auroral curtain: a thin sheet of light hanging along the oval, sharp along its lower edge
// and fading upward, combed into vertical rays that drift and flicker. Colours are the gases
// glowing at each height (on Earth: nitrogen's magenta fringe low down, oxygen green in the
// body, oxygen red high up). u runs along the oval, v from the bottom (0) to the top (1);
// the vertex alpha carries how bright this stretch is.

uniform vec4 ColorModulator;
uniform float AnimTime;
uniform vec3 LowColor;
uniform vec3 MidColor;
uniform vec3 HighColor;

in vec2 texCoord0;
in vec4 vertexColor;

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

void main() {
    float u = texCoord0.x;
    float v = clamp(texCoord0.y, 0.0, 1.0);
    float t = AnimTime;
    // Rays: fine vertical streaks, slowly sliding along the curtain and flickering.
    float rays = noise(vec2(u * 9.0 + t * 0.6, t * 0.35)) * 0.6 + noise(vec2(u * 31.0 - t * 1.4, t * 1.1)) * 0.4;
    rays = 0.35 + 0.65 * smoothstep(0.2, 0.85, rays);
    // Bigger brightness waves running along it.
    float surge = 0.55 + 0.45 * noise(vec2(u * 1.3 - t * 0.12, 3.7));
    // Height: a sharp lower border, then a long fade (rays reach up higher in places).
    float reach = 0.55 + 0.45 * noise(vec2(u * 6.0, t * 0.2 + 9.0));
    float profile = smoothstep(0.0, 0.05, v) * exp(-v * 3.2 / reach);
    vec3 colour = mix(LowColor, MidColor, smoothstep(0.0, 0.12, v));
    colour = mix(colour, HighColor, smoothstep(0.32, 0.8, v));
    float a = vertexColor.a * profile * rays * surge * 3.0;
    if (texCoord0.y < -0.5) {
        // The glowing band itself, seen from above (v below zero marks it): soft, green-cored.
        a = vertexColor.a * (0.55 + 0.45 * surge) * (0.7 + 0.3 * rays) * 1.6;
        colour = MidColor;
    }
    fragColor = vec4(colour * a, 1.0) * ColorModulator;
}
