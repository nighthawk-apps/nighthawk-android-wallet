fn main() {
    uniffi::generate_scaffolding("src/darkfi_mobile_ffi.udl").unwrap();

    // Generate Rust types from lightwallet.proto for the gRPC client
    tonic_build::configure()
        .build_server(false) // Client-only — no server code needed
        .build_client(true)
        .out_dir("src/proto_gen")
        .compile_protos(&["proto/lightwallet.proto"], &["proto/"])
        .expect("Failed to compile lightwallet.proto");

    link_android_sqlcipher();
}

/// Android release/debug builds MUST link static SQLCipher.
/// Silent fallback to bundled SQLite would leave `PRAGMA key` ineffective.
fn link_android_sqlcipher() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("android") {
        return;
    }
    let abi = match std::env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "arm64-v8a",
        Ok("arm") => "armeabi-v7a",
        Ok("x86") => "x86",
        Ok("x86_64") => "x86_64",
        other => {
            panic!(
                "Unsupported Android target arch for SQLCipher link: {other:?}"
            );
        }
    };
    let search = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../artifacts/sqlcipher")
        .join(abi);
    let sqlcipher = search.join("libsqlcipher.a");
    if !sqlcipher.exists() {
        panic!(
            "SQLCipher static library missing for Android {abi}: {}\n\
             Build artifacts first (see artifacts/sqlcipher/README.md). \
             Refusing to link plaintext SQLite for wallet storage.",
            sqlcipher.display()
        );
    }
    println!("cargo:rustc-link-search=native={}", search.display());
    println!("cargo:rustc-link-lib=static=sqlcipher");
    let crypto = search.join("libcrypto.a");
    if crypto.exists() {
        println!("cargo:rustc-link-lib=static=crypto");
    }
    println!("cargo:rerun-if-changed={}", sqlcipher.display());
}
