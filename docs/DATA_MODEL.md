# Data model

`ChalnaDatabase` is the durable metadata store. Room uses WAL, exported schemas, transactions, exact IDs, and no destructive fallback.

## Capture

The capture row stores an opaque ID, destination, exact content URI or Vault-relative reference, display name, epoch creation time, duration, size, dimensions, rotation, effective quality, codec, frame rate, bitrate, audio-known/audio-included state, MIME type, metadata state, favorite/trash state, export relationship, verification time, and schema/app creation/update versions.

States distinguish `READY`, `METADATA_PENDING`, and `TRASHED`. Media bytes are authoritative; a database row alone does not prove that a file exists. Gallery access verifies the addressed item and removes/repairs stale rows without scanning unrelated media.

## Pending operation

Pending-operation rows represent durable reconciliation work after media succeeds but indexing fails. This boundary prevents a valid user video from being deleted merely because Room persistence failed.

## Playback state

Playback position is keyed by capture ID. Writes are throttled by the player controller. A position near completion resumes from the start.

## Identity and ordering

New capture IDs are collision-resistant opaque values. Legacy exact references derive a stable SHA-256-based ID for idempotent import. Unique indexes reject duplicate identities. Gallery order is deterministic, with ID as the stable tie-breaker.

Dates use `Instant`/`ZoneId`/`LocalDate`; Today and Yesterday use calendar arithmetic rather than subtracting a fixed 86,400,000 milliseconds.
