# Chalna v1.1.0

This update keeps the same `app.chalna.capture` package and advances Android's update version code to 2.

## What changed

- Browse recordings created by Chalna, filter and select items, and use explicit share, export, or delete actions.
- Play saved local recordings inside Chalna using stable AndroidX Media3.
- Choose **Device Gallery** to save through Android MediaStore under `Movies/Chalna`, or **Chalna Vault** to save in Chalna's app-private local storage.
- Refined gallery, playback glow, and custom icon states while retaining Chalna's Foundation-only interface.
- Removed the production Diagnostics and VisualLab surfaces.

Chalna Vault does not claim encryption. Vault items are removed with app data or uninstall unless exported first. Device Gallery items may be visible to gallery, backup, or sync software authorized on the device.

Chalna does not request `INTERNET`, `READ_MEDIA_VIDEO`, or broad storage access. Its library indexes only recordings Chalna created; it does not scan unrelated device videos. Playback is local-only, and camera/microphone activation still begins only after an explicit supported capture trigger.

## Install and verify

Download `chalna-v1.1.0-release.apk`, `chalna-v1.1.0-SHA256.txt`, and `chalna-v1.1.0-build-info.json` from this private immutable release. Verify the checksum and confirm the build-info package is `app.chalna.capture`, version name is `1.1.0`, version code is `2`, and signing certificate SHA-256 is `E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9` before installing.

Physical OEM Assistant invocation, locked-screen behavior, real camera/audio encoding, storage-destination behavior, playback compatibility, thermal/battery behavior, and accessibility remain device-specific and must not be inferred from CI alone.
