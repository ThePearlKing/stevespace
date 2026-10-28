#version 150

// Distant ground around the camera, built in the flat block coordinates the world uses.
// Out to FlatRadius it stays flat, like the loaded chunks; beyond that it bends down over a
// sphere of the planet's radius, so the horizon drops and curves as it really does.

in vec3 Position;   // x, z: blocks from the mesh centre; y: height above sea level
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraOffset;  // camera relative to the mesh centre (y: above sea level)
uniform float PlanetRadius;
uniform float FlatRadius;
uniform float DrawScale;    // scale about the eye, to keep everything inside the far plane

out vec4 vertexColor;
out vec3 normal;
out float viewDistance;

void main() {
    vec2 offset = Position.xz - CameraOffset.xz;
    float d = length(offset);
    vec2 dir = d > 1e-3 ? offset / d : vec2(1.0, 0.0);
    float h = Position.y;
    float bend = max(0.0, d - FlatRadius) / PlanetRadius;
    float horizontal = d;
    float vertical = h;
    if (bend > 0.0) {
        horizontal = FlatRadius + (PlanetRadius + h) * sin(bend);
        vertical = (PlanetRadius + h) * cos(bend) - PlanetRadius;
    }
    vec3 rel = vec3(dir.x * horizontal, vertical - CameraOffset.y, dir.y * horizontal);
    gl_Position = ProjMat * ModelViewMat * vec4(rel * DrawScale, 1.0);

    // Turn the normal with the ground: further away, "up" leans away from the camera.
    float nd = dot(Normal.xz, dir);
    vec2 side = Normal.xz - dir * nd;
    float c = cos(bend);
    float s = sin(bend);
    float along = nd * c + Normal.y * s;
    float up = -nd * s + Normal.y * c;
    normal = vec3(side.x + dir.x * along, up, side.y + dir.y * along);
    vertexColor = Color;
    viewDistance = length(rel);
}
