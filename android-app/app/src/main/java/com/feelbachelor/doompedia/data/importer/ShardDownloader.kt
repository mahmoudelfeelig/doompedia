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

class ShardDownloader(
    private val context: Context,
) {
    suspend fun download(
        sourceUrl: String,
        destination: File,
        wifiOnly: Boolean,
        resume: Boolean = true,
    ): File {
        return withContext(Dispatchers.IO) {
            if (wifiOnly && !isOnWifi()) {
                error("Wi-Fi only download is enabled")
            }

            destination.parentFile?.mkdirs()
            val existingBytes = if (resume && destination.exists()) destination.length() else 0L
            val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }

            connection.connect()
            val append = existingBytes > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            connection.inputStream.use { input ->
                FileOutputStream(destination, append).buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }

            destination
        }
    }

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
