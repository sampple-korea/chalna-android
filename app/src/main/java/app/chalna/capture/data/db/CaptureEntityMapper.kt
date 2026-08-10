package app.chalna.capture.data.db

import app.chalna.capture.domain.CaptureItem
import app.chalna.capture.domain.CaptureQuality
import app.chalna.capture.domain.CaptureRecordState
import app.chalna.capture.domain.StorageDestination

internal fun CaptureItem.toEntity(version: Int): CaptureEntity =
    CaptureEntity(
        id = id,
        storageDestination = storageDestination.name,
        contentUri = contentUri,
        privateRef = privateRef,
        displayName = displayName,
        createdAtEpochMillis = createdAtMillis,
        durationMillis = durationMillis,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        quality = quality.name,
        codec = codec,
        frameRate = frameRate,
        bitrate = bitrate,
        audioIncluded = audioIncluded,
        audioKnown = audioKnown,
        mimeType = mimeType,
        metadataKnown = metadataKnown,
        state = state.name,
        favorite = favorite,
        trashedAtEpochMillis = trashedAtMillis,
        exportedFromId = exportedFromId,
        exportedCopyId = exportedCopyId,
        lastVerifiedEpochMillis = lastVerifiedAtMillis,
        createdVersion = version,
        updatedVersion = version,
    )

internal fun CaptureEntity.toDomain(): CaptureItem? =
    runCatching {
        CaptureItem(
            id = id,
            storageDestination = StorageDestination.valueOf(storageDestination),
            contentUri = contentUri,
            privateRef = privateRef,
            displayName = displayName,
            createdAtMillis = createdAtEpochMillis,
            durationMillis = durationMillis,
            quality = runCatching { CaptureQuality.valueOf(quality) }.getOrDefault(CaptureQuality.UNKNOWN),
            audioIncluded = audioIncluded,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            rotationDegrees = rotationDegrees,
            codec = codec,
            frameRate = frameRate,
            bitrate = bitrate,
            audioKnown = audioKnown,
            mimeType = mimeType,
            metadataKnown = metadataKnown,
            state = runCatching { CaptureRecordState.valueOf(state) }.getOrDefault(CaptureRecordState.METADATA_PENDING),
            favorite = favorite,
            trashedAtMillis = trashedAtEpochMillis,
            exportedFromId = exportedFromId,
            exportedCopyId = exportedCopyId,
            lastVerifiedAtMillis = lastVerifiedEpochMillis,
        ).takeIf(CaptureItem::isUsable)
    }.getOrNull()
