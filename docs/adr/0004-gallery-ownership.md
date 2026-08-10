# ADR 0004: Exact Gallery ownership

## Context

Scanning `Movies/Chalna` or a filename prefix can import another app's/user's files and requires broader access.

## Decision

Index only exact identities created during Chalna finalize or recovered from the prior exact index/Last Capture record.

## Alternatives

Whole MediaStore scan, prefix scan, and `READ_MEDIA_VIDEO` were rejected.

## Consequences

Chalna cannot discover manually copied lookalikes, which is intentional; privacy and ownership stay precise.

## Verification

Manifest policy forbids media-read permissions; repository tests reject unknown authorities/IDs and avoid path-wide queries.
