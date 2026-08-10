package app.chalna.capture.data

import app.chalna.capture.domain.StorageDestination
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIndexTest {
    @Test fun v111FixtureDecodesDeviceAndVaultRows() {
        val fixture = buildString {
            append("CHALNA_INDEX_1\t1\n")
            append(row("11111111-1111-1111-1111-111111111111", "DEVICE_GALLERY", "content://media/external/video/media/1", "", "CHALNA_1.mp4"))
            append(row("22222222-2222-2222-2222-222222222222", "CHALNA_VAULT", "content://app.chalna.capture.files/vault/2", "Chalna/CHALNA_2.mp4", "CHALNA_2.mp4"))
        }
        val decoded = LegacyCaptureIndexCodec.decode(fixture)
        assertTrue(decoded.migrationComplete)
        assertEquals(2, decoded.items.size)
        assertEquals(StorageDestination.DEVICE_GALLERY, decoded.items[0].storageDestination)
        assertEquals(StorageDestination.CHALNA_VAULT, decoded.items[1].storageDestination)
        assertEquals(0, decoded.malformedRows)
    }

    @Test fun malformedAndTruncatedRowsAreSkippedIndividually() {
        val fixture = "CHALNA_INDEX_1\t1\nnot-base64\n" +
            row("33333333-3333-3333-3333-333333333333", "DEVICE_GALLERY", "content://media/external/video/media/3", "", "CHALNA_3.mp4")
        val decoded = LegacyCaptureIndexCodec.decode(fixture)
        assertEquals(1, decoded.items.size)
        assertEquals(1, decoded.malformedRows)
    }

    @Test fun unknownQualityFallsBackWithoutDroppingVideo() {
        val fixture = "CHALNA_INDEX_1\t0\n" + row(
            "44444444-4444-4444-4444-444444444444",
            "DEVICE_GALLERY",
            "content://media/external/video/media/4",
            "",
            "CHALNA_4.mp4",
            quality = "FUTURE_QUALITY",
        )
        val decoded = LegacyCaptureIndexCodec.decode(fixture)
        assertEquals(1, decoded.items.size)
        assertEquals("UNKNOWN", decoded.items.single().quality.name)
    }

    private fun row(
        id: String,
        destination: String,
        uri: String,
        privateRef: String,
        name: String,
        quality: String = "FHD",
    ): String = listOf(
        id,
        destination,
        uri,
        privateRef,
        name,
        "1700000000000",
        "1200",
        quality,
        "1",
        "1024",
        "1920",
        "1080",
        "1",
    ).joinToString("\t") { encoder.encodeToString(it.toByteArray(StandardCharsets.UTF_8)) } + "\n"

    private companion object {
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
