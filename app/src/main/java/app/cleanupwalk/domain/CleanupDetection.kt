package app.cleanupwalk.domain

/**
 * A single object found in a photo by the detector
 * @param confidence the model confidence [0, 1]
 * @param box the normalized bounding box [left, top, right, bottom] in [0, 1], if known
 */
data class CleanupDetection(
    val type: CleanupIssueType,
    val confidence: Float,
    val box: List<Float>? = null
)

/**
 * The interpreted result of scanning one photo
 * @param counts the number of confident detections per issue type
 * @param primaryType the issue type to suggest to the walker, null if nothing was found
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

/**
 * Turns raw detections into something the walker can confirm with one tap
 */
class CleanupScanInterpreter(
    private val minConfidence: Float = 0.4f,
    private val overlapThreshold: Float = 0.5f
) {

    fun interpret(detections: List<CleanupDetection>): CleanupScanResult {
        val confident = suppressDuplicates(detections.filter { it.confidence >= minConfidence })
        if (confident.isEmpty()) {
            return CleanupScanResult.empty
        }

        val byType = confident.groupBy { it.type }
        val counts = byType.mapValues { it.value.size }

        // Suggest the type that matters most for the route: priority weighted by how sure we are
        val primary = byType.maxByOrNull { (type, items) ->
            type.priority * items.maxOf { it.confidence }
        } ?: return CleanupScanResult.empty

        return CleanupScanResult(
            counts,
            primary.key,
            primary.value.maxOf { it.confidence }
        )
    }

    /**
     * Non-maximum suppression per type, so one bottle isn't counted twice
     */
    private fun suppressDuplicates(detections: List<CleanupDetection>): List<CleanupDetection> {
        val kept = mutableListOf<CleanupDetection>()
        for (detection in detections.sortedByDescending { it.confidence }) {
            val box = detection.box
            val isDuplicate = box != null && kept.any {
                it.type == detection.type && it.box != null && iou(it.box, box) > overlapThreshold
            }
            if (!isDuplicate) {
                kept.add(detection)
            }
        }
        return kept
    }

    private fun iou(a: List<Float>, b: List<Float>): Float {
        val left = maxOf(a[0], b[0])
        val top = maxOf(a[1], b[1])
        val right = minOf(a[2], b[2])
        val bottom = minOf(a[3], b[3])
        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val areaA = maxOf(0f, a[2] - a[0]) * maxOf(0f, a[3] - a[1])
        val areaB = maxOf(0f, b[2] - b[0]) * maxOf(0f, b[3] - b[1])
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
