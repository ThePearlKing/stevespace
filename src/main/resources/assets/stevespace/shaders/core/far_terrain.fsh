#version 150

uniform vec3 SunDir;        // towards the Sun, in the camera's east/up/south axes
uniform vec3 HazeColor;
uniform float HazeDistance; // blocks over which the air hides the ground (0 = airless)
uniform float Ambient;

in vec4 vertexColor;
in vec3 normal;
in float viewDistance;

out vec4 fragColor;

void main() {
    vec3 n = normalize(normal);
    float ndl = dot(n, SunDir);
    // A narrow soft edge to the light, so low sun throws long crater shadows.
    float light = smoothstep(-0.03, 0.12, ndl) * (0.25 + 0.75 * max(ndl, 0.0));
    vec3 color = vertexColor.rgb * (Ambient + (1.0 - Ambient) * light * 1.15);
    if (HazeDistance > 0.0) {
        float haze = 1.0 - exp(-viewDistance / HazeDistance);
        color = mix(color, HazeColor, clamp(haze, 0.0, 1.0));
    }
    fragColor = vec4(color, 1.0);
}
