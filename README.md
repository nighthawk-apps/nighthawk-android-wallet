# Nighthawk Wallet
Shielded ZEC wallet using the Zcash Android SDK, maintained by [nighthawk apps](https://nighthawkapps.com)

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.nighthawkapps.wallet.android/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.nighthawkapps.wallet.android)

## Getting Started
If you'd like to compile this application from source, please see our [Setup Documentation](docs/Setup.md) to get started.

## Reporting an issue
If you wish to report a security issue, please follow our [Responsible Disclosure guidelines](https://github.com/zcash/ZcashLightClientKit/blob/master/responsible_disclosure.md).  See the [Wallet App Threat Model](https://zcash.readthedocs.io/en/latest/rtd_pages/wallet_threat_model.html) for more information about the security and privacy limitations of the wallet.

If you'd like to report a technical issue or feature request for the Android Wallet, please file a [GitHub issue](https://github.com/nighthawk-apps/nighthawk-android-wallet/issues/new/choose).

General Zcash questions and/or support requests are best directed to either:
 * z2z memo - zs1nhawkewaslscuey9qhnv9e4wpx77sp73kfu0l8wh9vhna7puazvfnutyq5ymg830hn5u2dmr0sf
 * Nighthawk Wallet Support - nighthawkwallet@protonmail.com
 * [DM @NighthawkWallet on X](https://x.com/nighthawkwallet)
 * [Zcash Forum](https://forum.zcashcommunity.com/)
 * [Discord Community](https://discord.io/zcash-community)

## Contributing
Contributions are very much welcomed!  Please read our [Contributing Guidelines](docs/CONTRIBUTING.md) to learn about our process.

## Known Issues
1. Intel-based machines may have trouble building in Android Studio.  The workaround is to add the following line to `~/.gradle/gradle.properties` `ZCASH_IS_DEPENDENCY_LOCKING_ENABLED=false`.  See [#420](https://github.com/zcash/secant-android-wallet/issues/420) for more information.
2. During builds, a warning will be printed that says "Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version."  This can be safely ignored.  The version under build-conventions is the same as the version used elsewhere in the application.
3. When the code coverage Gradle property `IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED` is enabled, the debug app APK cannot be run.  The coverage flag should therefore only be set when running automated tests.
4. Test coverage for Compose code will be low, due to [known limitations](https://github.com/jacoco/jacoco/issues/1208) in the interaction between Compose and Jacoco.
5. Adding the `espresso-contrib` dependency will cause builds to fail, due to conflicting classes.  This is a [known issue](https://github.com/zcash/zcash-android-wallet-sdk/issues/306) with the Zcash Android SDK.
6. Android Studio will warn about the Gradle checksum.  This is a [known issue](https://github.com/gradle/gradle/issues/9361) and can be safely ignored.
7. [#96](https://github.com/zcash/secant-android-wallet/issues/96) - Release builds print some R8 warnings which can be safely ignored.
8. During app's first launch, the following exception starting with `AndroidKeysetManager: keyset not found, will generate a new one` is printed twice.  This exception is not an error, and the code is not being invoked twice.
 
## Disclosure Policy
Do not disclose any bug or vulnerability on public forums, message boards, mailing lists, etc. prior to responsibly disclosing to Nighthawk Wallet and giving sufficient time for the issue to be fixed and deployed. Do not execute on or exploit any vulnerability.

### Reporting a Bug or Vulnerability
When reporting a bug or vulnerability, please provide the following to nighthawkwallet@protonmail.com

A short summary of the potential impact of the issue (if known).
Details explaining how to reproduce the issue or how an exploit may be formed.
Your name (optional). If provided, we will provide credit for disclosure. Otherwise, you will be treated anonymously and your privacy will be respected.
Your email or other means of contacting you.
A PGP key/fingerprint for us to provide encrypted responses to your disclosure. If this is not provided, we cannot guarantee that you will receive a response prior to a fix being made and deployed.

## Encrypting the Disclosure
We highly encourage all disclosures to be encrypted to prevent interception and exploitation by third parties prior to a fix being developed and deployed.  Please encrypt using the PGP public key with fingerprint: `8c07e1261c5d9330287f4ec35aff0fd018b01972`

## Disclaimers
There are some known areas for improvement:

- This app depends upon related libraries that it uses. There may be bugs.
- This wallet currently only supports sapling shielded pool, which makes it incompatible with wallets that support custom note management and orchard pool. 
- Network traffic analysis, like in other cryptocurrency wallets, can leak some privacy of the user.
- The wallet requires trust in the lightwalletd server to display accurate transaction information. 

See the [Wallet App Threat Model](https://zcash.readthedocs.io/en/latest/rtd_pages/wallet_threat_model.html)
for more information about the security and privacy limitations of the wallet.

## Contact Nighthawk Apps
zs1nhawkewaslscuey9qhnv9e4wpx77sp73kfu0l8wh9vhna7puazvfnutyq5ymg830hn5u2dmr0sf
