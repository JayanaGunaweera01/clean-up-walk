package app.cleanupwalk.walk

import app.cleanupwalk.data.CleanupRepository
import app.cleanupwalk.domain.CleanupIssue
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.CleanupRoutePlanner
import app.cleanupwalk.domain.CleanupStop
import app.cleanupwalk.domain.CleanupWalkProgress
import app.cleanupwalk.domain.CleanupWalkSession
import app.cleanupwalk.domain.GeoPoint
import app.cleanupwalk.domain.WalkDistanceTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * A snapshot of the walk in progress for the UI
 */
data class WalkState(
    val session: CleanupWalkSession,
    val progress: CleanupWalkProgress?,
    val location: GeoPoint? = null,
    val arrivedAt: CleanupStop? = null,
    /** Changes whenever the progress object changes internally, so the UI recomposes */
    val revision: Int = 0
)

/**
 * Owns the active cleanup walk. Counters are saved to the database as they change, so a walk
 * survives the app being closed; the route is re-planned from the remaining time when restored.
 */
class WalkManager(
    private val repository: CleanupRepository,
    private val clock: () -> Instant = Instant::now
) {

    private val planner = CleanupRoutePlanner()
    private val lock = Mutex()
    private var distanceTracker: WalkDistanceTracker? = null

    private val _state = MutableStateFlow<WalkState?>(null)
    val state: StateFlow<WalkState?> = _state.asStateFlow()

    /**
     * Load a walk that was in progress (ex. after the app was closed)
     * @return true if there is an active walk
     */
    suspend fun restore(location: GeoPoint?): Boolean = lock.withLock {
        val current = _state.value
        if (current != null) {
            if (current.progress == null && location != null) {
                _state.value = current.copy(
                    progress = CleanupWalkProgress(plan(location, current.session.remaining())),
                    location = location,
                    revision = current.revision + 1
                )
            }
            return@withLock true
        }

        val active = repository.getActiveWalk() ?: return@withLock false
        distanceTracker = WalkDistanceTracker(initialDistanceMeters = active.distanceMeters)
        _state.value = WalkState(
            active,
            location?.let { CleanupWalkProgress(plan(it, active.remaining())) },
            location
        )
        true
    }

    suspend fun start(location: GeoPoint, duration: Duration) = lock.withLock {
        val session = CleanupWalkSession(0, clock(), null, duration)
        val saved = session.copy(id = repository.saveWalk(session))
        distanceTracker = WalkDistanceTracker()
        _state.value = WalkState(saved, CleanupWalkProgress(plan(location, duration)), location)
    }

    suspend fun onLocation(location: GeoPoint, accuracyMeters: Float?) = lock.withLock {
        val current = _state.value ?: return@withLock
        val tracker = distanceTracker ?: return@withLock
        val before = tracker.distanceMeters
        val distance = tracker.update(location, accuracyMeters, clock())
        val session = current.session.copy(distanceMeters = distance)

        // Save every ~50 m so a closed app loses little
        if ((distance / SAVE_EVERY_METERS).toInt() != (before / SAVE_EVERY_METERS).toInt()) {
            repository.saveWalk(session)
        }

        val arrived = current.progress?.update(location)
        _state.value = current.copy(
            session = session,
            location = location,
            arrivedAt = arrived,
            revision = current.revision + 1
        )
    }

    suspend fun addLitter(count: Int = 1) = updateSession {
        it.copy(litterRemoved = it.litterRemoved + count)
    }

    /**
     * Log something found, during a walk or on its own
     * @param cleanedUp true if the walker fixed it on the spot
     */
    suspend fun logIssue(
        type: CleanupIssueType,
        location: GeoPoint,
        count: Int,
        confidence: Float?,
        cleanedUp: Boolean
    ) {
        val now = clock()
        repository.saveIssue(
            CleanupIssue(0, type, location, now, count, if (cleanedUp) now else null, confidence)
        )
        updateSession {
            it.copy(
                litterRemoved = it.litterRemoved + if (cleanedUp && type == CleanupIssueType.Litter) count else 0,
                issuesReported = it.issuesReported + if (cleanedUp) 0 else 1,
                issuesResolved = it.issuesResolved + if (cleanedUp) 1 else 0
            )
        }
    }

    /**
     * Complete the next stop. A known issue at the stop is marked resolved.
     */
    suspend fun completeNextStop(litterPickedUp: Int = 0) {
        val stop = _state.value?.progress?.nextStop
        _state.value?.progress?.completeNext()
        val issue = stop?.issue
        if (issue != null) {
            repository.saveIssue(issue.copy(resolvedOn = clock()))
        }
        updateSession {
            it.copy(
                litterRemoved = it.litterRemoved + litterPickedUp,
                issuesResolved = it.issuesResolved + if (issue != null) 1 else 0
            )
        }
    }

    suspend fun skipNextStop() = lock.withLock {
        val current = _state.value ?: return@withLock
        current.progress?.completeNext()
        _state.value = current.copy(arrivedAt = null, revision = current.revision + 1)
    }

    suspend fun resolveIssue(issue: CleanupIssue) {
        repository.saveIssue(issue.copy(resolvedOn = clock()))
    }

    /**
     * @return the finished walk
     */
    suspend fun finish(): CleanupWalkSession? = lock.withLock {
        val current = _state.value ?: return@withLock null
        val finished = current.session.copy(
            endTime = clock(),
            distanceMeters = distanceTracker?.distanceMeters ?: current.session.distanceMeters
        )
        repository.saveWalk(finished)
        repository.clean()
        _state.value = null
        distanceTracker = null
        finished
    }

    private suspend fun updateSession(change: (CleanupWalkSession) -> CleanupWalkSession) = lock.withLock {
        val current = _state.value ?: return@withLock
        val updated = change(current.session)
        repository.saveWalk(updated)
        _state.value = current.copy(session = updated, arrivedAt = null, revision = current.revision + 1)
    }

    private suspend fun plan(location: GeoPoint, duration: Duration) = planner.plan(
        location,
        duration,
        repository.getOpenIssues(),
        explorationHeading = Random.nextDouble(0.0, 360.0)
    )

    companion object {
        private const val SAVE_EVERY_METERS = 50.0
    }
}
