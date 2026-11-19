# Continuous Integration
Continuous integration is set up with GitHub Actions.  The workflows are defined in this repo under [/.github/workflows](../.github/workflows).

Workflows exist for:
 * Pull request - On pull request, static analysis and testing is performed.
 * Deploy - On merge to the main branch, a release build is automatically deployed.  Concurrency limits are in place, to ensure that only one release deployment can happen at a time.

## Native / UniFFI libraries

GitHub Actions workflows run **Gradle only**; they do **not** invoke **`cargo-ndk`** or regenerate **`libdarkfi_mobile_ffi.so`**. CI APKs therefore ship UniFFI native code only if **`darkfi-android-sdk/src/main/jniLibs/<abi>/libdarkfi_mobile_ffi.so`** is added by a workflow step or cache restore (none today). For local parity with production native behavior, build the Rust library as described in the repository **`README`** section **Build the project**.

## Setup
When forking this repository, some variables/secrets need to be defined to set up new continuous integration builds.

The variables/secrets passed to GitHub Actions then map to Gradle properties set up within our build scripts.  Necessary secrets are documented at the top of each GitHub workflow yml file, as well as reiterated here.

To enhance security, [OpenID Connect](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-google-cloud-platform) is used to generate temporary access tokens for each build.

### Pull request
* Variables
    * GitHub Actions variable `SUPPORT_EMAIL_ADDRESS` — passed through as Gradle property `WALLET_SUPPORT_EMAIL_ADDRESS` (environment `ORG_GRADLE_PROJECT_WALLET_SUPPORT_EMAIL_ADDRESS`).
* Secrets
    * `EMULATOR_WTF_API_KEY` - API key for [Emulator.wtf](https://emulator.wtf)

The Pull Request workflow runs `test_kotlin_modules`, `test_android_unit_tests` (`:darkfi-android-sdk:testDebugUnitTest :ui-lib:testDebugUnitTest`), static analysis (`ktlint`, `detekt`, `androidLint`), and Emulator.wtf instrumented tests when `EMULATOR_WTF_API_KEY` is configured. Nighthawk includes no Firebase Test Lab, Crashlytics, or Google Services SDKs in any build or workflow.

Note that pull requests will create a "release" build with a temporary fake signing key so forks can verify release packaging cleanly.

### Release deployment
* Variables
    * GitHub Actions variable `SUPPORT_EMAIL_ADDRESS` — passed through as Gradle property `WALLET_SUPPORT_EMAIL_ADDRESS` (environment `ORG_GRADLE_PROJECT_WALLET_SUPPORT_EMAIL_ADDRESS`).
* Secrets
    * `GOOGLE_PLAY_CLOUD_PROJECT` - Google Cloud project associated with Google Play.
    * `GOOGLE_PLAY_SERVICE_ACCOUNT` - Email address of service account.
    * `GOOGLE_PLAY_WORKLOAD_IDENTITY_PROVIDER` - Workload identity provider to generate temporary service account key
    * `UPLOAD_KEYSTORE_BASE_64` — Base64 encoded upload keystore.
    * `UPLOAD_KEYSTORE_PASSWORD` — Password for upload keystore.
    * `UPLOAD_KEY_ALIAS` — Name of key inside upload keystore.
    * `UPLOAD_KEY_ALIAS_PASSWORD` — Password for key alias.

To obtain the values for the Google Play deployment, you'll need to:

* Create a service account with access to your Google Play account.  Recommended permissions are to "edit and delete draft apps" and "release apps to testing tracks".
* Configure [workload identity federation](https://github.com/google-github-actions/auth#setting-up-workload-identity-federation)

Note that security of release deployments is enhanced via two mechanisms:
 - CI signs the app with the upload keystore and not the final release keystore.  If the upload keystore is ever leaked, it can be rotated without impacting end user security.
 - Deployment to Google Play can only be made to testing tracks.  Release to production requires manual human login under a different account with greater permissions.