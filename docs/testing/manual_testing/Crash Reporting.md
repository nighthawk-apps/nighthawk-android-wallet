# Crash reporting

Nighthawk includes **no Firebase Crashlytics or third-party telemetry SDKs** in any build flavor (Google Play, F-Droid, or debug).

## Local files (debug)
1. Install a clean debug build, finish onboarding to reach Home.
2. Trigger **Report Caught Exception** / **Throw Uncaught Exception** from the debug menu (when compiled in).
3. Inspect app-specific external storage under  
   `/sdcard/Android/data/<applicationId>/files/`  
   (exact subdirectory names depend on `crash-android-lib` configuration—confirm with current sources rather than legacy paths).

## Support email attachment
1. Follow **Contact Support.md** to attach user-initiated local diagnostic excerpts into outgoing mail only when you choose to report an issue.
