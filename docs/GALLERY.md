# Gallery

Chalna Gallery is a view of exact Chalna-created records, not the device library. It declares neither `READ_MEDIA_VIDEO` nor a filename/path scanner.

Room supplies a `PagingSource` for latest, oldest, longest, and largest ordering; destination, favorite, active, and trash filters are query inputs. UI uses stable capture IDs and date separators. Opening the first page does not wait for all-file validation or metadata extraction.

Missing legacy metadata is backfilled lazily. Existence reconciliation runs in bounded batches on relevant access. Visible thumbnail requests use target-sized `ContentResolver.loadThumbnail` work on `Dispatchers.IO`, cancellation when composition leaves the viewport, and an LRU keyed by identity/modified state/size. Loading uses a quiet neutral surface rather than the launcher mark.

Selection supports select-all by an ID query, share, favorite, trash, restore, permanent delete, and Vault export. Batch work reports typed progress and partial failures; successful items remain successful. Export relationships make the normal Vault-to-MediaStore copy idempotent. A missing exported copy clears the relationship before a new copy.

Deleting media and metadata is coordinated so partial failure is never shown as success. The current Last Capture is replaced by the next newest valid row. Active/pending outputs do not enter Gallery until validation/finalize succeeds.
