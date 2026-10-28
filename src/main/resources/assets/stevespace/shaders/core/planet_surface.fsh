#version 150

// Planet surface seen from altitude or orbit, in body-frame units of one planet radius.

uniform sampler2D Sampler0;   // equirectangular surface map
uniform vec3 SunDir;          // direction to the sun
uniform vec3 CameraPos;       // camera position (planet radius = 1)
uniform vec3 HazeColor;       // colour of the air near the horizon
uniform float HazeDensity;    // haze per planet radius of view distance (0 = airless)
uniform float Brightness;
uniform float SelfGlow;       // light a world gives off itself (Acheron): its night side never quite goes dark

in vec3 bodyPos;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec3 n = normalize(bodyPos);
    vec3 toCamera = CameraPos - bodyPos;
    float dist = length(toCamera);
    vec3 v = toCamera / dist;
    float facing = dot(n, v);
    if (facing < 0.0) {
        discard; // far side of the planet
    }

    vec3 albedo = texture(Sampler0, texCoord0).rgb;
    float ndl = dot(n, SunDir);
    float day = smoothstep(-0.1, 0.12, ndl);
    float light = 0.02 + 0.98 * day * (0.45 + 0.55 * max(ndl, 0.0));
    vec3 color = albedo * max(light, SelfGlow);

    // Sun glint on open water.
    float water = smoothstep(0.05, 0.18, albedo.b - albedo.r);
    vec3 h = normalize(SunDir + v);
    color += vec3(1.0, 0.95, 0.85) * pow(max(dot(n, h), 0.0), 80.0) * water * day * 0.45;

    // Aerial perspective: distant ground fades into the colour of the air, more at grazing angles.
    float haze = 1.0 - exp(-dist * HazeDensity);
    haze = clamp(haze + pow(1.0 - facing, 6.0) * 0.6 * step(0.001, HazeDensity), 0.0, 1.0);
    vec3 air = HazeColor * (0.08 + 0.92 * smoothstep(-0.2, 0.25, ndl));
    color = mix(color, air, haze);

    fragColor = vec4(color * Brightness, 1.0);
}
