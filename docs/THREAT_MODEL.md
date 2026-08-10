# Chalna threat model

## Scope and assumptions

This model covers the Android application, its local media/database, system Assistant and Quick Settings entry points, notification intents, FileProvider shares, CI signing, and immutable Release distribution. Android system services, CameraX/Media3/Room libraries, and GitHub-hosted runners are trusted dependencies within their documented boundaries. The attacker may install another app, send exported intents, present malformed IDs/URIs, race user actions, or obtain a released APK; the attacker does not initially control Android system UID or the protected signing secret.

## Assets

- captured video/audio bytes and metadata;
- explicit user-trigger semantics and camera/microphone privacy boundary;
- CaptureState/command ordering;
- Vault paths and temporary URI grants;
- Room index, attempt journal, playback position, and settings;
- package/signing identity and immutable Release assets.

## Trust boundaries

1. Android role controller/SystemUI → BIND-protected Voice Interaction/recognition/tile services.
2. External app → exported `MainActivity` launcher intent.
3. Notification/tile/user UI → private command dispatcher and non-exported `CaptureService`.
4. Application → MediaStore, app-specific storage, and FileProvider.
5. UI/player → Room capture ID lookup → local content URI.
6. GitHub Actions secrets → temporary keystore → signed artifacts → immutable private Release.

## Threats and controls

| Threat | Asset/impact | Mitigation | Verification |
|---|---|---|---|
| Malicious explicit Activity intent | arbitrary local file playback or crash | action allowlist; opaque ID format; Room existence lookup; no URI/path extras | intent/security instrumentation |
| Forged service/tile call | hidden or unordered recording | CaptureService non-exported; system-bound services require platform permissions; actor dedupe/typed receipts | merged-manifest policy; concurrency tests |
| Stale PendingIntent | wrong capture/stop action | immutable PendingIntent; unique request identity; stale stop is no-op and clears notification | notification/service tests |
| External URI injection | read another app's media | Player/share accepts only a Room item owned by Chalna; content authority validation | malformed URI tests |
| FileProvider traversal | expose Vault root/trash/other files | narrow `external-files-path`; canonical Vault containment; no raw path input | `StorageSecurityTest`; APK XML audit |
| Excess URI grant | receiver keeps unrelated access | exact selected URI in ClipData; temporary read flag; chooser; no root provider path | share instrumentation/static audit |
| Locked Quick Tile misuse | capture without authentication | official TileService/keyguard flow; unlock-and-run where required; no keyguard dismissal | locked-device plan |
| Assist context exposure | foreground screen/query disclosure | disabled Assist/screenshot context; callbacks ignore data; no persistence/logging | source policy and session tests |
| Debug surface exported | state manipulation in release | Visual Lab exists only in `src/debug`; release manifest/DEX/R8 inspection rejects it | release workflow |
| Database corruption/migration crash | missing Gallery rows | Room transactions, schema export, idempotent legacy importer, media-as-source reconciliation | migration fixtures and recovery tests |
| Accidental video deletion | irreversible user data loss | Recently Deleted, canonical path checks, partial-failure reporting, valid-orphan salvage | repository/media tests |
| Process death during finalize | valid orphan deleted or hidden recording | `START_NOT_STICKY`; exact attempt journal; playable salvage; no auto-record | recovery instrumentation |
| Signing key leak/substitution | malicious update | keystore outside repo; step-scoped secrets; pinned prior fingerprint; candidate/remote comparison | secret scan and release validation |
| Release asset tampering | altered installer | APK/AAB hashes, APK/JAR signature, immutable release attestation, remote re-download | `gh release verify[-asset]` and package checks |
| Dependency/network exfiltration | local media leaves device | no INTERNET permission; no analytics/ads/network stack; resolved dependency policy/SBOM | merged manifest and dependency graph |

## Privacy invariants

Before an explicit Assistant, Quick Tile, notification, or in-app test trigger, Chalna does not acquire CameraX provider, bind camera, prepare Recorder, open microphone, or buffer frames. It does not persist AssistStructure, screenshots, voice queries, foreground package, locations, accounts, contacts, or media content. Telemetry is bounded local outcome/timing metadata keyed by opaque invocation ID.

## Residual risk

OEM Assistant delivery, lock-screen behavior, Side-button routing, privacy indicators, and camera/thermal failure modes require physical Pixel/Samsung tests. A compromised Android system process or rooted device is outside the app's enforcement boundary. Vault is app-private storage, not encryption against a compromised OS.
