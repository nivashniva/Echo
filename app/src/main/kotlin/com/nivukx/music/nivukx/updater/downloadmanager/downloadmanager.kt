package com.nivukx.music.echomusic.updater.downloadmanager

import android.content.Context
import android.os.Environment
import com.nivukx.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CustomDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    @Volatile private var isPaused = false

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onDownloadComplete: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        downloadJob?.cancel()
        isPaused = false
        val appContext = context.applicationContext

        downloadJob = scope.launch {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(apkUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    useCaches = false
                }
                connection.connect()

                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $code")
                }

                val downloadRoot = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IllegalStateException("Download storage is unavailable")
                val downloadDir = File(downloadRoot, "echo_updates").apply {
                    if (!exists() && !mkdirs()) throw IllegalStateException("Cannot create update directory")
                }

                // Never write directly to the APK path. A killed/interrupted download must not
                // leave a corrupt APK that later code mistakes for a complete update.
                val tempFile = File(downloadDir, "echomusic.apk.part")
                val outputFile = File(downloadDir, "echomusic.apk")
                val totalLength = connection.contentLengthLong
                var totalRead = 0L
                var lastProgressNs = 0L
                var lastProgress = -1f

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (isActive && !isPaused) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            totalRead += read

                            if (totalLength > 0) {
                                val progress = (totalRead.toDouble() / totalLength).toFloat().coerceIn(0f, 1f)
                                val now = System.nanoTime()
                                if (progress >= 1f || progress - lastProgress >= 0.01f || now - lastProgressNs >= 200_000_000L) {
                                    lastProgress = progress
                                    lastProgressNs = now
                                    withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                                }
                            }
                        }
                    }
                }

                if (!isActive || isPaused) {
                    tempFile.delete()
                    return@launch
                }

                if (totalLength > 0 && totalRead != totalLength) {
                    throw IllegalStateException("Incomplete download: $totalRead/$totalLength bytes")
                }

                if (outputFile.exists() && !outputFile.delete()) {
                    throw IllegalStateException("Cannot replace previous APK")
                }
                if (!tempFile.renameTo(outputFile)) {
                    throw IllegalStateException("Cannot finalize downloaded APK")
                }

                withContext(Dispatchers.Main.immediate) {
                    onProgress(1f)
                    onDownloadComplete(outputFile)
                }
            } catch (e: CancellationException) {
                // Cancellation is an expected control path for pause/restart; do not show an
                // error snackbar for it.
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    onError(e.message ?: appContext.getString(R.string.download_failed))
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
        downloadJob = null
    }

    fun cancelDownload() {
        isPaused = true
        downloadJob?.cancel()
        downloadJob = null
    }
}
