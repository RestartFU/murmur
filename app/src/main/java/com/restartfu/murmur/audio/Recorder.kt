package com.restartfu.murmur.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Captures 16 kHz mono float PCM on a background thread.
 *
 * [onLevel] receives an RMS level roughly every 50 ms and [onLimit] fires once the
 * recording reaches [MAX_SECONDS]; both are called on the recording thread.
 */
class Recorder(
    private val onLevel: (Float) -> Unit,
    private val onLimit: () -> Unit,
) {
    class Recording(val samples: FloatArray, val peak: Float) {
        val seconds: Float get() = samples.size.toFloat() / SAMPLE_RATE
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var samples = FloatArray(SAMPLE_RATE * 10)
    private var size = 0
    private var peak = 0f

    @SuppressLint("MissingPermission") // Checked by the caller.
    fun start(): Boolean {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBytes <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING,
                max(minBytes, SAMPLE_RATE * 4 / 2), // at least 0.5 s of floats
            )
        } catch (e: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return false
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return false
        }

        size = 0
        peak = 0f
        running = true
        worker = thread(name = "murmur-recorder") {
            val chunk = FloatArray(SAMPLE_RATE / 20)
            try {
                while (running) {
                    val n = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) break
                    if (n == 0) continue
                    append(chunk, n)
                    var sumSquares = 0.0
                    for (i in 0 until n) {
                        val v = chunk[i]
                        sumSquares += v * v
                        if (abs(v) > peak) peak = abs(v)
                    }
                    onLevel(sqrt(sumSquares / n).toFloat())
                    if (size >= SAMPLE_RATE * MAX_SECONDS) {
                        onLimit()
                        break
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
        return true
    }

    fun stop(): Recording {
        running = false
        worker?.join(2_000)
        worker = null
        return Recording(samples.copyOf(size), peak)
    }

    private fun append(chunk: FloatArray, n: Int) {
        if (size + n > samples.size) samples = samples.copyOf(max(samples.size * 2, size + n))
        System.arraycopy(chunk, 0, samples, size, n)
        size += n
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        /** Parakeet uses full attention, so very long clips cost a lot of memory on a phone. */
        const val MAX_SECONDS = 120
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
