package com.restartfu.murmur.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.restartfu.murmur.audio.Recorder
import com.restartfu.murmur.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ModelMissingException : Exception("The speech model is not installed.")

/**
 * Process-wide holder for the Parakeet recogniser. Loading takes a few seconds and the
 * model occupies several hundred MB of RAM, so it is loaded on demand and released by
 * the caller when idle.
 */
object Transcriber {
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null

    val isLoaded: Boolean get() = recognizer != null

    suspend fun warmUp(context: Context) = withContext(Dispatchers.Default) {
        lock.withLock { ensureLoaded(context.applicationContext) }
    }

    /** Transcribes 16 kHz mono float samples. Parakeet detects the language itself. */
    suspend fun transcribe(context: Context, samples: FloatArray): String =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val recognizer = ensureLoaded(context.applicationContext)
                // A little trailing silence helps the decoder emit the final word.
                val padded = samples.copyOf(samples.size + Recorder.SAMPLE_RATE / 2)
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(padded, Recorder.SAMPLE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }
        }

    suspend fun unload() = withContext(Dispatchers.Default) {
        lock.withLock {
            recognizer?.release()
            recognizer = null
        }
    }

    private fun ensureLoaded(context: Context): OfflineRecognizer {
        recognizer?.let { return it }
        if (!ModelManager.isInstalled(context)) throw ModelMissingException()
        val dir = ModelManager.modelDir(context)
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(dir, ModelManager.ENCODER).path,
                    decoder = File(dir, ModelManager.DECODER).path,
                    joiner = File(dir, ModelManager.JOINER).path,
                ),
                tokens = File(dir, ModelManager.TOKENS).path,
                modelType = "nemo_transducer",
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            ),
        )
        return OfflineRecognizer(config = config).also { recognizer = it }
    }
}
