struct VertexOut {
    @builtin(position) pos: vec4f,
    @location(0) color: vec4f,
}

@vertex
fn vs_main(
    @location(0) position: vec4f,
    @location(1) color: vec4f
) -> VertexOut {
    var output: VertexOut;
    output.pos = position;
    output.color = color;
    return output;
}

@fragment
fn fs_main(input: VertexOut) -> @location(0) vec4f {
    return input.color;
}
