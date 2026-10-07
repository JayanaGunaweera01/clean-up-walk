package app.cleanupwalk.domain

import java.time.Instant

/**
 * A problem spotted during (or before) a cleanup walk
 * @param count the number of items seen at this spot (ex. pieces of litter)
 * @param resolvedOn when the issue was cleaned up or reported, null if still open
 */
data class CleanupIssue(
    val id: Long,
    val type: CleanupIssueType,
    val location: GeoPoint,
    val reportedOn: Instant,
    val count: Int = 1,
    val resolvedOn: Instant? = null,
    val confidence: Float? = null,
    val notes: String? = null
) {
    val isResolved: Boolean
        get() = resolvedOn != null
}
