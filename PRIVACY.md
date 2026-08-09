# Privacy notice

Last updated: 2026-08-09. This notice describes intended Chalna 1.1.0 behavior; v1.1.0 binary verification is **Pending verification**.

## Data used

Chalna uses the rear camera to create video after an explicit supported trigger. If the user enables audio and grants Microphone permission, the recording also contains audio. It uses notification state to show an active recording and may use vibration for user feedback.

The selected destination controls storage. **Device Gallery** writes the recording to Android MediaStore under `Movies/Chalna`, where authorized gallery, backup, or sync software may process it. **Chalna Vault** writes it to app-private local storage. “Vault” describes app-private placement, not cryptographic encryption. Vault media is removed when Chalna's app data is cleared or the app is uninstalled unless the user exports it first.

Settings and an index of Chalna-created capture metadata are stored locally. The index may include a local URI/reference, filename, creation time, duration, quality, audio flag, dimensions, and size. Production Chalna has no Diagnostics or VisualLab collection surface. It does not collect assist structure, screenshots, foreground-app identity, accounts, location, contacts, communications, or advertising identifiers.

## Data not transmitted

The app has no account, cloud service, analytics, advertising, telemetry, or `INTERNET` permission. Chalna does not intentionally transmit recordings or capture-index metadata. Share/export actions are explicit user actions and may grant another selected app access to a chosen recording. Android media providers, backup/sync products, gallery applications, or other apps the user authorizes may independently process Device Gallery items; their policies apply.

## Retention and control

Recordings remain until deleted. Device Gallery items can be managed through Chalna, an authorized gallery/files app, or system storage tools. Vault items are managed through Chalna and disappear with app data/uninstall. Local settings and the capture index remain until changed, cleared, or uninstalled. A stale index entry is removed when Chalna confirms that its referenced media no longer exists.

## Permissions

- Camera: required for video.
- Microphone: optional; required only when audio recording is enabled.
- Notifications: presents ongoing capture status and stop control on Android versions that require permission.
- Foreground service (camera/microphone) and vibration: support visible capture and feedback.

Chalna must not request internet, `READ_MEDIA_VIDEO`, broad storage, location, contacts, SMS, call-log, overlay, accessibility-service, or other unrelated permissions. The library is limited to Chalna-created recordings; it does not scan unrelated device videos. See [Android privacy best practices](https://developer.android.com/privacy-and-security/about), [permissions](https://developer.android.com/training/permissions/requesting), and [shared media storage](https://developer.android.com/training/data-storage/shared/media).

Privacy questions and suspected violations should be reported privately through the repository owner’s GitHub Security Advisory channel. A public contact address is **Pending verification**.
