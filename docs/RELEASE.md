# Release record and verification

Android work runs in GitHub Actions, never on the local PC. The v1.1.1 workflow executes from the exact `main` commit. Repository/default workflow permissions are `contents: read`; only the release job receives `contents: write` and `attestations: read`, the minimum additional scopes needed to publish and verify the immutable release. Every reusable GitHub Action is pinned to a full 40-character commit SHA.

## Immutable v1.0.0 baseline

The private v1.0.0 release is published and immutable at commit `80dc98ccdc2587812e99270928531b6d40972be8`. Its downloaded build-info records package `app.chalna.capture`, version code `1`, APK SHA-256 `69ca221786038cabf5df563a296524e52a2cfa851f84a3eab726b5a7ffba8830`, and signing certificate SHA-256 `E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9`.

v1.1.1 keeps the package and signer and advances to version code `3`. The expected certificate digest is pinned directly in the workflow and must also equal the protected repository secret; changing only the secret cannot authorize an unintended signer.

## Signing

The dedicated user-provided PKCS#12 key is stored outside the repository. Prior inspection verified alias `dev-siro`, `PrivateKeyEntry`, RSA-4096, and certificate validity through 2126. The workflow receives five independently stored secrets:

- `CHALNA_RELEASE_KEYSTORE_B64`
- `CHALNA_RELEASE_STORE_PASSWORD`
- `CHALNA_RELEASE_KEY_ALIAS`
- `CHALNA_RELEASE_KEY_PASSWORD`
- `CHALNA_RELEASE_CERT_SHA256`

Passwords are step-scoped, the restored file is mode `0600`, and signing material is removed with `if: always()`.

## Candidate gates

The exact v1.1.1 release commit must pass Android CI and UI QA before dispatch. Android CI runs repository policy, formatting, static checks, captures the resolved dependency graph, rejects resolved Material/Material-icon/Media3-Material artifacts, then runs release lint, JVM tests, and debug assembly. It requires non-empty JUnit XML with zero failures/errors and archives the dependency report, test reports, and raw test results. UI QA runs the full connected test suite, proves that Android accepts the installed debug package as an Assistant role holder without qualification bypass, separately executes screenshot instrumentation, and requires the named non-empty gallery, glow, and icon PNG artifacts.

No prior run proves the v1.1.1 candidate. Run IDs and inspected artifacts must be added to `docs/QA_REPORT.md` only after the new runs finish.

## Release transaction

`.github/workflows/release.yml` accepts only version `1.1.1`, refuses an existing tag/release, restores and validates the signing identity, runs policy/lint/JVM tests, and builds a minified signed APK. It then checks:

- zip alignment and APK signature scheme;
- non-debug certificate and exact v1.0.0 signer continuity;
- package `app.chalna.capture`, version name `1.1.1`, version code `3`, min/target SDK, and non-debuggable manifest;
- successful API 34 Assistant role assignment and secure interaction/recognition service wiring for the exact signed APK;
- absence of `INTERNET` and `READ_MEDIA_VIDEO`;
- absence of Diagnostics/VisualLab in the manifest, DEX package listing, and R8 mapping;
- install and launch of that exact signed APK on an API 34 emulator.

The workflow creates a draft containing exactly these nonempty assets:

- `chalna-v1.1.1-release.apk`
- `chalna-v1.1.1-SHA256.txt`
- `chalna-v1.1.1-build-info.json`

Only after names, count, sizes, and target commit match does it publish. It then downloads all assets again, checks the checksum and local APK hash, and runs `gh release verify-asset` plus `gh release verify` with `attestations: read`. Immutable-release attestations may propagate asynchronously, so verification retries at most 12 times at 10-second intervals and then fails. This is bounded propagation tolerance, not a bypass.

The re-downloaded APK is independently rechecked for signer, package, version name/code, forbidden permissions, and Diagnostics/VisualLab package/component names. A failed mutable draft is cleaned up; a published immutable release is never deleted or modified by the workflow.

## Authoritative final values

The APK hash, byte size, signer fingerprint, commit SHA, tag, SDK values, and release workflow run ID belong in the immutable `chalna-v1.1.1-build-info.json` asset. Keeping self-derived APK hashes out of the source commit avoids a circular build where recording the hash changes the artifact being hashed.

Do not claim v1.1.1 completion, signing success, publication, or integrity until the workflow logs, GitHub API state, downloaded asset metadata, build-info, checksum, and APK are inspected.

## Rollback

Never overwrite an immutable asset or force-push a release tag. A correction uses a new source commit, incremented version, newly signed APK, and new immutable release.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [apkanalyzer](https://developer.android.com/tools/apkanalyzer), [immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases), and [release integrity verification](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity).
