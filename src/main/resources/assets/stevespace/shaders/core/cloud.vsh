#version 150

// A storm cloud: a quad standing upright, turned to face the camera. U runs -1..1 across it
// (the seed packed into whole tens), V 0..1 from its flat base to its top (how many quarters
// further it really is than drawn packed into tens); Normal.x is its height over half-width / 4,
// Normal.y whether it spreads into an anvil.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out float seed;
out vec4 albedo;
out float aspect;
out float anvil;
out float viewDistance;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    seed = floor(UV0.x / 10.0 + 0.5);
    float further = floor(UV0.y / 10.0 + 0.5);
    uv = vec2(UV0.x - seed * 10.0, UV0.y - further * 10.0);
    albedo = Color;
    aspect = max(0.05, Normal.x * 4.0);
    anvil = step(0.5, Normal.y);
    viewDistance = length(view.xyz) * max(1.0, further / 4.0);
}
