package app.cleanupwalk.ui.walk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cleanupwalk.R
import app.cleanupwalk.detection.PhotoScanner
import app.cleanupwalk.ui.Formatters
import app.cleanupwalk.ui.scan.ChoiceDialog
import app.cleanupwalk.ui.scan.ScanFlow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkScreen(
    viewModel: WalkViewModel,
    scanner: PhotoScanner,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val formatters = remember { Formatters(context) }
    val walk by viewModel.state.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val noWalk by viewModel.noWalk.collectAsState()

    var now by remember { mutableStateOf(Instant.now()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var askLitterCount by remember { mutableStateOf(false) }

    // Keep the screen on and track location while this screen is visible
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        viewModel.startTracking()
        onDispose {
            view.keepScreenOn = false
            viewModel.stopTracking()
        }
    }

    // Tick the timer
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    LaunchedEffect(noWalk) {
        if (noWalk && finished == null) {
            onClose()
        }
    }

    val current = walk
    val remaining = current?.session?.remaining(now) ?: Duration.ZERO

    // Time's up and the loop is done - wrap up automatically
    LaunchedEffect(remaining.isZero, current?.progress?.isComplete) {
        if (current != null && remaining.isZero && current.progress?.isComplete == true) {
            viewModel.finish()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { padding ->
        ScanFlow(scanner = scanner, onFinding = { viewModel.saveFinding(it) }) { startScan ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(formatters.timeLeft(remaining), stringResource(R.string.time_left), Modifier.weight(1f))
                    Stat(
                        formatters.distance(current?.session?.distanceMeters ?: 0.0),
                        stringResource(R.string.walked),
                        Modifier.weight(1f)
                    )
                    Stat(
                        (current?.session?.litterRemoved ?: 0).toString(),
                        stringResource(R.string.litter_removed),
                        Modifier.weight(1f)
                    )
                }

                NextStopCard(current = current, formatters = formatters)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hasNext = current?.progress?.nextStop != null
                    OutlinedButton(
                        onClick = {
                            if (viewModel.nextStopIsLitter()) askLitterCount = true else viewModel.completeStop(0)
                        },
                        enabled = hasNext,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.done_here)) }
                    OutlinedButton(
                        onClick = { viewModel.skipStop() },
                        enabled = hasNext,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.skip)) }
                }

                Spacer(Modifier.weight(1f))

                Text(stringResource(R.string.safety_note), style = MaterialTheme.typography.bodySmall)

                Button(
                    onClick = { viewModel.addLitter() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                ) {
                    Text(stringResource(R.string.plus_one_litter), style = MaterialTheme.typography.headlineSmall)
                }

                OutlinedButton(onClick = startScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan_issue))
                }

                TextButton(
                    onClick = { confirmFinish = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.finish_walk)) }
            }
        }
    }

    if (askLitterCount) {
        val suggested = current?.progress?.nextStop?.issue?.count ?: 1
        val options = (listOf(suggested) + listOf(1, 2, 3, 5, 10, 20)).distinct()
        ChoiceDialog(
            title = stringResource(R.string.how_many),
            options = options.map { it.toString() },
            onDismiss = { askLitterCount = false }
        ) { index ->
            askLitterCount = false
            viewModel.completeStop(options[index])
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_walk_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish()
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    finished?.let { session ->
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.walk_complete)) },
            text = { Text(formatters.result(session), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NextStopCard(current: app.cleanupwalk.walk.WalkState?, formatters: Formatters) {
    val progress = current?.progress
    val location = current?.location

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.next_stop), style = MaterialTheme.typography.labelLarge)

            if (progress == null) {
                Text(stringResource(R.string.waiting_for_gps), style = MaterialTheme.typography.headlineSmall)
                return@Column
            }

            val next = progress.nextStop
            val issue = next?.issue
            Text(
                when {
                    next == null -> stringResource(R.string.head_back)
                    issue != null -> formatters.issueType(issue.type)
                    else -> stringResource(R.string.exploration_stop)
                },
                style = MaterialTheme.typography.headlineSmall
            )

            when {
                location == null -> Text(stringResource(R.string.waiting_for_gps))
                current?.arrivedAt != null -> Text(stringResource(R.string.arrived))
                else -> {
                    // Rotating arrow points along the bearing relative to north
                    val bearing = progress.bearingToNext(location)
                    val distance = progress.distanceToNext(location) ?: progress.distanceToStart(location)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.rotate(bearing.toFloat())
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                R.string.distance_direction,
                                formatters.distance(distance),
                                formatters.direction(bearing)
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            val route = progress.route
            val issues = route.issueStops.size
            Spacer(Modifier.height(8.dp))
            Text(
                if (issues == 0) {
                    stringResource(R.string.route_summary_exploring, formatters.distance(route.distanceMeters))
                } else {
                    stringResource(R.string.route_summary, issues, formatters.distance(route.distanceMeters))
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
