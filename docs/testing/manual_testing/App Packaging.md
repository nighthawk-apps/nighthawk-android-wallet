# App packaging sanity checks

## Debug APK
1. `./gradlew :app:assembleDarkfitestnetDebug` (or your target variant).
2. Android Studio → Build → Analyze APK…
3. Confirm everyday debug artifacts (`META-INF/`, `classes*.dex`, `res/`, etc.) are present. Exact auxiliary files evolve with dependencies—use this pass to spot accidental stripping, not to chase historical protobuf lists from retired stacks.

## Release bundle / universal APK
1. Temporarily set `IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY=true` in `gradle.properties` **only** if you need a locally signed release slice for instrumentation.
2. Produce artifacts with modern tasks, e.g. `./gradlew :app:bundleDarkfimainnetRelease` plus any universal-APK helper tasks your CI exposes (names change—mirror `.github/workflows` when unsure).
3. `adb install -r` the universal APK, launch cold, and confirm logging noise stays minimal compared to debug.

Always revert signing overrides after testing.
