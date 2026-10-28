#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1;

uniform float PlanetCurvature;  // 1 / the world's radius (blocks): on a small world the view bends over it; 0 = flat
uniform float PlanetAltitude;   // camera height above the ground's datum

// Bend a camera-relative position over the world's sphere (as the distant ground is drawn): flat
// distance along the ground becomes arc round the sphere, so far ground drops below the horizon.
// Only in the 3D view (a perspective projection), never for things drawn flat on the screen.
vec3 planetCurve(vec3 pos) {
    if (PlanetCurvature <= 0.0 || ProjMat[3][3] != 0.0) {
        return pos;
    }
    float d = length(pos.xz);
    if (d < 1e-3) {
        return pos;
    }
    float R = 1.0 / PlanetCurvature;
    // Each thing bends by its own distance from the world's middle: the ground (at R) curves away,
    // while the ship and you up above keep your shape (bending by R there stretched them sideways).
    float r = max(1.0, R + PlanetAltitude + pos.y);
    float a = d / r;
    vec2 dir = pos.xz / d;
    return vec3(dir.x * r * sin(a), r * cos(a) - R - PlanetAltitude, dir.y * r * sin(a));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(planetCurve(Position), 1.0);

    vertexDistance = fog_distance(planetCurve(Position), FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    texCoord1 = UV1;
}
