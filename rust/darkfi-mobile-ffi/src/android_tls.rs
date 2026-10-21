//! JNI entry point that lets the Nym client verify TLS on Android.
//!
//! nym's HTTP client (reqwest 0.13 with rustls) checks certificates through
//! `rustls-platform-verifier`, which calls Android's trust store over JNI and
//! panics on first use unless it was given the JVM and an app `Context`. The
//! rest of this crate is called through JNA, so this is the one real JNI export;
//! Kotlin calls it from `PlatformTls.ensureInitialized` before starting Nym.

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JClass, JObject};
use jni::EnvUnowned;

#[no_mangle]
pub extern "system" fn Java_com_nighthawkapps_lib_android_sdk_nym_PlatformTls_nativeInit<'caller>(
    mut env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    context: JObject<'caller>,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        rustls_platform_verifier::android::init_with_env(env, context)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
