# ADR 0002: Single-consumer capture actor

## Context

One coroutine per service intent allowed ordering and Starting-cancellation races.

## Decision

Use an unlimited input channel with one consumer. Resolve Toggle atomically from authoritative state and return typed invocation receipts.

## Alternatives

A mutex around independent jobs was rejected because cancellation and message order remain implicit.

## Consequences

Only one engine operation/finalize runs; every command has deterministic ordering and testable results.

## Verification

Actor/concurrency tests cover duplicate IDs, alternating commands, concurrent stop sources, Starting cancel, Saving busy, and destruction.
