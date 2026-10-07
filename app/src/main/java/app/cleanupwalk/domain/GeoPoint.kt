package app.cleanupwalk.domain

/**
 * A location on the earth in decimal degrees
 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isZero: Boolean
        get() = latitude == 0.0 && longitude == 0.0
}
