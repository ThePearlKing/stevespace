#version 150

// Like vanilla position_tex_color, but without the "discard below 10% alpha" cutoff,
// so soft glows (sun glare, stars, planets) fade out smoothly instead of ending in a hard edge.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
