#version 150

// The thin glowing shell of air around a planet seen from high above or from space.
// Drawn additively on a sphere slightly larger than the planet.

uniform vec3 SunDir;
uniform vec3 CameraPos;
uniform vec3 AtmosphereColor;
uniform float Strength;
uniform float ShellRadius;
// 1 for a world that shines by its own light: the whole rim glows its colour, day side or night.
uniform float Glow;

in vec3 bodyPos;

out vec4 fragColor;

void main() {
    if (Glow > 0.5) {
        // Light the world gives off itself: the closer the line of sight passes to the world's
        // edge, the brighter, fading out to nothing at the shell. Works from inside the shell too.
        vec3 p = bodyPos * ShellRadius;
        bool inside = length(CameraPos) < ShellRadius;
        if (!inside && dot(normalize(bodyPos), CameraPos - p) < 0.0) {
            discard; // seen from outside, only the near side of the shell
        }
        vec3 d = normalize(p - CameraPos);
        float t = max(0.0, -dot(CameraPos, d));
        float b = length(CameraPos + d * t);
        float fade = 1.0 - smoothstep(1.0, ShellRadius, b);
        float g = b < 1.0 ? 0.0 : fade * fade * 0.8;
        fragColor = vec4(AtmosphereColor * g * Strength, 1.0);
        return;
    }
    vec3 n = normalize(bodyPos);
    vec3 v = normalize(CameraPos - bodyPos * ShellRadius);
    float facing = dot(n, v);
    if (facing < 0.0) {
        discard;
    }
    // Brightest where we look edge-on through the most air.
    float rim = pow(1.0 - facing, 4.0);
    float lit = smoothstep(-0.3, 0.35, dot(n, SunDir));
    // Sunset side of the rim is warmer.
    vec3 color = mix(vec3(1.0, 0.55, 0.3), AtmosphereColor, smoothstep(-0.15, 0.25, dot(n, SunDir)));
    fragColor = vec4(color * rim * lit * Strength, 1.0);
}
