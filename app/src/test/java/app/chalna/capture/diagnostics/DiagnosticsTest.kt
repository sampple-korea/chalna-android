package app.chalna.capture.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsTest {
    @Test fun boundedAndComputesStats() {
        val subject = Diagnostics(2)
        subject.record(DiagnosticEvent("start", 10, 1))
        subject.record(DiagnosticEvent("start", 20, 2))
        subject.record(DiagnosticEvent("start", 30, 3))
        assertEquals(listOf(20L, 30L), subject.snapshot().map { it.elapsedMillis })
        assertEquals(TimingStats(2, 20, 30, 25.0, 25.0, 30), subject.stats("start"))
    }

    @Test fun emptyStatsAreDefined() {
        assertEquals(TimingStats(0, 0, 0, 0.0, 0.0, 0), Diagnostics().stats("none"))
    }

    @Test fun p95UsesNearestRank() {
        val subject = Diagnostics(20)
        (1L..20L).forEach { subject.record(DiagnosticEvent("latency", it, it)) }
        assertEquals(19L, subject.stats("latency").p95)
        assertEquals(10.5, subject.stats("latency").median, 0.0)
    }
}
