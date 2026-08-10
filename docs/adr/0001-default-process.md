# ADR 0001: Default-process integration

## Context

Private Voice Interaction processes read process-local state owned by the default process, so Assistant feedback could disagree with capture.

## Decision

Run voice, session, recognition, capture, tile, and UI components in the default application process and keep application startup lazy.

## Alternatives

AIDL/bound-service IPC was rejected because it adds latency, reconnection, death handling, and multiprocess persistence without product benefit.

## Consequences

State is coherent and dispatch avoids IPC; the selected voice service can keep a lightweight process warm.

## Verification

Manifest policy rejects private process attributes; installed role/session tests observe the same state repository.
