# Crash reporting

Debug builds may persist structured crash traces locally **and** optionally forward to Firebase Crashlytics when keys are present.

## Local files (debug)
1. Install a clean debug build, finish onboarding to reach Home.
2. Trigger **Report Caught Exception** / **Throw Uncaught Exception** from the debug menu (when compiled in).
3. Inspect app-specific external storage under  
   `/sdcard/Android/data/<applicationId>/files/`  
   (exact subdirectory names depend on `crash-android-lib` configuration—confirm with current sources rather than legacy paths).

## Crashlytics (optional)
1. Provide `google-services.json` for your Firebase project or install a CI-produced APK with keys baked in.
2. Repeat the synthetic crash steps and verify events appear in the Firebase console.

## Support email attachment
1. Follow **Contact Support.md** to ensure crash excerpts propagate into outgoing mail when that integration is enabled.
