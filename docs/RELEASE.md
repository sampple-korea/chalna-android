# Release runbook

Android work runs in GitHub Actions, never on the local PC. A configured workflow is not evidence of a successful release; inspect logs and artifacts from the exact commit.

## Preconditions

- Version/changelog/product/privacy/security documents reviewed.
- Protected release environment and least-privilege workflow permissions configured.
- PKCS#12 keystore and passwords stored only as GitHub secrets; expected alias and SHA-256 certificate fingerprint recorded out of repository.
- Actions pinned, dependency versions fixed, private-repository visibility confirmed, and immutable Releases enabled where available.
- Device-test and QA blockers explicitly accepted or closed; no undocumented “pass.”

## CI verification

Trigger the release candidate workflow at an immutable commit. Inspect every job log, not only the green summary. Required jobs: policy scans; clean release build; JVM tests; lint/static analysis; instrumentation; deterministic UI screenshots; packaging/signing. Download reports and artifacts, record run URL/ID, workflow commit SHA, runner image, tool versions, test counts, and artifact retention.

## Artifact verification

On a disposable verification environment, inspect the downloaded candidate with Android SDK tools:

```text
sha256sum chalna-1.0.0.apk
apksigner verify --verbose --print-certs chalna-1.0.0.apk
aapt2 dump badging chalna-1.0.0.apk
apkanalyzer manifest permissions chalna-1.0.0.apk
apkanalyzer manifest print chalna-1.0.0.apk
apkanalyzer files list chalna-1.0.0.apk
```

Confirm package `app.chalna.capture`, version 1.0.0/1, min 29, target 36, expected signer fingerprint, no debug/test flags, no `INTERNET` or unrelated permission, correct exported/protected components, expected FGS types, no signing files/secrets, and reasonable contents. Install that exact hash on test devices; complete smoke, permission, keyguard, start/stop/finalize, notification, and no-pre-trigger sensor checks.

## Publish and independently verify

Create tag `v1.0.0` at the verified commit without rewriting history. Create a private GitHub Release, attach only the verified signed APK/checksum/notices, and publish accurate limitations. Re-download the asset through the GitHub release/API, recompute SHA-256, rerun signature/metadata inspection, and confirm repository visibility, tag target, asset size/name/content type, release state, and immutability. A mismatch blocks release.

## Pending release record

| Evidence | Required value | Recorded value |
|---|---|---|
| Commit SHA | exact 40-character commit | Pending verification |
| CI run URL/ID | successful inspected run | Pending verification |
| Test/lint/instrumentation summaries | zero blocking failures | Pending verification |
| Screenshot artifact/review | approved evidence link | Pending verification |
| Device-test report | supported matrix/accepted limits | Pending verification |
| APK filename/size | exact release asset | Pending verification |
| APK SHA-256 from CI | 64 hex characters | Pending verification |
| Signing certificate SHA-256 | matches approved certificate | Pending verification |
| APK metadata/permissions | expected package/version/SDK/manifest | Pending verification |
| Tag and target | `v1.0.0` → verified commit | Pending verification |
| GitHub release URL/ID | private, immutable, non-draft production release | Pending verification |
| Re-downloaded SHA-256/signature | exact match | Pending verification |

## Rollback

Do not overwrite or replace an immutable asset. If verification fails, mark the release unsuitable, preserve evidence, fix on a new commit, increment version as appropriate, and issue a new signed release. Never force-push the release tag.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [apkanalyzer](https://developer.android.com/tools/apkanalyzer), [GitHub release management](https://docs.github.com/repositories/releasing-projects-on-github/managing-releases-in-a-repository), and [artifact attestations](https://docs.github.com/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds).
