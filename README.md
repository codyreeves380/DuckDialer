# DuckDialer Android

Independent Kotlin Android dialer project.

- Application ID: `com.alwaysasuspect.duckdialer`
- Theme: black / orange
- Compile SDK / target SDK: 37
- Minimum SDK: 29

## Open and build

1. Install Android Studio with Android SDK Platform 37 and JDK 17.
2. Open this folder in Android Studio.
3. Allow Gradle to sync and download the Android Gradle Plugin/Kotlin plugin.
4. Build with **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

This workspace did not contain Android SDK, Gradle, or an emulator, so the project has not been compiled or device-tested here.

## Current starter milestone

- `ACTION_DIAL` entry point.
- `RoleManager.ROLE_DIALER` request.
- `InCallService` declaration and basic incoming/ongoing call controls.
- Dial pad and `TelecomManager.placeCall`.
- Persistent `*67` preference and outgoing-call prefixing for ordinary non-emergency numbers.
- Calls-made-today count from the call log, subject to call-log permission.

## Important next engineering work

This is a starter scaffold, not a finished production dialer. Before daily use, implement and test:
- lifecycle-safe observation for call state and audio state;
- robust incoming-call UI presentation and process recreation;
- hold, swap, conference, DTMF keypad, Bluetooth and route controls;
- proper Contacts / Recents screens and permission rationale;
- carrier/device validation for `*67` (availability varies by carrier);
- emergency-call handoff and all Telecom edge cases;
- accessibility, localization, automated tests, and signing/release pipeline.

Android always routes emergency calls through the preloaded emergency-capable dialer. DuckDialer does not override that behavior.
