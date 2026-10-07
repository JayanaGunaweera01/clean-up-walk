package app.cleanupwalk.detection

import android.graphics.Bitmap
import app.cleanupwalk.domain.CleanupDetection

/**
 * Finds cleanup issues (litter, overflowing bins, damaged infrastructure, fallen branches) in a photo
 */
interface IssueDetector : AutoCloseable {
    fun detect(image: Bitmap): List<CleanupDetection>
}
