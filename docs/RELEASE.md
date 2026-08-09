# Release record and verification

Android work runs in GitHub Actions, never on the local PC. The `v1.0.0` workflow executes from the exact `main` commit with `contents: write` only in the release job; ordinary CI is read-only.

## Signing

The dedicated user-provided PKCS#12 key is stored outside the repository. Local inspection verified alias `dev-siro`, `PrivateKeyEntry`, RSA-4096, certificate validity through 2126, and the expected SHA-256 certificate fingerprint. The workflow receives five independently stored secrets:

- `CHALNA_RELEASE_KEYSTORE_B64`
- `CHALNA_RELEASE_STORE_PASSWORD`
- `CHALNA_RELEASE_KEY_ALIAS`
- `CHALNA_RELEASE_KEY_PASSWORD`
- `CHALNA_RELEASE_CERT_SHA256`

Passwords are step-scoped, the restored file is mode `0600`, the certificate digest must equal the pinned expected value, and signing material is removed with `if: always()`.

## Candidate evidence

- Android CI: [run 31311630513](https://github.com/sampple-korea/chalna-android/actions/runs/31311630513), pass.
- UI QA: [run 31311630522](https://github.com/sampple-korea/chalna-android/actions/runs/31311630522), pass.
- Screenshots: artifact `ui-qa-api-34-31311630522`, 15 PNG states; final selected images committed.
- Repository visibility and immutable-release setting were re-read through GitHub CLI/API before release preparation.

## Release transaction

`.github/workflows/release.yml` validates SemVer and `gh release verify*` capability, checks that the release does not already exist, restores/validates signing identity, runs policy/lint/release unit tests, and builds a minified signed APK. It then verifies zip alignment, APK signature schemes, non-debug certificate, expected certificate fingerprint, package, version name/code, min/target SDK, debuggable state, and forbidden permissions. The exact signed APK is installed and launched on an API 34 emulator before publication.

The workflow creates a draft containing exactly these nonempty assets:

- `chalna-v1.0.0-release.apk`
- `chalna-v1.0.0-SHA256.txt`
- `chalna-v1.0.0-build-info.json`

Only after asset names/count/sizes and target commit pass does it publish. Repository immutable releases were enabled before this transaction. After publication, it re-downloads the assets, checks SHA-256, runs `gh release verify-asset` and `gh release verify`, reruns `apksigner`, and rechecks package/version. A failed mutable draft is cleaned up; a published immutable release is never deleted or modified by the workflow.

## Authoritative final values

The APK hash, byte size, signer fingerprint, commit SHA, tag, SDK values, and release workflow run ID are stored in the immutable `chalna-v1.0.0-build-info.json` asset. Keeping self-derived APK hashes out of the source commit avoids a circular build where recording the hash changes the artifact being hashed.

GitHub artifact attestations are not relied on for this private personal Pro repository; APK signing, SHA-256, immutable-release verification, and GitHub release/asset integrity verification remain mandatory.

## Rollback

Never overwrite an immutable asset or force-push the release tag. A future correction uses a new source commit, incremented version, newly signed APK, and new immutable release.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [apkanalyzer](https://developer.android.com/tools/apkanalyzer), [immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases), and [release integrity verification](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity).
