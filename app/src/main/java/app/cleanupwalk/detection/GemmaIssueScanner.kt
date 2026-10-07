package app.cleanupwalk.detection

import android.util.Log
import app.cleanupwalk.domain.CleanupScanResult
import app.cleanupwalk.domain.GemmaScan
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Asks Gemma 3n, running fully on the phone with LiteRT-LM, what cleanup problems a photo shows.
 *
 * Loading the model takes a few seconds, so the engine is loaded on the first scan and kept for
 * the rest of the session. Each scan uses a fresh conversation so photos don't influence each other.
 */
class GemmaIssueScanner(
    private val models: GemmaModelStore,
    private val cacheDir: File
) {

    private val lock = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    val isLoaded: Boolean
        get() = engine != null

    /**
     * @param jpeg the photo, JPEG encoded
     * @return what Gemma found, or [CleanupScanResult.empty] if there is no model or it failed
     */
    suspend fun scan(jpeg: ByteArray): CleanupScanResult = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = loadEngine() ?: return@withLock CleanupScanResult.empty
            try {
                val config = ConversationConfig(
                    systemInstruction = Contents.of(GemmaScan.systemInstruction),
                    samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1)
                )
                val reply = engine.createConversation(config).use { conversation ->
                    conversation.sendMessage(
                        Contents.of(Content.ImageBytes(jpeg), Content.Text(GemmaScan.USER_PROMPT))
                    ).toString()
                }
                Log.d(TAG, "Gemma replied: $reply")
                GemmaScan.parse(reply)
            } catch (e: Throwable) {
                // Includes native errors, e.g. on an emulator without the ARM libraries
                Log.e(TAG, "Gemma scan failed", e)
                CleanupScanResult.empty
            }
        }
    }

    fun close() {
        engine?.close()
        engine = null
        loadedPath = null
    }

    private fun loadEngine(): Engine? {
        val model = models.modelFile() ?: return null
        engine?.let { if (loadedPath == model.path) return it else close() }

        // The vision encoder runs fastest on the GPU; fall back to the CPU on phones without OpenCL
        for (visionBackend in listOf<Backend>(Backend.GPU(), Backend.CPU())) {
            val candidate = try {
                Engine(
                    EngineConfig(
                        modelPath = model.path,
                        backend = Backend.CPU(),
                        visionBackend = visionBackend,
                        cacheDir = cacheDir.path
                    )
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create the Gemma engine", e)
                return null
            }
            try {
                candidate.initialize()
                engine = candidate
                loadedPath = model.path
                return candidate
            } catch (e: Throwable) {
                Log.w(TAG, "Could not load Gemma with ${visionBackend.name} vision", e)
                runCatching { candidate.close() }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "GemmaIssueScanner"
    }
}
