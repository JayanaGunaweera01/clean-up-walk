package app.cleanupwalk.detection

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds the Gemma 3n model file (.litertlm) on the phone.
 *
 * The model is a gated open-weight download (you accept Google's Gemma license on Hugging Face),
 * so it isn't bundled. It can get onto the phone two ways:
 * 1. Import it from the app menu (copied into private app storage), or
 * 2. Copy it over USB into Android/data/app.cleanupwalk/files/models/
 */
class GemmaModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val importedFile = File(appContext.filesDir, "models/$MODEL_FILENAME")
    private val usbDirectory = appContext.getExternalFilesDir("models")

    /**
     * The model to load, or null if none is installed
     */
    fun modelFile(): File? {
        if (importedFile.isFile && importedFile.length() > 0) {
            return importedFile
        }
        return usbDirectory?.listFiles { file -> file.isFile && file.name.endsWith(".litertlm") }
            ?.maxByOrNull { it.length() }
    }

    val hasModel: Boolean
        get() = modelFile() != null

    val hasImportedModel: Boolean
        get() = importedFile.exists()

    /**
     * Copy a .litertlm file picked by the user into app storage. A few GB, so this takes a while.
     * @return true if the file was copied
     */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val name = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        if (name != null && !name.endsWith(".litertlm")) {
            return@withContext false
        }

        importedFile.parentFile?.mkdirs()
        val temp = File(importedFile.parentFile, "$MODEL_FILENAME.part")
        val copied = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            } != null
        }.getOrDefault(false)

        if (!copied || temp.length() < MIN_MODEL_BYTES) {
            temp.delete()
            return@withContext false
        }
        temp.renameTo(importedFile)
    }

    suspend fun removeImported() = withContext(Dispatchers.IO) {
        importedFile.delete()
    }

    companion object {
        const val MODEL_FILENAME = "gemma-3n.litertlm"
        private const val BUFFER_SIZE = 1 shl 20
        private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
