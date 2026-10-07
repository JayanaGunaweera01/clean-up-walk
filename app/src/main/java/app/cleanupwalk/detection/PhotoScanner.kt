package app.cleanupwalk.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import app.cleanupwalk.domain.CleanupScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Prepares a temporary file for the camera app and has Gemma analyze the photo it captures.
 * Photos are deleted as soon as they are analyzed.
 */
class PhotoScanner(context: Context, private val gemma: GemmaIssueScanner) {

    private val appContext = context.applicationContext

    data class Capture(val file: File, val uri: Uri)

    fun newCapture(): Capture {
        val directory = File(appContext.cacheDir, "scans").apply { mkdirs() }
        val file = File(directory, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        return Capture(file, uri)
    }

    suspend fun analyze(capture: Capture): CleanupScanResult = withContext(Dispatchers.Default) {
        try {
            val image = loadImage(capture.file) ?: return@withContext CleanupScanResult.empty
            val jpeg = try {
                ByteArrayOutputStream().use { out ->
                    image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    out.toByteArray()
                }
            } finally {
                image.recycle()
            }
            gemma.scan(jpeg)
        } catch (e: Exception) {
            CleanupScanResult.empty
        } finally {
            discard(capture)
        }
    }

    fun discard(capture: Capture) {
        capture.file.delete()
    }

    private fun loadImage(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        // Decode near Gemma's vision input size to save memory and time
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_SIZE && bounds.outHeight / (sampleSize * 2) >= MAX_SIZE) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null

        val rotation = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (rotation == 0f) {
            return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true
        )
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    companion object {
        private const val MAX_SIZE = 768
        private const val JPEG_QUALITY = 90
    }
}
