# Release procedure

Android build/test/package/emulator work runs only in GitHub Actions. v1.2.0 is version code 4 and package `app.chalna.capture`; `version.properties` is the single source checked by Gradle and the workflow.

## Signing continuity

The update signer is anchored to the immutable v1.1.1 APK certificate SHA-256:

`E1344975A288EC785AB12841CA8719B2115EADF41AE6F6E7AB8770979B7FA2B9`

The release job requires five separate protected secrets: keystore Base64, store password, alias, key password, and expected certificate. Passwords are step-scoped. The restored PKCS#12 is mode 0600, validated before Gradle, and removed in an `always()` step. Candidate APK and AAB certificates must equal the workflow anchor, secret, and downloaded v1.1.1 signer.

The user-accessible backup remains outside the repository at `C:\Users\root\OneDrive\Desktop\dev-siro.p12`. Secret values are never documented or logged.

## CI gates

Before dispatch, the exact main commit must have green Android CI, UI QA, Benchmark, and Security workflows:

- policy, ktlint, Detekt, Android Lint, JVM tests, Room migration tests, dependency graph, and debug APK;
- API emulator instrumentation, Assistant role assignment, accessibility and deterministic screenshot suite;
- Baseline Profile candidate, macrobenchmark JSON, and trace artifacts;
- actionlint, shellcheck, secret scan, CodeQL, dependency review where applicable, and CycloneDX SBOM.

## Artifact build and inspection

`.github/workflows/release.yml` accepts only source-matching SemVer, refuses an existing tag/release, builds minified/resource-shrunk signed APK and AAB, then validates:

- application ID, version name/code, min/target SDK, non-debuggable state;
- APK zipalign with 16 KB page alignment and APK Signature Scheme verification;
- AAB bundletool validation, JAR signature, package/version, and `PAGE_ALIGNMENT_16K` config;
- generated package-only startup/baseline profile sources and compiled Baseline Profile entries in both APK and AAB;
- every native ELF LOAD segment alignment;
- exact signer continuity with v1.1.1;
- no Internet, media-read, broad storage, overlay, Accessibility Service, debug Activity, Visual Lab, production Diagnostics, or unexpected exported component;
- required Voice Interaction/session/recognition/default category/Quick Tile components;
- no resolved Material/Material icon/Media3 Material UI dependency;
- non-empty CycloneDX SBOM and private R8 mapping evidence.

The workflow then performs an uninstall-free v1.1.1 → v1.2.0 update install, clean install, Assistant role qualification, launch smoke, and API 35 16 KB page-size install/launch.

## Immutable publication

The draft is created only after every local gate and contains exactly:

- `chalna-v1.2.0-release.apk`
- `chalna-v1.2.0-release.aab`
- `chalna-v1.2.0-SHA256.txt`
- `chalna-v1.2.0-build-info.json`
- `chalna-v1.2.0-sbom.json`

Asset names/count/sizes and target commit are checked before publication. A failed draft may be removed; a published immutable release is never mutated.

After publish, all assets are downloaded again. The job checks both hashes, `gh release verify`, `gh release verify-asset` for APK and AAB, immutable state, APK/AAB signer, package/version, bundle validity, build-info commit, and SBOM. The build-info records package/version/SDK/toolchain, exact commit/tag/run, sizes/hashes, previous/current signer, and reproducible commit timestamp without secrets.

GitHub immutable-release attestation is mandatory for this repository. Additional Actions provenance attestation is recorded when the repository/account supports it; lack of optional provenance does not weaken checksum, platform signature, or immutable-release verification.

No release claim is valid until the workflow logs, API release metadata, downloaded artifacts, signatures, checksums, package metadata, and clean/update install evidence are inspected.
