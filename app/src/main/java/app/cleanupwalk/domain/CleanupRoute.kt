package app.cleanupwalk.domain

import java.time.Duration

/**
 * A stop on a cleanup walk. Stops without an issue are exploration waypoints used to fill the
 * remaining time with new ground where problems haven't been spotted yet.
 */
data class CleanupStop(
    val location: GeoPoint,
    val issue: CleanupIssue? = null
) {
    val isExploration: Boolean
        get() = issue == null
}

/**
 * A closed walking loop that starts and ends at the same place
 * @param stops the stops in visiting order (the start is not included)
 * @param distanceMeters the estimated walking distance, including the return to the start
 * @param duration the estimated walking + dwell time
 */
data class CleanupRoute(
    val start: GeoPoint,
    val stops: List<CleanupStop>,
    val distanceMeters: Double,
    val duration: Duration
) {
    val issueStops: List<CleanupStop>
        get() = stops.filter { !it.isExploration }
}
