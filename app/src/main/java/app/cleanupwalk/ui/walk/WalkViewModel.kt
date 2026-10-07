package app.cleanupwalk.ui.walk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cleanupwalk.CleanupWalkApp
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.CleanupWalkSession
import app.cleanupwalk.ui.scan.Finding
import app.cleanupwalk.walk.WalkState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WalkViewModel(private val app: CleanupWalkApp) : ViewModel() {

    val state: StateFlow<WalkState?> = app.walkManager.state

    private val _finished = MutableStateFlow<CleanupWalkSession?>(null)
    val finished: StateFlow<CleanupWalkSession?> = _finished.asStateFlow()

    private val _noWalk = MutableStateFlow(false)
    val noWalk: StateFlow<Boolean> = _noWalk.asStateFlow()

    private var tracking: Job? = null

    init {
        viewModelScope.launch {
            if (!app.walkManager.restore(null)) {
                _noWalk.value = true
            }
        }
    }

    /**
     * Track location while the walk screen is visible
     */
    fun startTracking() {
        if (tracking?.isActive == true) {
            return
        }
        tracking = viewModelScope.launch {
            app.locationTracker.updates().collect { fix ->
                // A restored walk needs a location before it can re-plan its route
                if (app.walkManager.state.value?.progress == null) {
                    app.walkManager.restore(fix.location)
                }
                app.walkManager.onLocation(fix.location, fix.accuracyMeters)
            }
        }
    }

    fun stopTracking() {
        tracking?.cancel()
        tracking = null
    }

    fun addLitter() {
        viewModelScope.launch { app.walkManager.addLitter() }
    }

    /**
     * @param litter pieces picked up at a litter stop
     */
    fun completeStop(litter: Int) {
        viewModelScope.launch { app.walkManager.completeNextStop(litter) }
    }

    fun skipStop() {
        viewModelScope.launch { app.walkManager.skipNextStop() }
    }

    fun saveFinding(finding: Finding) {
        viewModelScope.launch {
            val location = app.walkManager.state.value?.location
                ?: app.locationTracker.current()?.location
                ?: return@launch
            app.walkManager.logIssue(finding.type, location, finding.count, finding.confidence, finding.cleanedUp)
        }
    }

    fun finish() {
        viewModelScope.launch {
            stopTracking()
            _finished.value = app.walkManager.finish()
        }
    }

    fun nextStopIsLitter(): Boolean {
        return state.value?.progress?.nextStop?.issue?.type == CleanupIssueType.Litter
    }
}
