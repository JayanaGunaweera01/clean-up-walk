package app.cleanupwalk.ui

import android.content.Context
import app.cleanupwalk.R
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.CleanupWalkSession
import java.time.Duration
import java.util.Locale

/**
 * Text formatting shared by the screens
 */
class Formatters(private val context: Context) {

    private val usesMiles: Boolean
        get() = Locale.getDefault().country in setOf("US", "LR", "MM", "GB")

    fun issueType(type: CleanupIssueType): String = context.getString(
        when (type) {
            CleanupIssueType.Litter -> R.string.issue_litter
            CleanupIssueType.OverflowingBin -> R.string.issue_overflowing_bin
            CleanupIssueType.DamagedInfrastructure -> R.string.issue_damaged_infrastructure
            CleanupIssueType.FallenBranch -> R.string.issue_fallen_branch
        }
    )

    fun distance(meters: Double): String {
        return if (usesMiles) {
            val feet = meters * 3.28084
            if (feet < 1000) String.format(Locale.getDefault(), "%.0f ft", feet)
            else String.format(Locale.getDefault(), "%.1f mi", meters / 1609.344)
        } else {
            if (meters < 1000) String.format(Locale.getDefault(), "%.0f m", meters)
            else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
        }
    }

    fun direction(bearingDegrees: Double): String {
        val names = listOf(
            R.string.dir_n, R.string.dir_ne, R.string.dir_e, R.string.dir_se,
            R.string.dir_s, R.string.dir_sw, R.string.dir_w, R.string.dir_nw
        )
        val index = (((bearingDegrees % 360 + 360) % 360 + 22.5) / 45.0).toInt() % 8
        return context.getString(names[index])
    }

    fun timeLeft(duration: Duration): String {
        val minutes = duration.toMinutes()
        val seconds = duration.seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    fun litter(count: Int): String {
        return context.resources.getQuantityString(R.plurals.litter_pieces, count, count)
    }

    /**
     * "You cleaned 1.8 km of your neighborhood and removed 14 pieces of litter."
     */
    fun result(session: CleanupWalkSession): String {
        val result = context.getString(R.string.walk_result, distance(session.distanceMeters), litter(session.litterRemoved))
        return if (session.issuesReported > 0) {
            result + " " + context.getString(R.string.walk_result_reported, session.issuesReported)
        } else {
            result
        }
    }
}
