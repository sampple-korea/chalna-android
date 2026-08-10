# Security policy

## Supported versions

Only the newest published immutable GitHub Release is supported. v1.2.0 source targets package `app.chalna.capture`, version code 4, and the established release signer; the Release build-info asset is the authoritative artifact ledger.

## Reporting

Report suspected vulnerabilities privately to the repository owner through GitHub Security Advisories. Do not open a public issue containing exploit details, keys, recordings, device identifiers, or personal data. Include affected commit/artifact hash, Android version/OEM, reproduction steps, impact, and a minimal redacted log. Response-time commitments are **Pending verification** and are not contractually guaranteed.

## Security model

- The repository and release workflow are private; release artifacts are not public by default.
- The app declares no `INTERNET` permission and disables cleartext traffic. Lack of `INTERNET` materially limits app-initiated network access but does not replace dependency and artifact review.
- Camera and microphone are accessed only after an explicit supported trigger. Boot capture, warm-up, persistent binding, pre-buffering, and background surveillance are prohibited.
- The capture service is non-exported. System-bound voice services require `android.permission.BIND_VOICE_INTERACTION`.
- Release credentials belong only in protected GitHub secrets/environments. Keystores, passwords, certificates with private keys, and signing output must not enter Git history or artifacts.
- Media uses exact Chalna-created MediaStore identities or a canonical app-specific Vault path; Android and the user control downstream access and sharing.
- MainActivity accepts only known actions and opaque Room capture IDs. FileProvider exposes only the Vault subtree and never accepts a user path.
- Production telemetry is bounded outcome metadata and excludes assist structure, screenshots, captured media, account data, location, and foreground-app identity.

## Release security gates

Before distribution, inspect the exact downloaded artifact: SHA-256, APK signature/certificate, package/version/SDK metadata, permissions, exported components, debuggable flag, native libraries, and absence of secrets. Match it to an immutable commit and successful CI run. See [release procedure](docs/RELEASE.md).

The repository-grounded asset/boundary/threat analysis is in [THREAT_MODEL.md](docs/THREAT_MODEL.md).

References: [Android app security](https://developer.android.com/privacy-and-security/security-best-practices), [exported component risks](https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components), [app signing](https://developer.android.com/studio/publish/app-signing), and [GitHub encrypted secrets](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions).
