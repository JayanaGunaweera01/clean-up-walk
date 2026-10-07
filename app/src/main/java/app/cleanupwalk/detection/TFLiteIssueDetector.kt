package app.cleanupwalk.detection

import android.graphics.Bitmap
import app.cleanupwalk.domain.CleanupDetection
import app.cleanupwalk.domain.CleanupIssueType
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runs an on-device object detection model in the standard TFLite post-processed SSD format
 * (MediaPipe Model Maker, TensorFlow Object Detection API, EfficientDet-Lite exports):
 *
 * - Input: [1, height, width, 3] UINT8 (0-255) or FLOAT32 (normalized to [-1, 1])
 * - Outputs (any order): boxes [1, N, 4] as (top, left, bottom, right), classes [1, N],
 *   scores [1, N], count [1]
 *
 * Class indices map to [CleanupIssueType.modelClassIndex]: 0 litter, 1 overflowing bin,
 * 2 damaged infrastructure, 3 fallen branch. Other classes are ignored.
 *
 * Everything runs on the phone - photos never leave the device.
 */
class TFLiteIssueDetector(model: ByteBuffer) : IssueDetector {

    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    private val inputShape = interpreter.getInputTensor(0).shape()
    private val inputType = interpreter.getInputTensor(0).dataType()
    private val inputHeight = inputShape[1]
    private val inputWidth = inputShape[2]

    @Synchronized
    override fun detect(image: Bitmap): List<CleanupDetection> {
        val scaled = Bitmap.createScaledBitmap(image, inputWidth, inputHeight, true)
        val input = toInput(scaled)
        if (scaled !== image) {
            scaled.recycle()
        }

        val outputs = mutableMapOf<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            outputs[i] = when (shape.size) {
                3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
                2 -> Array(shape[0]) { FloatArray(shape[1]) }
                else -> FloatArray(shape.fold(1) { acc, value -> acc * value })
            }
        }
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)
        return parse(outputs.values.toList())
    }

    private fun parse(outputs: List<Any>): List<CleanupDetection> {
        @Suppress("UNCHECKED_CAST")
        val boxes = outputs.firstOrNull { it is Array<*> && it.firstOrNull() is Array<*> }
                as? Array<Array<FloatArray>> ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val vectors = outputs.filter { it is Array<*> && it.firstOrNull() is FloatArray }
            .map { (it as Array<FloatArray>)[0] }
        val count = (outputs.firstOrNull { it is FloatArray } as? FloatArray)?.firstOrNull()?.roundToInt()

        if (vectors.size < 2) {
            return emptyList()
        }

        // Classes are whole numbers and scores are probabilities, so tell them apart by value
        val classes = vectors.maxByOrNull { vector -> vector.count { abs(it - it.roundToInt()) < 1e-4f } }
            ?: return emptyList()
        val scores = vectors.first { it !== classes }

        val n = minOf(count ?: scores.size, scores.size, classes.size, boxes[0].size)
        return (0 until n).mapNotNull { i ->
            val type = CleanupIssueType.fromModelClass(classes[i].roundToInt()) ?: return@mapNotNull null
            val box = boxes[0][i]
            CleanupDetection(
                type,
                scores[i].coerceIn(0f, 1f),
                // (top, left, bottom, right) -> (left, top, right, bottom)
                listOf(box[1], box[0], box[3], box[2]).map { it.coerceIn(0f, 1f) }
            )
        }
    }

    private fun toInput(bitmap: Bitmap): ByteBuffer {
        val isFloat = inputType == DataType.FLOAT32
        val bytesPerChannel = if (isFloat) 4 else 1
        val buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (isFloat) {
                buffer.putFloat((r - 127.5f) / 127.5f)
                buffer.putFloat((g - 127.5f) / 127.5f)
                buffer.putFloat((b - 127.5f) / 127.5f)
            } else {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()
        return buffer
    }

    override fun close() {
        interpreter.close()
    }
}
