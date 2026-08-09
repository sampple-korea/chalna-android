# Third-party notices

## Noto Sans Korean

The Chalna interface embeds the variable Noto Sans Korean font from the official Google Fonts repository. It is licensed under the SIL Open Font License 1.1. The complete license is included at `licenses/NotoSansKR-OFL.txt`.

Source: https://github.com/google/fonts/tree/main/ofl/notosanskr

Chalna source declares the following direct dependencies. This is an inventory aid, not a substitute for the license files resolved into the verified build. Exact transitive versions and license texts are **Pending verification** until CI produces and archives a dependency/SBOM report.

| Component family | Declared version | Typical license | Source |
|---|---:|---|---|
| Android Gradle Plugin | 9.3.1 | Apache-2.0 | [Android build releases](https://developer.android.com/build/releases/gradle-plugin) |
| Kotlin / Compose compiler plugin | 2.3.21 | Apache-2.0 | [Kotlin](https://github.com/JetBrains/kotlin) |
| AndroidX Compose BOM / Runtime / UI / Foundation / Animation | 2026.06.00 BOM | Apache-2.0 | [AndroidX](https://github.com/androidx/androidx) |
| CameraX Core / Camera2 / Lifecycle / Video | 1.6.1 | Apache-2.0 | [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera) |
| Activity, Core, Lifecycle, DataStore, AndroidX Test, Espresso | versions pinned in `libs.versions.toml` | Apache-2.0 | [AndroidX releases](https://developer.android.com/jetpack/androidx/versions) |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 | [JUnit 4](https://github.com/junit-team/junit4) |

Release procedure: generate the resolved dependency graph; collect bundled notices and licenses; verify every transitive component and license; preserve required attributions in the distributed artifact/release; record discrepancies here. No Material library, Material icon package, or Material ripple dependency is intentionally permitted.
