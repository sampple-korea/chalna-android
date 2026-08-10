# ADR 0006: Optional notification permission

## Context

Android permits a foreground service to call `startForeground` when POST_NOTIFICATIONS is denied, though drawer visibility is reduced; blocking capture contradicted the core Assistant use case.

## Decision

Notification grant is optional readiness. Chalna always calls required foreground APIs, supports second-invocation stop, and exposes direct notification settings.

## Alternatives

Making the runtime grant mandatory was rejected.

## Consequences

Denied users can record but may lack drawer STOP/saved notifications; Android's task-manager disclosure remains platform controlled.

## Verification

Readiness and instrumentation tests cover denied notifications with successful dispatch.
