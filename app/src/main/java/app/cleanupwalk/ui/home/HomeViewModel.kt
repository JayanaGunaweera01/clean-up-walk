package app.cleanupwalk.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cleanupwalk.CleanupWalkApp
import app.cleanupwalk.domain.CleanupIssue
import app.cleanupwalk.domain.CleanupWalkSession
import app.cleanupwalk.domain.CleanupWalkTotals
import app.cleanupwalk.domain.GeoPoint
import app.cleanupwalk.ui.scan.Finding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration

data class HomeUiState(
    val openIssues: List<CleanupIssue> = emptyList(),
    val totals: CleanupWalkTotals = CleanupWalkTotals.from(emptyList()),
    val hasActiveWalk: Boolean = false,
    val hasModel: Boolean = false,
    val hasImportedModel: Boolean = false
)

sealed interface HomeEvent {
    data object WalkStarted : HomeEvent
    data object NoLocation : HomeEvent
    data object IssueSaved : HomeEvent
    data class ModelImported(val success: Boolean) : HomeEvent
}

class HomeViewModel(private val app: CleanupWalkApp) : ViewModel() {

    private val modelState = MutableStateFlow(false to false)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    val state: StateFlow<HomeUiState> = combine(
        app.repository.issues,
        app.repository.walks,
        modelState
    ) { issues: List<CleanupIssue>, walks: List<CleanupWalkSession>, model: Pair<Boolean, Boolean> ->
        HomeUiState(
            openIssues = issues.filter { !it.isResolved },
            totals = CleanupWalkTotals.from(walks),
            hasActiveWalk = walks.any { it.isActive },
            hasModel = model.first,
            hasImportedModel = model.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshModel()
    }

    fun consumeEvent() {
        _events.value = null
    }

    fun startWalk(minutes: Long) {
        viewModelScope.launch {
            _isBusy.value = true
            val fix = app.locationTracker.current()
            _isBusy.value = false
            if (fix == null || fix.location.isZero) {
                _events.value = HomeEvent.NoLocation
                return@launch
            }
            app.walkManager.start(fix.location, Duration.ofMinutes(minutes))
            _events.value = HomeEvent.WalkStarted
        }
    }

    fun saveFinding(finding: Finding) {
        viewModelScope.launch {
            _isBusy.value = true
            val fix = app.locationTracker.current()
            _isBusy.value = false
            val location: GeoPoint = fix?.location?.takeIf { !it.isZero } ?: run {
                _events.value = HomeEvent.NoLocation
                return@launch
            }
            app.walkManager.logIssue(finding.type, location, finding.count, finding.confidence, finding.cleanedUp)
            _events.value = HomeEvent.IssueSaved
        }
    }

    fun resolve(issue: CleanupIssue) {
        viewModelScope.launch { app.walkManager.resolveIssue(issue) }
    }

    fun delete(issue: CleanupIssue) {
        viewModelScope.launch { app.repository.deleteIssue(issue) }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val success = app.models.import(uri)
            refreshModel()
            _events.value = HomeEvent.ModelImported(success)
        }
    }

    fun removeModel() {
        viewModelScope.launch {
            app.models.removeImported()
            refreshModel()
        }
    }

    private fun refreshModel() {
        viewModelScope.launch {
            modelState.value = app.models.hasModel() to app.models.hasImportedModel
        }
    }
}
