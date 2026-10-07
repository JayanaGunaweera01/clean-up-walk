package app.cleanupwalk.domain


/**
 * Tracks which stops of a route have been reached
 * @param arrivalRadiusMeters how close the walker must be to count as arriving at a stop
 */
class CleanupWalkProgress(
    val route: CleanupRoute,
    private val arrivalRadiusMeters: Double = 25.0
) {

    private val visited = BooleanArray(route.stops.size)

    val nextStopIndex: Int?
        get() = visited.indexOfFirst { !it }.takeIf { it >= 0 }

    val nextStop: CleanupStop?
        get() = nextStopIndex?.let { route.stops[it] }

    val isComplete: Boolean
        get() = nextStopIndex == null

    val visitedCount: Int
        get() = visited.count { it }

    /**
     * Mark the walker's location. Exploration stops are passed automatically; issue stops are
     * reported as arrived so the walker can resolve them.
     * @return the issue stop the walker just arrived at, if any
     */
    fun update(location: GeoPoint): CleanupStop? {
        val index = nextStopIndex ?: return null
        val stop = route.stops[index]
        if (CleanupGeo.distance(location, stop.location) > arrivalRadiusMeters) {
            return null
        }
        if (stop.isExploration) {
            visited[index] = true
            return null
        }
        return stop
    }

    /**
     * Complete (or skip) the next stop
     */
    fun completeNext() {
        nextStopIndex?.let { visited[it] = true }
    }

    fun distanceToNext(location: GeoPoint): Double? {
        return nextStop?.let { CleanupGeo.distance(location, it.location) }
    }

    /**
     * The bearing to the next stop in degrees, or the bearing back to the start once complete
     */
    fun bearingToNext(location: GeoPoint): Double {
        val target = nextStop?.location ?: route.start
        return CleanupGeo.bearing(location, target)
    }

    fun distanceToStart(location: GeoPoint): Double {
        return CleanupGeo.distance(location, route.start)
    }
}
