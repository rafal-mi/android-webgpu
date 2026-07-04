@vertex
fn vs_main(@builtin(vertex_index) vertexIndex : u32) -> @builtin(position) vec4f {
    const pos = array(vec2f(0.0, 0.5), vec2f(-0.5, -0.5), vec2f(0.5, -0.5));
    return vec4f(pos[vertexIndex], 0, 1);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(1, 1, 0, 1);
}
