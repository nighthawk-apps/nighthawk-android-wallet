fn main() {
    uniffi::generate_scaffolding("src/darkfi_mobile_ffi.udl").unwrap();

    // Generate Rust types from lightwallet.proto for the gRPC client
    tonic_build::configure()
        .build_server(false) // Client-only — no server code needed
        .build_client(true)
        .out_dir("src/proto_gen")
        .compile_protos(&["proto/lightwallet.proto"], &["proto/"])
        .expect("Failed to compile lightwallet.proto");
}
