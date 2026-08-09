package app.chalna.capture.diagnostics

data class DiagnosticEvent(val name: String, val elapsedMillis: Long, val timestampMillis: Long)

class Diagnostics(private val capacity: Int = 64) {
    private val events = ArrayDeque<DiagnosticEvent>()
    @Synchronized fun record(event: DiagnosticEvent) {
        events.addLast(event.copy(name = event.name.take(64), elapsedMillis = event.elapsedMillis.coerceAtLeast(0)))
        while (events.size > capacity.coerceAtLeast(1)) events.removeFirst()
    }
    @Synchronized fun snapshot(): List<DiagnosticEvent> = events.toList()
    @Synchronized fun stats(name: String): TimingStats = TimingStats.of(events.filter { it.name == name }.map { it.elapsedMillis })
}

data class TimingStats(val count: Int, val min: Long, val max: Long, val average: Double) {
    companion object {
        fun of(values: List<Long>): TimingStats = if (values.isEmpty()) TimingStats(0, 0, 0, 0.0)
        else TimingStats(values.size, values.min(), values.max(), values.average())
    }
}
