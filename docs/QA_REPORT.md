# QA report

Report date: 2026-08-09. Documentation/source inspection only. No local Android build was run, in accordance with repository policy. No CI log, test report, screenshot, device run, APK, signature, checksum, or release asset has been inspected.

## Source-verified facts

| Check | Evidence | Status |
|---|---|---|
| Application/SDK metadata | `app/build.gradle.kts`: `app.chalna.capture`, 1.0.0 (1), min 29, target/compile 36 | Verified by source inspection |
| Toolchain declarations | version catalog/build files: AGP 9.3.1, Gradle 9.5.0, Kotlin 2.3.21, Compose BOM 2026.06.00, CameraX 1.6.1 | Verified by source inspection only |
| Manifest network permission | no `android.permission.INTERNET`; cleartext disabled | Verified by source inspection only |
| Component boundary | capture service non-exported; voice services require `BIND_VOICE_INTERACTION` | Verified by source inspection only |
| Coordinator model | serialized states and bounded invocation-ID dedupe visible in source | Verified by source inspection only |

Source inspection does not prove runtime behavior, resolved dependencies, merged manifest, packaged APK contents, or policy compliance.

## Pending verification ledger

| Area | Required evidence | Result / evidence link |
|---|---|---|
| Policy scan | forbidden permissions/dependencies/imports/pre-capture patterns; secrets/dynamic versions | Pending verification |
| Debug/release compile | full GitHub Actions logs and commit SHA | Pending verification |
| JVM tests | report and test counts/failures | Pending verification |
| Lint/static analysis | complete logs and reports | Pending verification |
| Instrumentation | API/device/locale configuration and report | Pending verification |
| UI screenshots | artifact manifest plus visual review, including `home-ready-night.png` | Pending verification |
| Accessibility | semantics, TalkBack, focus, contrast, 200% font, RTL | Pending verification |
| No-pre-capture invariant | instrumented lifecycle traces and physical privacy indicators | Pending verification |
| Camera/audio/MediaStore | start/stop/finalize/error and playable output | Pending verification |
| Keyguard/OEM | completed device matrix | Pending verification |
| Long-run performance | thermal, battery, frame/finalize reliability | Pending verification |
| Release APK | metadata, merged permissions, exported components, debuggable/minification | Pending verification |
| Signing | `apksigner verify --verbose --print-certs` and expected certificate fingerprint | Pending verification |
| Integrity | CI and downloaded-asset SHA-256 match | Pending verification |
| GitHub release | private immutable release/tag/commit/asset API inspection | Pending verification |

## Decision

Release readiness: **Not established — Pending verification**. Update each row with immutable evidence; do not replace “Pending verification” with Pass based only on source review or workflow configuration.
