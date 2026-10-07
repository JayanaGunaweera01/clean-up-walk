package app.cleanupwalk.ui.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanupwalk.R
import app.cleanupwalk.detection.PhotoScanner
import app.cleanupwalk.domain.CleanupIssue
import app.cleanupwalk.ui.Formatters
import app.cleanupwalk.ui.scan.ChoiceDialog
import app.cleanupwalk.ui.scan.ScanFlow
import java.text.DateFormat
import java.util.Date

private val DURATIONS = listOf(15L, 30L, 45L, 60L)

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    scanner: PhotoScanner,
    onOpenWalk: () -> Unit
) {
    val context = LocalContext.current
    val formatters = remember { Formatters(context) }
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val event by viewModel.events.collectAsState()

    var showDurations by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val action = pendingAfterPermission
        pendingAfterPermission = null
        if (granted.values.any { it }) {
            action?.invoke()
        } else {
            Toast.makeText(context, R.string.location_permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    fun withLocation(action: () -> Unit) {
        pendingAfterPermission = action
        permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    LaunchedEffect(event) {
        when (val current = event) {
            HomeEvent.WalkStarted -> onOpenWalk()
            HomeEvent.NoLocation -> Toast.makeText(context, R.string.no_location, Toast.LENGTH_LONG).show()
            HomeEvent.IssueSaved -> Toast.makeText(context, R.string.issue_saved, Toast.LENGTH_SHORT).show()
            is HomeEvent.ModelImported -> Toast.makeText(
                context,
                if (current.success) R.string.model_imported else R.string.model_invalid,
                Toast.LENGTH_LONG
            ).show()
            null -> Unit
        }
        if (event != null) {
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_model)) },
                                onClick = {
                                    showMenu = false
                                    modelPicker.launch(arrayOf("*/*"))
                                }
                            )
                            if (state.hasImportedModel) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove_model)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.removeModel()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        ScanFlow(scanner = scanner, onFinding = { viewModel.saveFinding(it) }) { startScan ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            val totals = state.totals
                            Text(
                                when (totals.walks) {
                                    0 -> stringResource(R.string.no_walks_yet)
                                    1 -> stringResource(R.string.totals_one, formatters.distance(totals.distanceMeters), totals.litterRemoved)
                                    else -> stringResource(
                                        R.string.totals,
                                        totals.walks,
                                        formatters.distance(totals.distanceMeters),
                                        totals.litterRemoved
                                    )
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (state.hasActiveWalk) onOpenWalk() else showDurations = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (state.hasActiveWalk) R.string.resume_walk else R.string.start_walk))
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { withLocation(startScan) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.scan_issue))
                    }
                    Text(
                        stringResource(if (state.hasModel) R.string.model_on else R.string.model_off),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!state.hasModel) {
                        Text(
                            stringResource(R.string.model_help),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                item {
                    Text(
                        stringResource(R.string.open_issues),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (state.openIssues.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_open_issues), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(state.openIssues, key = { it.id }) { issue ->
                        IssueRow(
                            issue = issue,
                            formatters = formatters,
                            onResolve = { viewModel.resolve(issue) },
                            onDelete = { viewModel.delete(issue) }
                        )
                    }
                }
            }
        }
    }

    if (showDurations) {
        ChoiceDialog(
            title = stringResource(R.string.choose_duration),
            options = DURATIONS.map { stringResource(R.string.duration_minutes, it.toInt()) },
            onDismiss = { showDurations = false }
        ) { index ->
            showDurations = false
            withLocation { viewModel.startWalk(DURATIONS[index]) }
        }
    }

    busy?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(message), Modifier.padding(start = 16.dp))
                }
            }
        )
    }
}

@Composable
private fun IssueRow(
    issue: CleanupIssue,
    formatters: Formatters,
    onResolve: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val name = formatters.issueType(issue.type)
    val time = remember(issue.reportedOn) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date.from(issue.reportedOn))
    }
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(if (issue.count > 1) stringResource(R.string.issue_count, name, issue.count) else name)
            },
            supportingContent = { Text(time) },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_resolved)) },
                            onClick = {
                                showMenu = false
                                onResolve()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        )
    }
}
