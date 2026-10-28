#version 150

// A cloud puff: a camera-facing quad. UV runs -1..1 across it (the seed is packed into whole
// tens on U, how many quarters further it really is than drawn on V); Normal carries the puff's own glow colour (0..1), Color its colour and opacity.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out float seed;
out vec4 albedo;
out vec3 glow;
out float viewDistance;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    seed = floor((UV0.x + 1.0) / 10.0 + 0.5);
    float further = floor((UV0.y + 1.0) / 10.0 + 0.5);
    uv = vec2(UV0.x - seed * 10.0, UV0.y - further * 10.0);
    albedo = Color;
    glow = Normal * 0.5 + 0.5;
    viewDistance = length(view.xyz) * max(1.0, further / 4.0);
}
