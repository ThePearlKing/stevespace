#version 150

// Planet meshes are unit spheres in the planet's own (body-fixed) frame. The model-view matrix
// places them around the camera; the fragment shaders work in the body frame.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 bodyPos;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    bodyPos = Position;
    texCoord0 = UV0;
}
