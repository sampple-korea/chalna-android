# Chalna v1.2.0 production hardening

## Current audit — 2026-08-10

- Repository: `sampple-korea/chalna-android`; `origin` points to the private GitHub repository and `main` is the default branch.
- Baseline: immutable `v1.1.1`, commit `98557c2a77fb7cfed5709bfa23a43fce57639ec5`, package `app.chalna.capture`, version code `3`.
- Signing continuity anchor: SHA-256 `E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9`; the protected signing secrets are present and the existing release APK is the comparison artifact.
- Next release: version name `1.2.0`, version code `4`, tag `v1.2.0`. Version metadata moves to one checked source.
- Android build, lint, tests, emulator work, benchmark/profile generation, APK/AAB packaging, and dependency resolution remain GitHub Actions-only.

## Confirmed architecture risks

1. Voice interaction, session, and recognition components run in three private processes while reading a default-process `CaptureRuntime`; state is therefore not shared and invocation Glow/action can be wrong.
2. Service intents launch independent coroutines; a mutex serializes only after launch and cannot cancel a start while `CameraXCaptureEngine.start()` is suspended.
3. The current state model uses wall-clock time for recording duration and post-finalize guarding and catches cancellation as capture failure.
4. The active attempt journal lacks an exact MediaStore identity and deletes stale output without first attempting to salvage a playable recording.
5. The AtomicFile capture index rewrites the whole list, discovers files by path/name, and eagerly validates/extracts metadata on Gallery refresh.
6. `ProductionUiDependencies` eagerly creates ExoPlayer, performs app-wide one-second ticks, owns unrelated screens, and makes lifecycle/security behavior difficult to test.
7. The player uses TextureView, incomplete seeking/lifecycle state, and global inset ownership; the Gallery lacks paging, sort, favorites, trash, and durable batch results.
8. The invocation Glow allocates a palette in the draw path, ignores tangent orientation, and reduces per-corner display geometry to one radius.

## Decisions

- Remove the unnecessary component process attributes and keep the default application process. Keep `Application` initialization lightweight and lazy.
- Replace mutable runtime guesses with an authoritative `CaptureStateRepository`, typed `CaptureCommandDispatcher`, and single-consumer actor.
- Use epoch time only for filenames/user dates and monotonic time for duration, ordering, latency, timers, and guards.
- Use Room 2.8.4 with Paging 3.5.0, exported schemas, non-destructive migrations, an idempotent importer for `capture-index-v1`, and exact capture identities only.
- Treat media bytes as the source of truth. Finalize validates output before READY; metadata persistence failure creates a reconciliation operation and never deletes a valid video.
- Lazily construct Gallery reconciliation, thumbnail decoding, and Media3 playback only when their screens need them.
- Add a locked-device-safe Quick Settings Tile that uses the same dispatcher and never bypasses keyguard.
- Keep Material UI/icons, network permission, broad media reads, pre-capture, warm camera, and persistent binding prohibited.
- Publish APK, AAB, checksums, build info, and SBOM only after clean/update/16KB, signer-continuity, and remote-asset verification.

## Implementation and verification stages

1. Process unification, invocation-token registry, typed dispatch results, actor/state machine, cancellation and service lifecycle.
2. CameraX output identity, monotonic stats, storage/thermal stop, finalize validation, attempt salvage and reconciliation.
3. Room database, legacy import, paging/sort/filter/favorites/trash/export relationships and playback-position persistence.
4. Lazy Player controller, SurfaceView, recording conflict, seeking/speed/fullscreen/error state, safe intent routing.
5. Screen-owned state/controllers, no global tick, refined Home/Setup/Settings/Gallery, Quick Tile, edge-to-edge/back/accessibility.
6. Allocation-free invocation Glow hot path, tangent streaks, per-corner geometry and deterministic Visual Lab frames.
7. Unit, migration, concurrency, instrumentation, screenshot, security, benchmark, baseline-profile, upgrade and release verification.
8. GitHub Actions failure loop, image/trace inspection, deliberate refinement, source audit, immutable v1.2.0 publication and remote re-verification.

## Completion evidence ledger

- Implemented: default-process Assistant/capture authority, command actor, monotonic timing, CameraX recovery/finalize validation, durable reconciliation, Room migration, paged Gallery/trash/export, lazy Player, Quick Tile, refined UI/Glow/icons, and release/security automation.
- Preserved rather than recreated the v1.1.1 Assistant-role hotfix: the recognition service, `android.speech.RecognitionService` default category, voice-interaction metadata, package, and signer remain intact. UI QA proves Android RoleController qualification on API 29, 33, 34, 35, and 36.
- Final source audit found that Android 14+ `KEY_SHOW_SESSION_ID` is an integer while the registry read it as a string. The registry now follows the platform type, and API 34 instrumentation asserts the resulting prepare/show identity; final CI must run on this correction.
- Android CI [31364048916](https://github.com/sampple-korea/chalna-android/actions/runs/31364048916) passed policy, formatting, Detekt, lint, JVM tests, dependency inspection, and debug APK for commit `09ab820da03d5bcd8a26c2702f1e987db84013df`.
- UI QA [31364048942](https://github.com/sampple-korea/chalna-android/actions/runs/31364048942) passed API 34 instrumentation, 39 deterministic screenshot goldens, accessibility smoke, and API 29/33/35/36 Assistant-role qualification for the same commit.
- Benchmark [31364048906](https://github.com/sampple-korea/chalna-android/actions/runs/31364048906) generated 464 app-only startup/baseline rules with SHA-256 `3F2B2E2AEC3EA1986DA9F01DEE6DDB832B15E254503490C43F77F0F71E91668A`; the package-filtered sources are committed. Cold initial-display median was 334.505 ms and warm median was 55.482 ms, both within their accepted regression thresholds, and ten Perfetto traces were archived.
- Inspected: 39 API 34 Korean screenshots across Home, Setup, Gallery, Player, Settings, Glow keyframes, large font, Night/Mist, tall/cutout simulation, and adaptive icon masks. The artifact from run 31364048942 compares all 39 goldens with zero pixel/perceptual difference and was opened for visual review.
- Repository visibility was independently re-read through the GitHub API and corrected to `PRIVATE`; default branch is `main`, force-push and deletion are disabled, and immutable releases are enabled.
- Pending: v1.2.0 APK/AAB metadata, checksums, signer match, immutable release and re-downloaded asset verification.

---

# Archived v1.1.1 Assistant eligibility hotfix

## Goal

Publish a private, signed, immutable v1.1.1 hotfix that restores Android Assistant-role eligibility without regressing the v1.1 capture, storage, gallery, player, design, privacy, or signing contract. The package remains `app.chalna.capture`; the update is version name `1.1.1`, version code `3`.

## Baseline

- Immutable v1.0.0 was published from commit `80dc98ccdc2587812e99270928531b6d40972be8` with package `app.chalna.capture`, version code `1`, and signer SHA-256 `E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9`.
- Immutable v1.1.0 exposed a role-qualification regression: its voice-interaction metadata omitted `recognitionService`, while Android RoleController requires non-null session service, recognition service, and `supportsAssist=true` metadata before listing a VoiceInteractionService as an Assistant candidate.
- v1.1.1 must remain installable as an update by preserving both application ID and signing identity.
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

- The APK declares a valid, system-bound recognition-service component. It rejects speech requests without recording audio because Chalna is not a speech assistant.
- API 34 UI QA and signed-release smoke tests assign `android.app.role.ASSISTANT` to the installed package without bypassing role qualification, then verify both secure service mappings.
- The immutable v1.1.0 tag and assets remain untouched; v1.1.1 uses a new tag and versioned assets.
- Android CI [31324093962](https://github.com/sampple-korea/chalna-android/actions/runs/31324093962) passed policy, formatting, static analysis, dependency inspection, lint, JVM tests, and debug assembly for the fix commit.
- UI QA [31324097583](https://github.com/sampple-korea/chalna-android/actions/runs/31324097583) passed connected tests, 38 deterministic screenshot tests, artifact checks, install/launch, Assistant role assignment, and secure service-wiring verification on API 34.

- The unchanged v1.1 UI retains the 38 rendered PNGs inspected across Home, Gallery, Player, Settings, Glow, icon masks, Night/Mist, and 200% font states; selected exact outputs remain committed in `docs/screenshots/`.
- The release workflow must prove package/signing continuity, version code `3`, Assistant role qualification, checksum integrity, published immutable-release verification, and remote APK identity before completion. Its signed `build-info` asset is the durable release ledger.
- Device Gallery and Chalna Vault recording, playback, export/share/delete, OEM Assistant invocation, keyguard delivery, hardware latency, thermal/battery behavior, and accessibility remain explicitly unverified until corresponding CI or physical-device evidence exists.

No signing or publication claim is valid before the release workflow and re-downloaded artifact are inspected.
