package app.cleanupwalk.domain

/**
 * What a scan found in one photo
 * @param counts the number of items seen per issue type
 * @param primaryType the issue type to suggest to the walker, null if nothing was found
 * @param confidence how sure the model was [0, 1]
 */
data class CleanupScanResult(
    val counts: Map<CleanupIssueType, Int>,
    val primaryType: CleanupIssueType?,
    val confidence: Float?
) {
    val isEmpty: Boolean
        get() = primaryType == null

    companion object {
        val empty = CleanupScanResult(emptyMap(), null, null)
    }
}
