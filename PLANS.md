# Chalna v1.1.0 second-pass plan

## Goal

Prepare a private, signed, immutable v1.1.0 update that adds local capture management and playback without expanding sensor triggers, network access, or broad media permissions. The package remains `app.chalna.capture`; the update is version name `1.1.0`, version code `2`.

## Baseline

- Immutable v1.0.0 was published from commit `80dc98ccdc2587812e99270928531b6d40972be8` with package `app.chalna.capture`, version code `1`, and signer SHA-256 `E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9`.
- v1.1.0 must remain installable as an update by preserving both application ID and signing identity.
- Android compilation, dependency resolution, tests, emulator work, APK inspection, and release packaging run only on GitHub-hosted CI.

## Second-pass scope

1. Replace the production Diagnostics and VisualLab surfaces with capture-library navigation and tests.
2. Add a local capture index, filtering, selection, deletion, sharing/export actions, and an in-app local player.
3. Offer two explicit storage destinations:
   - **Device Gallery:** writes into Android MediaStore under `Movies/Chalna`; other authorized gallery/backup applications may see it.
   - **Chalna Vault:** writes into Chalna's app-private local storage; it is not a claim of encryption and is removed with app data/uninstall unless exported first.
4. Use stable AndroidX Media3 1.11.0 only through `media3-exoplayer`; keep every player control in the Foundation-only Chalna UI. Do not add Media3 UI, streaming, network, media-session, download, or ads modules.
5. Refine gallery, playback glow, and custom icon states while retaining the Foundation-only design system.
6. Keep capture cold until an Assistant invocation or notification action; browsing and playback of already-saved files must not bind camera or microphone.

## Policy and verification

- Forbid `INTERNET`, `READ_MEDIA_VIDEO`, broad storage, unrelated permissions, Material libraries/icons/ripple, production Diagnostics, and VisualLab.
- Assert non-empty JVM test XML with zero failures/errors and archive it from Android CI.
- Require UI QA to export non-empty gallery, glow, and icon PNGs in addition to instrumentation reports.
- Build a minified signed release and inspect alignment, signature schemes, package, version, SDKs, debuggable state, forbidden permissions, and absence of Diagnostics/VisualLab in the manifest, DEX package listing, and R8 mapping.
- Pin signer continuity to the verified v1.0.0 fingerprint as well as the protected repository secret.
- Publish exactly three versioned assets, then re-download and independently recheck checksum, signature, package, version code, permissions, and forbidden production surfaces.
- Grant the release job only `contents: write` and `attestations: read`, then retry immutable-release verification for at most 12 attempts with 10-second intervals to tolerate bounded GitHub attestation propagation; fail if verification remains unavailable.

## Exit criteria

- Android CI [31322147848](https://github.com/sampple-korea/chalna-android/actions/runs/31322147848) is green: policy, formatting, static analysis, resolved dependency inspection, lint, 32 JVM tests, and debug APK passed.
- UI QA [31322147867](https://github.com/sampple-korea/chalna-android/actions/runs/31322147867) is green: 44 connected tests, an independent 38-frame deterministic screenshot pass, required artifact checks, and install/launch smoke passed.
- All 38 rendered PNGs were downloaded. Home, Gallery, Player, Settings, Glow, icon masks, Night/Mist, and 200% font captures were directly inspected after a deliberate refinement pass; selected exact outputs are committed in `docs/screenshots/`.
- The release workflow must prove package/signing continuity, version code `2`, checksum integrity, published immutable-release verification, and remote APK identity before completion. Its signed `build-info` asset is the durable release ledger.
- Device Gallery and Chalna Vault recording, playback, export/share/delete, OEM Assistant invocation, keyguard delivery, hardware latency, thermal/battery behavior, and accessibility remain explicitly unverified until corresponding CI or physical-device evidence exists.

No signing or publication claim is valid before the release workflow and re-downloaded artifact are inspected.
