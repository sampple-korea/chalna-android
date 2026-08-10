# ADR 0003: Room capture index

## Context

AtomicFile/Base64 storage rewrote the whole library and could not support transactional paging, trash, export relationships, or recovery work.

## Decision

Use stable Room with WAL, exported schemas, unique IDs, Flow/Paging queries, transactions, and explicit migrations.

## Alternatives

Keeping the line codec or adopting a custom SQLite layer was rejected for scalability and migration risk.

## Consequences

Metadata becomes queryable and transactional; database size/dependency cost increases modestly.

## Verification

Schema JSON is committed, migration fixtures run in instrumentation, and destructive migration APIs are forbidden.
