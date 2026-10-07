package app.cleanupwalk.domain

import java.time.Duration
import java.time.Instant

/**
 * Accumulates walked distance from GPS fixes, ignoring inaccurate fixes, jitter while standing
 * still, and jumps that would require moving faster than a person walks or jogs.
 */
class WalkDistanceTracker(
    private val minStepMeters: Double = 5.0,
    private val maxAccuracyMeters: Float = 35f,
    private val maxSpeedMetersPerSecond: Double = 4.0,
    initialDistanceMeters: Double = 0.0
) {

    var distanceMeters: Double = initialDistanceMeters
        private set

    private var lastLocation: GeoPoint? = null
    private var lastTime: Instant? = null

    /**
     * @return the total distance walked in meters
     */
    fun update(location: GeoPoint, accuracyMeters: Float?, time: Instant): Double {
        if (accuracyMeters != null && accuracyMeters > maxAccuracyMeters) {
            return distanceMeters
        }

        if (location.latitude == 0.0 && location.longitude == 0.0) {
            return distanceMeters
        }

        val previous = lastLocation
        val previousTime = lastTime
        if (previous == null || previousTime == null) {
            lastLocation = location
            lastTime = time
            return distanceMeters
        }

        val step = CleanupGeo.distance(previous, location)
        if (step < minStepMeters) {
            // Don't move the anchor, so slow walking still adds up
            return distanceMeters
        }

        val seconds = Duration.between(previousTime, time).toMillis() / 1000.0
        if (seconds > 0 && step / seconds > maxSpeedMetersPerSecond) {
            // A GPS jump (or a ride in a vehicle) - re-anchor without counting it
            lastLocation = location
            lastTime = time
            return distanceMeters
        }

        distanceMeters += step
        lastLocation = location
        lastTime = time
        return distanceMeters
    }
}
