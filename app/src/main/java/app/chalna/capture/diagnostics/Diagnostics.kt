package app.chalna.capture.diagnostics

data class DiagnosticEvent(val name: String, val elapsedMillis: Long, val timestampMillis: Long)

class Diagnostics(private val capacity: Int = 20) {
    private val events = ArrayDeque<DiagnosticEvent>()
    @Synchronized fun record(event: DiagnosticEvent) {
        events.addLast(event.copy(name = event.name.take(64), elapsedMillis = event.elapsedMillis.coerceAtLeast(0)))
        while (events.size > capacity.coerceAtLeast(1)) events.removeFirst()
    }
    @Synchronized fun snapshot(): List<DiagnosticEvent> = events.toList()
    @Synchronized fun stats(name: String): TimingStats = TimingStats.of(events.filter { it.name == name }.map { it.elapsedMillis })
}

data class TimingStats(
    val count: Int,
    val min: Long,
    val max: Long,
    val average: Double,
    val median: Double,
    val p95: Long,
) {
    companion object {
        fun of(values: List<Long>): TimingStats {
            if (values.isEmpty()) return TimingStats(0, 0, 0, 0.0, 0.0, 0)
            val sorted = values.sorted()
            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle].toDouble()
            val p95Index = kotlin.math.ceil(sorted.size * .95).toInt().coerceIn(1, sorted.size) - 1
            return TimingStats(sorted.size, sorted.first(), sorted.last(), sorted.average(), median, sorted[p95Index])
        }
    }
}
