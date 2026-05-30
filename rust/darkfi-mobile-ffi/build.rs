fn main() {
    uniffi::generate_scaffolding("src/darkfi_mobile_ffi.udl").unwrap();
    link_android_sqlcipher_if_present();
}

fn link_android_sqlcipher_if_present() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("android") {
        return;
    }
    let abi = match std::env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "arm64-v8a",
        Ok("arm") => "armeabi-v7a",
        Ok("x86") => "x86",
        Ok("x86_64") => "x86_64",
        _ => return,
    };
    let search =
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../artifacts/sqlcipher").join(abi);
    let sqlcipher = search.join("libsqlcipher.a");
    if sqlcipher.exists() {
        println!("cargo:rustc-link-search=native={}", search.display());
        println!("cargo:rustc-link-lib=static=sqlcipher");
        let crypto = search.join("libcrypto.a");
        if crypto.exists() {
            println!("cargo:rustc-link-lib=static=crypto");
        }
    }
}
