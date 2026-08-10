# Storage

## Device Gallery

CameraX writes to an exact MediaStore item in `Movies/Chalna`. The destination sets display name, `video/mp4`, relative path, and capture date. No broad storage or media-read permission is requested. Chalna retains only URIs for items it created.

## Chalna Vault

Vault output uses the app-specific external Movies directory. It is not encryption and is removed with app data/uninstall unless copied out first. An unavailable, read-only, or low-space Vault fails explicitly; it never silently falls back to Device Gallery.

`FileProvider` exposes only the Vault subtree declared in `res/xml/file_paths.xml`. Shares use `content://`, `ClipData`, `FLAG_GRANT_READ_URI_PERMISSION`, the selected IDs, and `video/*`; raw paths and user-supplied references are rejected.

## Safety

Before capture, `StatFs` checks destination capacity with a reserve. During recording, CameraX status bytes/duration feed the service; critical remaining space requests a normal stop/finalize. A successful Finalize is not enough: the validator requires readable output, positive size/duration, MP4 metadata, and expected MIME before READY.

The attempt journal records invocation, destination, exact URI/path, display name, epoch/monotonic start, requested audio/quality, and stage. Recovery salvages a valid orphan and only deletes zero-byte, corrupt, unparseable, or safely stale pending output.

Recently Deleted uses MediaStore trash on supported Android versions and an atomic `.trash` move inside Vault. Vault path canonicalization prevents deletion outside its owned directory. Retention is 30 calendar days.
