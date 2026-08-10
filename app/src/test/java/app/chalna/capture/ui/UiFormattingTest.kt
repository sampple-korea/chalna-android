package app.chalna.capture.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormattingTest {
    @Test fun durationOverOneHourUsesHourMinuteSecondFormat() {
        assertEquals("1:03:42", formatDuration(3_822_000L))
        assertEquals("00:00", formatDuration(-1L))
    }
}
