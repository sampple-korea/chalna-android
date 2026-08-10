package app.chalna.capture.data

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import app.chalna.capture.domain.LastCapture

class LastCaptureValidator(
    private val resolver: ContentResolver,
) {
    fun exists(capture: LastCapture?): Boolean {
        if (capture == null || !capture.isUsable()) return false
        val uri = runCatching { capture.uri.toUri() }.getOrNull() ?: return false
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && !cursor.isNull(0) && cursor.getLong(0) >= 0
            } ?: false
        }.getOrDefault(false)
    }
}
