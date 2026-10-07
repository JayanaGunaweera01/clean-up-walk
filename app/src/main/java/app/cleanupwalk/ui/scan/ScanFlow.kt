package app.cleanupwalk.ui.scan

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanupwalk.R
import app.cleanupwalk.detection.PhotoScanner
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.CleanupScanResult
import app.cleanupwalk.ui.Formatters
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What the walker confirmed after a scan
 */
data class Finding(
    val type: CleanupIssueType,
    val count: Int,
    val confidence: Float?,
    val cleanedUp: Boolean
)

private sealed interface ScanStep {
    data object Idle : ScanStep
    data object Analyzing : ScanStep
    data class PickType(val result: CleanupScanResult) : ScanStep
    data class PickCount(val type: CleanupIssueType, val suggested: Int, val confidence: Float?) : ScanStep
    data class AskCleaned(val type: CleanupIssueType, val count: Int, val confidence: Float?) : ScanStep
}

/**
 * Photo -> on-device detection -> one or two taps to confirm.
 * @param content receives a function that starts a scan
 */
@Composable
fun ScanFlow(
    scanner: PhotoScanner,
    onFinding: (Finding) -> Unit,
    content: @Composable (startScan: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val formatters = remember { Formatters(context) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<ScanStep>(ScanStep.Idle) }
    var capture by remember { mutableStateOf<PhotoScanner.Capture?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val current = capture ?: return@rememberLauncherForActivityResult
        capture = null
        if (!success) {
            scanner.discard(current)
            step = ScanStep.Idle
            return@rememberLauncherForActivityResult
        }
        step = ScanStep.Analyzing
        scope.launch {
            step = ScanStep.PickType(scanner.analyze(current))
        }
    }

    fun next(type: CleanupIssueType, count: Int, confidence: Float?) {
        step = if (type.isRemovable) {
            ScanStep.AskCleaned(type, count, confidence)
        } else {
            onFinding(Finding(type, count, confidence, cleanedUp = false))
            ScanStep.Idle
        }
    }

    content {
        val newCapture = scanner.newCapture()
        capture = newCapture
        try {
            takePicture.launch(newCapture.uri)
        } catch (e: ActivityNotFoundException) {
            capture = null
            scanner.discard(newCapture)
            Toast.makeText(context, R.string.no_camera_app, Toast.LENGTH_SHORT).show()
        }
    }

    when (val current = step) {
        ScanStep.Idle -> Unit

        ScanStep.Analyzing -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.analyzing_photo), Modifier.padding(start = 16.dp))
                }
            }
        )

        is ScanStep.PickType -> {
            val result = current.result
            // The suggestion first, then anything else that was detected
            val types = CleanupIssueType.entries.sortedByDescending {
                if (it == result.primaryType) Int.MAX_VALUE else result.counts[it] ?: 0
            }
            val title = result.primaryType?.let {
                stringResource(
                    R.string.detected,
                    stringResource(R.string.issue_count, formatters.issueType(it), result.counts[it] ?: 1),
                    ((result.confidence ?: 0f) * 100).roundToInt()
                )
            } ?: stringResource(R.string.what_did_you_find)

            ChoiceDialog(
                title = title,
                options = types.map { formatters.issueType(it) },
                onDismiss = { step = ScanStep.Idle }
            ) { index ->
                val type = types[index]
                val detected = result.counts[type] ?: 1
                val confidence = if (type == result.primaryType) result.confidence else null
                if (type == CleanupIssueType.Litter) {
                    step = ScanStep.PickCount(type, detected, confidence)
                } else {
                    next(type, detected, confidence)
                }
            }
        }

        is ScanStep.PickCount -> {
            val options = (listOf(current.suggested) + listOf(1, 2, 3, 5, 10, 20)).distinct()
            ChoiceDialog(
                title = stringResource(R.string.how_many),
                options = options.map { it.toString() },
                onDismiss = { step = ScanStep.Idle }
            ) { index ->
                next(current.type, options[index], current.confidence)
            }
        }

        is ScanStep.AskCleaned -> AlertDialog(
            onDismissRequest = { step = ScanStep.Idle },
            title = { Text(stringResource(R.string.did_you_clean_it)) },
            confirmButton = {
                TextButton(onClick = {
                    onFinding(Finding(current.type, current.count, current.confidence, cleanedUp = true))
                    step = ScanStep.Idle
                }) { Text(stringResource(R.string.cleaned_it)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onFinding(Finding(current.type, current.count, current.confidence, cleanedUp = false))
                    step = ScanStep.Idle
                }) { Text(stringResource(R.string.report_only)) }
            }
        )
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { index, option ->
                    TextButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(option, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
