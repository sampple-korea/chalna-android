# ADR 0010: No pre-capture

## Context

Keeping camera/microphone warm could reduce latency but violates user expectation, privacy, battery, and thermal boundaries.

## Decision

Acquire CameraX provider, bind VideoCapture, prepare Recorder, and enable audio only after an explicit accepted trigger.

## Alternatives

Ring buffer, pre-buffer, warm provider/session, persistent binding, hidden recording, and reboot restart were rejected.

## Consequences

Cold camera startup remains device-dependent; privacy indicator and recording correspond to a user action.

## Verification

Source policy, manifest audit, hot-path tests, and physical privacy-indicator/device latency testing.
