# Privacy notice

Last updated: 2026-08-09. This notice describes the intended Chalna 1.0.0 behavior; binary verification is **Pending verification**.

## Data used

Chalna uses the rear camera to create video after an explicit supported trigger. If the user enables audio and grants Microphone permission, the recording also contains audio. It uses notification state to show an active recording and may use vibration for user feedback. Videos are written to Android MediaStore on the device.

Settings are stored locally with Android DataStore. Diagnostics are intended to contain only a bounded list of event names, timestamps, and elapsed timings. They must not contain image/audio content, assist structure, screenshots, foreground-app identity, accounts, location, contacts, communications, or stable advertising identifiers.

## Data not transmitted

The app has no account, cloud service, analytics, advertising, telemetry, or `INTERNET` permission. Chalna does not intentionally transmit recordings or diagnostics. The user, Android media providers, backup/sync products, gallery applications, or other apps the user authorizes may independently process MediaStore items; their policies apply.

## Retention and control

Recordings remain until the user deletes them through Chalna-supported UI, a gallery/files application, or system storage tools. Local settings remain until changed, app data is cleared, or the app is uninstalled. In-memory diagnostics are bounded and disappear when the relevant process ends unless a future feature explicitly documents different behavior.

## Permissions

- Camera: required for video.
- Microphone: optional; required only when audio recording is enabled.
- Notifications: presents ongoing capture status and stop control on Android versions that require permission.
- Foreground service (camera/microphone) and vibration: support visible capture and feedback.

Chalna must not request internet, broad storage, location, contacts, SMS, call-log, overlay, accessibility-service, or other unrelated permissions. See [Android privacy best practices](https://developer.android.com/privacy-and-security/about), [permissions](https://developer.android.com/training/permissions/requesting), and [shared media storage](https://developer.android.com/training/data-storage/shared/media).

Privacy questions and suspected violations should be reported privately through the repository owner’s GitHub Security Advisory channel. A public contact address is **Pending verification**.
