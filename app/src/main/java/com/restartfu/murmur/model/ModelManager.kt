package com.restartfu.murmur.model

import android.app.DownloadManager
import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Downloads, verifies and unpacks the speech model.
 *
 * The model is Moondream's Parakeet Ultra (a post-trained NVIDIA parakeet-tdt-0.6b-v3,
 * 25 European languages) exported to int8 ONNX by the sherpa-onnx project.
 *
 * The archive is fetched with the system [DownloadManager], so it survives the app being
 * closed, then hashed and extracted in a single pass. Only the four files the recogniser
 * needs are kept.
 */
object ModelManager {
    const val DISPLAY_NAME = "Parakeet TDT 0.6B Ultra (int8)"
    const val ARCHIVE_SIZE = 487_134_290L
    private const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-nemo-parakeet-tdt-0.6b-ultra-int8.tar.bz2"
    private const val ARCHIVE_SHA256 =
        "d216b71d7f628b3f2f2a20ee7ead39ff0b075984959eb4bf6ddf8dc8da737c09"
    private const val MODEL_DIR = "parakeet-tdt-0.6b-ultra-int8"
    private const val ARCHIVE_FILE = "parakeet-ultra-int8.tar.bz2"
    private const val COMPLETE_MARKER = ".complete"

    const val ENCODER = "encoder.int8.onnx"
    const val DECODER = "decoder.int8.onnx"
    const val JOINER = "joiner.int8.onnx"
    const val TOKENS = "tokens.txt"
    private val REQUIRED = setOf(ENCODER, DECODER, JOINER, TOKENS)

    private const val PREFS = "model"
    private const val KEY_DOWNLOAD_ID = "download_id"

    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val downloaded: Long, val total: Long) : State
        data class Installing(val progress: Float) : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.NotInstalled)
    val state: StateFlow<State> = _state.asStateFlow()
    private var worker: Job? = null

    fun modelDir(context: Context) = File(context.noBackupFilesDir, "models/$MODEL_DIR")

    fun isInstalled(context: Context) = File(modelDir(context), COMPLETE_MARKER).exists()

    private fun archiveFile(context: Context) = File(context.getExternalFilesDir(null), ARCHIVE_FILE)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Re-derives the state, resuming progress tracking for a download started earlier. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (worker?.isActive == true) return
        when {
            isInstalled(app) -> _state.value = State.Installed
            prefs(app).getLong(KEY_DOWNLOAD_ID, -1L) != -1L -> worker = scope.launch { track(app) }
            _state.value !is State.Failed -> _state.value = State.NotInstalled
        }
    }

    fun download(context: Context) {
        val app = context.applicationContext
        if (worker?.isActive == true || isInstalled(app)) return
        val dm = app.getSystemService(DownloadManager::class.java)
        archiveFile(app).delete()
        val request = DownloadManager.Request(ARCHIVE_URL.toUri())
            .setTitle("Murmur speech model")
            .setDescription(DISPLAY_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, null, ARCHIVE_FILE)
            .setAllowedOverMetered(true)
        val id = dm.enqueue(request)
        prefs(app).edit { putLong(KEY_DOWNLOAD_ID, id) }
        _state.value = State.Downloading(0, ARCHIVE_SIZE)
        worker = scope.launch { track(app) }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        worker?.cancel()
        clearDownload(app)
        archiveFile(app).delete()
        _state.value = if (isInstalled(app)) State.Installed else State.NotInstalled
    }

    fun delete(context: Context) {
        val app = context.applicationContext
        cancel(app)
        modelDir(app).deleteRecursively()
        _state.value = State.NotInstalled
    }

    private fun clearDownload(context: Context) {
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (id != -1L) context.getSystemService(DownloadManager::class.java).remove(id)
        prefs(context).edit { remove(KEY_DOWNLOAD_ID) }
    }

    private suspend fun track(context: Context) {
        val dm = context.getSystemService(DownloadManager::class.java)
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        while (scope.isActive) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            val row = cursor.use {
                if (!it.moveToFirst()) null
                else Triple(
                    it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            }
            if (row == null) {
                prefs(context).edit { remove(KEY_DOWNLOAD_ID) }
                _state.value = State.Failed("The download was cancelled.")
                return
            }
            val (status, done, total) = row
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // Forget the download only afterwards, so an install interrupted by the
                    // process dying is retried on the next launch.
                    install(context)
                    clearDownload(context)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    clearDownload(context)
                    _state.value = State.Failed("The download failed. Check your connection and try again.")
                    return
                }
                else -> _state.value = State.Downloading(done, if (total > 0) total else ARCHIVE_SIZE)
            }
            delay(500)
        }
    }

    private fun install(context: Context) {
        val archive = archiveFile(context)
        val target = modelDir(context)
        val staging = File(target.parentFile, "$MODEL_DIR.staging")
        _state.value = State.Installing(0f)
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            val total = archive.length().coerceAtLeast(1)
            val hashing = HashingInputStream(archive.inputStream())
            val bz = BZip2CompressorInputStream(BufferedInputStream(hashing, 1 shl 16), true)
            val found = mutableSetOf<String>()
            TarArchiveInputStream(bz).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name in REQUIRED) {
                        File(staging, name).outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = tar.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                _state.value = State.Installing(hashing.count.toFloat() / total)
                            }
                        }
                        found += name
                    }
                    _state.value = State.Installing(hashing.count.toFloat() / total)
                }
                // Hash any trailing bytes the tar reader did not need.
                bz.skip(Long.MAX_VALUE)
                hashing.skip(Long.MAX_VALUE)
            }
            val actual = hashing.hexDigest()
            if (actual != ARCHIVE_SHA256) throw IOException("The downloaded file is corrupt (checksum mismatch).")
            val missing = REQUIRED - found
            if (missing.isNotEmpty()) throw IOException("The archive is missing ${missing.joinToString()}.")

            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("Could not move the model into place.")
            File(target, COMPLETE_MARKER).createNewFile()
            archive.delete()
            _state.value = State.Installed
        } catch (e: Exception) {
            staging.deleteRecursively()
            archive.delete()
            _state.value = State.Failed(e.message ?: "Installing the model failed.")
        }
    }

    /** Counts and hashes bytes as they are read, so verification needs no second pass. */
    private class HashingInputStream(input: InputStream) : FilterInputStream(input) {
        private val digest = MessageDigest.getInstance("SHA-256")
        @Volatile var count = 0L
            private set

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) { digest.update(b.toByte()); count++ }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { digest.update(b, off, n); count += n }
            return n
        }

        // FilterInputStream.skip would bypass the digest; read through instead.
        override fun skip(n: Long): Long {
            val buf = ByteArray(1 shl 16)
            var skipped = 0L
            while (skipped < n) {
                val r = read(buf, 0, minOf(buf.size.toLong(), n - skipped).toInt())
                if (r < 0) break
                skipped += r
            }
            return skipped
        }

        fun hexDigest(): String = digest.digest().joinToString("") { "%02x".format(it) }
    }
}
