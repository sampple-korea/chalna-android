package app.chalna.capture.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModelsTest {
    @Test fun readinessRequiresMicrophoneOnlyWhenAudioEnabled() {
        assertTrue(Readiness(true, false, false, true).fullyReady)
        val withAudio = Readiness(true, false, true, true)
        assertFalse(withAudio.canCapture)
        assertEquals(setOf(Requirement.MICROPHONE_PERMISSION), withAudio.missing)
    }

    @Test fun qualityFallbackStartsAtPreferenceAndIncludesAvailableOnly() {
        assertEquals(
            listOf(CaptureQuality.FHD, CaptureQuality.HD),
            QualityFallback.ordered(CaptureQuality.FHD, setOf(CaptureQuality.FHD, CaptureQuality.HD)),
        )
    }

    @Test fun filenameIsUtcStableAndSafe() {
        assertEquals("CHALNA_19700101_000000_000_ab12.mp4", CaptureFileNames.video(0, "ab-12"))
    }

    @Test fun recoveryNeverClaimsInterruptedCaptureIsActive() {
        assertEquals(CaptureState.Idle, ProcessRecovery.recovered(CaptureState.Idle))
        assertTrue(ProcessRecovery.recovered(CaptureState.Recording("x", 1, 1)) is CaptureState.Failed)
    }

    @Test fun lastCaptureRequiresContentUri() {
        val id = "11111111-1111-1111-1111-111111111111"
        assertTrue(LastCapture("content://media/1", 1, 1, id = id, sizeBytes = 1).isUsable())
        assertFalse(LastCapture("file:///tmp/x", 1, 1, id = id, sizeBytes = 1).isUsable())
    }
}
