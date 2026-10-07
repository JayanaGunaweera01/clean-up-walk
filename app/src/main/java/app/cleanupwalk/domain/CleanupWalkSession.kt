package app.cleanupwalk.domain

import java.time.Duration
import java.time.Instant

/**
 * A single cleanup walk
 * @param plannedDuration the time the walker chose (ex. 30 minutes)
 * @param litterRemoved the number of pieces of litter picked up
 * @param issuesReported the number of new issues logged during the walk
 * @param issuesResolved the number of known issues cleaned up or confirmed as reported during the walk
 */
data class CleanupWalkSession(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val plannedDuration: Duration,
    val distanceMeters: Double = 0.0,
    val litterRemoved: Int = 0,
    val issuesReported: Int = 0,
    val issuesResolved: Int = 0
) {
    val isActive: Boolean
        get() = endTime == null

    fun elapsed(now: Instant = Instant.now()): Duration {
        val end = endTime ?: now
        return Duration.between(startTime, end).let { if (it.isNegative) Duration.ZERO else it }
    }

    fun remaining(now: Instant = Instant.now()): Duration {
        val remaining = plannedDuration.minus(elapsed(now))
        return if (remaining.isNegative) Duration.ZERO else remaining
    }
}

/**
 * Lifetime totals across all finished walks
 */
data class CleanupWalkTotals(
    val walks: Int,
    val distanceMeters: Double,
    val litterRemoved: Int,
    val issuesReported: Int,
    val issuesResolved: Int,
    val time: Duration
) {
    companion object {
        fun from(sessions: List<CleanupWalkSession>): CleanupWalkTotals {
            val finished = sessions.filter { !it.isActive }
            return CleanupWalkTotals(
                finished.size,
                finished.sumOf { it.distanceMeters },
                finished.sumOf { it.litterRemoved },
                finished.sumOf { it.issuesReported },
                finished.sumOf { it.issuesResolved },
                finished.fold(Duration.ZERO) { acc, session -> acc.plus(session.elapsed()) }
            )
        }
    }
}
