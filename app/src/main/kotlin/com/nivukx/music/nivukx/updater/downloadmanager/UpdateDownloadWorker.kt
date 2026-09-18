package com.nivukx.music.nivukx.updater.downloadmanager

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nivukx.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.zip.ZipInputStream
class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "unknown"
        val fileSize = inputData.getString("file_size") ?: ""

        try {
            val startingNotification = DownloadNotificationManager.getDownloadStartingNotification(version, fileSize)
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    DownloadNotificationManager.NOTIFICATION_ID, 
                    startingNotification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, startingNotification)
            }
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            
        }

        try {
            val url = URL(apkUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    context.getString(R.string.server_error, connection.responseCode)
                )
                if (connection.responseCode >= 500) {
                    return@withContext Result.retry()
                }
                return@withContext Result.failure()
            }

            val fileLength = connection.contentLengthLong
            val externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return@withContext Result.failure(workDataOf("error" to "External download storage unavailable"))
            val downloadDir = File(externalDownloads, "echo_updates")
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                connection.disconnect()
                return@withContext Result.failure(workDataOf("error" to "Unable to create update directory"))
            }

            val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip", ignoreCase = true)
            val downloadFile = File(downloadDir, if (isZip) "nivukx_temp.zip.part" else "nivukx.apk.part")
            val completedDownload = File(downloadDir, if (isZip) "nivukx_temp.zip" else "nivukx.apk")

            // Never expose a partially downloaded APK as the installable artifact.
            downloadFile.delete()
            completedDownload.delete()

            connection.inputStream.use { inputStream ->
                FileOutputStream(downloadFile).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytesRead = 0L
                    var lastProgress = -1
                    var lastNotificationTime = 0L

                    while (true) {
                        if (isStopped) {
                            downloadFile.delete()
                            connection.disconnect()
                            return@withContext Result.retry()
                        }

                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (fileLength > 0) {
                            val progress = ((totalBytesRead * 100L) / fileLength).toInt().coerceIn(0, 100)
                            val now = System.currentTimeMillis()
                            if (progress > lastProgress && now - lastNotificationTime >= 1000L) {
                                lastProgress = progress
                                lastNotificationTime = now
                                val progressNotification = DownloadNotificationManager
                                    .getDownloadProgressNotification(progress, version)
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                                    as android.app.NotificationManager
                                notificationManager.notify(
                                    DownloadNotificationManager.NOTIFICATION_ID,
                                    progressNotification
                                )
                                setProgress(workDataOf("progress" to progress / 100f))
                            }
                        }
                    }
                    outputStream.fd.sync()
                }
            }
            connection.disconnect()

            if (!downloadFile.renameTo(completedDownload)) {
                downloadFile.delete()
                return@withContext Result.failure(workDataOf("error" to "Unable to finalize update download"))
            }

            val finalFile = if (isZip) {
                val extractedPart = File(downloadDir, "nivukx.apk.part")
                val targetApkFile = File(downloadDir, "nivukx.apk")
                extractedPart.delete()
                targetApkFile.delete()
                var extracted = false
                try {
                    ZipInputStream(completedDownload.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                FileOutputStream(extractedPart).use { fos ->
                                    zis.copyTo(fos)
                                    fos.fd.sync()
                                }
                                extracted = true
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    extractedPart.delete()
                    completedDownload.delete()
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        e.message ?: "Failed to extract zip file"
                    )
                    return@withContext Result.failure()
                } finally {
                    completedDownload.delete()
                }
                if (!extracted || !extractedPart.renameTo(targetApkFile)) {
                    extractedPart.delete()
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        "Could not finalize APK from zip"
                    )
                    return@withContext Result.failure()
                }
                targetApkFile
            } else {
                // For direct APK downloads, completedDownload is already the final path.
                completedDownload
            }

            if (version.startsWith("nightly-r")) {
                val runNumberString = version.removePrefix("nightly-r")
                val runNumber = runNumberString.toIntOrNull()
                if (runNumber != null) {
                    val sharedPreferences = context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
                }
            }

            DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)

            Result.success(workDataOf("file_path" to finalFile.absolutePath))
        } catch (e: IOException) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            return@withContext Result.retry()
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            Result.failure()
        }
    }
}
