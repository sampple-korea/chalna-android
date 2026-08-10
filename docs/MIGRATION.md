# Migration

v1.2.0 preserves package `app.chalna.capture`, the v1.1.1 signing certificate, DataStore keys, MediaStore video bytes, Vault files, and the last-capture record.

`LegacyCaptureIndexImporter` performs an idempotent one-time import from `capture-index-v1`:

1. Read the AtomicFile snapshot without rewriting it.
2. Decode each Base64 row independently; malformed/truncated rows are skipped rather than aborting the transaction.
3. Merge exact MediaStore and Vault references with the last-capture record.
4. Deduplicate by stable ID and exact reference.
5. Insert usable rows in a Room transaction.
6. Mark the import complete only after the transaction commits.
7. Rename the legacy file to a read-only backup; retain it for a recovery window before cleanup.
8. Verify actual files later in small background batches. No path/prefix-wide MediaStore import occurs.

A crash before the completion marker safely repeats the transaction because IDs are stable and inserts are conflict-safe. Room schema migrations are explicit and `fallbackToDestructiveMigration` is forbidden. Instrumentation fixtures cover empty, mixed-destination, malformed, truncated, duplicate, missing-file, repeat-run, and rollback cases.

Release verification installs the immutable v1.1.1 APK and then installs v1.2.0 with `adb install -r`, without uninstalling. Signer equality is checked before installation. Database fixture migration remains a dedicated Room instrumentation test because a production release APK cannot expose a data-seeding endpoint.
