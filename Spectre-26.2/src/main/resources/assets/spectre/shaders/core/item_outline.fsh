#version 330

uniform sampler2D CaptureSampler;

layout(std140) uniform ItemOutlineConfig {
    vec4 Line;   // rgb outline colour, a outline opacity
    vec4 Fill;   // rgb fill colour, a fill strength
    vec4 Shape;  // x outline width in pixels
};

in vec2 texCoord;

out vec4 fragColor;

const int RAYS = 16;

void main() {
    vec4 item = texture(CaptureSampler, texCoord);
    vec2 px = Shape.x / vec2(textureSize(CaptureSampler, 0));

    // Dilate the silhouette: the outline is wherever a neighbour is covered but this pixel is not.
    float near = 0.0;
    for (int i = 0; i < RAYS; i++) {
        float a = 6.2831853 * float(i) / float(RAYS);
        vec2 d = vec2(cos(a), sin(a)) * px;
        near = max(near, texture(CaptureSampler, texCoord + d).a);
        near = max(near, texture(CaptureSampler, texCoord + d * 0.5).a);
    }

    float edge = clamp(near - item.a, 0.0, 1.0);
    if (item.a <= 0.0 && edge <= 0.0) {
        discard;
    }

    // Gentle tint inside so the item keeps its own texture.
    vec3 body = mix(item.rgb, Fill.rgb * item.a, Fill.a);
    vec4 line = vec4(Line.rgb, 1.0) * Line.a * edge;
    fragColor = vec4(body, item.a) + line * (1.0 - item.a);
}
