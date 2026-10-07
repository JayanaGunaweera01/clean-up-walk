package app.cleanupwalk.detection

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Where the detection model comes from: one the user imported wins over one bundled in the APK
 * (assets/models/issue_detector.tflite). With neither, the app asks the walker what they found.
 */
class DetectionModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val importedFile = File(appContext.filesDir, "models/$MODEL_FILENAME")

    val hasImportedModel: Boolean
        get() = importedFile.exists()

    suspend fun hasModel(): Boolean = withContext(Dispatchers.IO) {
        importedFile.exists() || hasBundledModel()
    }

    /**
     * @return a ready detector, or null if no usable model is available
     */
    suspend fun createDetector(): IssueDetector? = withContext(Dispatchers.IO) {
        val bytes = readImported() ?: readBundled() ?: return@withContext null
        runCatching {
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            TFLiteIssueDetector(buffer)
        }.getOrNull()
    }

    /**
     * Copy a .tflite model picked by the user into app storage
     * @return true if the model loads
     */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val temp = File(appContext.cacheDir, "import_$MODEL_FILENAME")
        val copied = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            temp.delete()
            return@withContext false
        }

        // Make sure it actually loads before replacing the current model
        val isValid = runCatching {
            val bytes = temp.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            TFLiteIssueDetector(buffer).close()
            true
        }.getOrDefault(false)

        if (isValid) {
            importedFile.parentFile?.mkdirs()
            temp.copyTo(importedFile, overwrite = true)
        }
        temp.delete()
        isValid
    }

    suspend fun removeImported() = withContext(Dispatchers.IO) {
        importedFile.delete()
    }

    private fun readImported(): ByteArray? {
        return if (importedFile.exists()) runCatching { importedFile.readBytes() }.getOrNull() else null
    }

    private fun readBundled(): ByteArray? {
        return runCatching {
            appContext.assets.open("models/$MODEL_FILENAME").use { it.readBytes() }
        }.getOrNull()
    }

    private fun hasBundledModel(): Boolean {
        return runCatching { appContext.assets.list("models")?.contains(MODEL_FILENAME) == true }
            .getOrDefault(false)
    }

    companion object {
        const val MODEL_FILENAME = "issue_detector.tflite"
    }
}
