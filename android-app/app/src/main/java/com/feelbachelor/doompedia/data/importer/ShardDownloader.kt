package com.feelbachelor.doompedia.data.importer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgressSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

class ShardDownloader(
    private val context: Context,
) {
    private companion object {
        const val HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416
    }

    suspend fun download(
        sourceUrl: String,
        destination: File,
        wifiOnly: Boolean,
        resume: Boolean = true,
        onProgress: ((DownloadProgressSnapshot) -> Unit)? = null,
    ): File {
        return withContext(Dispatchers.IO) {
            if (wifiOnly && !isOnWifi()) {
                error("Wi-Fi only download is enabled")
            }

            destination.parentFile?.mkdirs()
            var lastError: Throwable? = null
            for (attempt in 0..1) {
                val allowResume = resume && attempt == 0
                try {
                    return@withContext downloadOnce(sourceUrl, destination, allowResume, onProgress)
                } catch (error: Throwable) {
                    lastError = error
                    // Retry once with a clean file if resume path failed.
                    destination.delete()
                }
            }
            throw lastError ?: IllegalStateException("Download failed for unknown reason")
        }
    }

    private fun downloadOnce(
        sourceUrl: String,
        destination: File,
        allowResume: Boolean,
        onProgress: ((DownloadProgressSnapshot) -> Unit)?,
    ): File {
        var existingBytes = if (allowResume && destination.exists()) destination.length() else 0L
        var connection = openConnection(sourceUrl, existingBytes)
        var responseCode = connection.responseCode

        // Resume edge-case: local file is larger or equal to remote -> restart clean.
        if (existingBytes > 0 && responseCode == HTTP_REQUESTED_RANGE_NOT_SATISFIABLE) {
            connection.disconnect()
            destination.delete()
            existingBytes = 0L
            connection = openConnection(sourceUrl, 0L)
            responseCode = connection.responseCode
        }

        if (responseCode !in 200..299 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            val details = readErrorBody(connection)
            connection.disconnect()
            error("Download failed ($responseCode) for $sourceUrl${if (details.isBlank()) "" else ": $details"}")
        }

        val append = existingBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
        val responseBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
        val totalBytes = when {
            append && responseBytes > 0L -> existingBytes + responseBytes
            !append && responseBytes > 0L -> responseBytes
            else -> -1L
        }

        val startedAtNanos = System.nanoTime()
        var lastEmitNanos = startedAtNanos
        var downloadedBytes = if (append) existingBytes else 0L

        onProgress?.invoke(
            DownloadProgressSnapshot(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                bytesPerSecond = 0L,
            )
        )

        connection.inputStream.use { input ->
            FileOutputStream(destination, append).buffered().use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    downloadedBytes += read.toLong()
                    val nowNanos = System.nanoTime()
                    val elapsedSinceLast = nowNanos - lastEmitNanos
                    if (elapsedSinceLast >= 200_000_000L) {
                        val elapsedSeconds = (nowNanos - startedAtNanos).coerceAtLeast(1L) / 1_000_000_000.0
                        val speed = (downloadedBytes / elapsedSeconds).toLong()
                        onProgress?.invoke(
                            DownloadProgressSnapshot(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                bytesPerSecond = speed,
                            )
                        )
                        lastEmitNanos = nowNanos
                    }
                }
            }
        }
        connection.disconnect()

        val elapsedSeconds = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L) / 1_000_000_000.0
        val finalSpeed = (downloadedBytes / elapsedSeconds).toLong()
        onProgress?.invoke(
            DownloadProgressSnapshot(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                bytesPerSecond = finalSpeed,
            )
        )

        return destination
    }

    private fun openConnection(sourceUrl: String, rangeStart: Long): HttpURLConnection {
        return (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            if (rangeStart > 0L) {
                setRequestProperty("Range", "bytes=$rangeStart-")
            }
        }
    }

    private fun readErrorBody(connection: HttpURLConnection): String {
        return runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
