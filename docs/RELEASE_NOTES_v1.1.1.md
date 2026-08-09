# Chalna v1.1.1

## Fixed

- Restored Chalna's eligibility in Android's default Assistant picker.
- Added the complete voice-interaction metadata required by Android RoleController, including a declared recognition-service component.
- Restored the dedicated VoiceInteractionSession process recommended by the Android voice-interaction contract.
- Added API 34 emulator gates that install Chalna, assign the Assistant role without bypassing qualification, and verify the platform's interaction and recognition service wiring.

## Behavior and privacy

Chalna remains an invocation-triggered video capture tool, not a speech assistant. Its recognition-service component exists only to satisfy Android's Assistant role contract and rejects speech-recognition requests without activating the microphone. Camera and optional recording audio still start only after an explicit Assistant invocation.

There is no network permission, broad media permission, pre-capture, camera warm-up, or persistent camera binding. Device Gallery recordings remain in `Movies/Chalna`; Chalna Vault recordings remain app-private and can be removed with app data or uninstall.

## Installation

Download `chalna-v1.1.1-release.apk`, `chalna-v1.1.1-SHA256.txt`, and `chalna-v1.1.1-build-info.json` from this private immutable release. The update keeps package `app.chalna.capture` and the existing release signing identity. After installation, open Chalna and choose it in the system default Assistant screen.

Assistant gestures, power-button routing, and locked screen-off delivery remain OEM-dependent and require physical-device validation.
