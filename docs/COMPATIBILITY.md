# Compatibility

## Supported baseline

Android 10/API 29 and newer; target API 36, compile API 37.1. A rear camera is required. Microphone is optional when audio is disabled. Phones are the primary form factor; tablets/foldables should remain usable but Assistant affordances may differ. Android TV, Automotive, Wear OS, ChromeOS, and managed-device support are not claimed.

## Platform behavior

| Area | Expected | Risk / required evidence |
|---|---|---|
| API 29–32 | Assistant role metadata with session and non-capturing recognition services; legacy notification behavior | OEM gesture routing, background camera restrictions |
| API 33 | Runtime notification permission | denied notification visibility and stop recovery |
| API 34–36 | camera/microphone FGS types and while-in-use restrictions | valid Assistant-trigger exemption/path, start timing |
| Keyguard | system may call keyguard Assistant entry point | OEM support, strong-auth state, no unlock/bypass |
| Device Gallery | scoped MediaStore output under `Movies/Chalna` without `READ_MEDIA_VIDEO` | visibility, finalize, deletion, storage-full behavior |
| Chalna Vault | app-private local file output | finalize, URI grants, export/share/delete, clear-data/uninstall behavior |
| Media3 1.11.0 | local indexed content playback | codecs, seek/duration, lifecycle/surface release, audio focus |
| CameraX quality | select supported rear-camera profile | fallback order, encoder failures, lens availability |

API 37 behavior is outside the v1 production target and requires later validation.

API 34 CI installs Chalna and assigns `android.app.role.ASSISTANT` without bypassing qualification. It verifies that Android maps both `voice_interaction_service` and `voice_recognition_service` to Chalna. The recognition component rejects speech requests without microphone access; its presence satisfies Android's role contract and does not turn Chalna into a speech assistant.

## OEM limitations

Samsung, Google Pixel, Xiaomi/Redmi, Oppo/OnePlus/Realme, Vivo, Motorola, and other vendors may rename default-assistant settings; reserve power-button/gesture entry points; kill background processes; suppress autostart; alter keyguard behavior; delay notifications; or impose camera/thermal policies. Chalna must report failure and release resources, not attempt hidden persistence, accessibility/overlay workarounds, boot capture, battery-optimization coercion, or unrelated permissions.

Work profiles, parental controls, enterprise policy, disabled camera sensors, privacy toggles, concurrent camera use, calls, low storage, thermal throttling, and Do Not Disturb can alter behavior. Supported status is **Pending verification** until the corresponding matrix row passes.

References: [Assistant/VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService), [foreground services](https://developer.android.com/develop/background-work/services/fgs), [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [camera privacy controls](https://developer.android.com/training/permissions/explaining-access), and [CameraX device compatibility](https://developer.android.com/media/camera/camerax/devices).
