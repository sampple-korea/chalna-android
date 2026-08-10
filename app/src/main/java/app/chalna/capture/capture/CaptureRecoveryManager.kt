package app.chalna.capture.capture

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.StorageDestination
import app.chalna.capture.gallery.GalleryRepository
import app.chalna.capture.media.AndroidGalleryMedia
import kotlinx.coroutines.CancellationException

sealed interface CaptureRecoveryResult {
    data object NothingToRecover : CaptureRecoveryResult

    data class Salvaged(
        val item: CaptureItem,
    ) : CaptureRecoveryResult

    data class Deferred(
        val captureId: String,
    ) : CaptureRecoveryResult

    data class RemovedCorrupt(
        val captureId: String,
    ) : CaptureRecoveryResult

    data class Failed(
        val captureId: String,
    ) : CaptureRecoveryResult
}

class CaptureRecoveryManager(
    private val context: Context,
    private val attempts: CaptureAttemptStore,
    private val gallery: GalleryRepository,
    private val media: AndroidGalleryMedia,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun recover(): CaptureRecoveryResult {
        val journalPresent = attempts.hasAttempt()
        val attempt = attempts.read()
        if (attempt == null) {
            if (!journalPresent) return CaptureRecoveryResult.NothingToRecover
            attempts.clear()
            return CaptureRecoveryResult.RemovedCorrupt(UNKNOWN_CAPTURE_ID)
        }
        val candidate =
            CaptureItem(
                id = attempt.captureId,
                storageDestination = attempt.destination,
                contentUri = attempt.contentUri,
                privateRef = attempt.privateRef,
                displayName = attempt.displayName,
                createdAtMillis = attempt.startedAtEpochMillis,
                durationMillis = 0,
                quality = attempt.requestedQuality,
                audioIncluded = attempt.requestedAudio,
                audioKnown = false,
                metadataKnown = false,
                state = CaptureRecordState.METADATA_PENDING,
            )
        val validated =
            try {
                media.validate(candidate)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        if (validated != null) {
            if (attempt.destination == StorageDestination.DEVICE_GALLERY && !publishPending(attempt.contentUri)) {
                return CaptureRecoveryResult.Failed(attempt.captureId)
            }
            return try {
                val indexed =
                    gallery.recordFinalized(
                        validated.copy(state = CaptureRecordState.READY).toLastCapture(),
                    )
                attempts.clear()
                CaptureRecoveryResult.Salvaged(indexed)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                CaptureRecoveryResult.Failed(attempt.captureId)
            }
        }
        val age = nowEpochMillis() - attempt.startedAtEpochMillis
        if (age in 0 until RECOVERY_STALE_MILLIS) return CaptureRecoveryResult.Deferred(attempt.captureId)
        return try {
            media.deletePermanently(candidate)
            attempts.clear()
            CaptureRecoveryResult.RemovedCorrupt(attempt.captureId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CaptureRecoveryResult.Failed(attempt.captureId)
        }
    }

    private fun publishPending(raw: String): Boolean {
        val uri = runCatching { raw.toUri() }.getOrNull() ?: return false
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return false
        return context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        ) == 1
    }

    private companion object {
        const val RECOVERY_STALE_MILLIS = 60_000L
        const val UNKNOWN_CAPTURE_ID = "unknown"
    }
}
