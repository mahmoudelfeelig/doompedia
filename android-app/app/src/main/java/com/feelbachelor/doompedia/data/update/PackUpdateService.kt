package com.feelbachelor.doompedia.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.feelbachelor.doompedia.data.importer.DeltaApplier
import com.feelbachelor.doompedia.data.importer.DownloadProgressSnapshot
import com.feelbachelor.doompedia.data.importer.PackInstaller
import com.feelbachelor.doompedia.data.importer.PackManifest
import com.feelbachelor.doompedia.data.importer.PackShard
import com.feelbachelor.doompedia.data.importer.ShardDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.security.MessageDigest

enum class PackUpdateStatus {
    NO_MANIFEST,
    SKIPPED_NETWORK,
    UP_TO_DATE,
    UPDATED,
    FAILED,
}

data class PackUpdateResult(
    val status: PackUpdateStatus,
    val installedVersion: Int,
    val installedSignature: String,
    val message: String,
)

data class PackUpdateProgress(
    val phase: String,
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val detail: String = "",
)

class PackUpdateService(
    private val context: Context,
    private val downloader: ShardDownloader,
    private val packInstaller: PackInstaller,
    private val deltaApplier: DeltaApplier,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun checkAndApply(
        manifestUrl: String,
        wifiOnly: Boolean,
        installedVersion: Int,
        installedSignature: String,
        onProgress: ((PackUpdateProgress) -> Unit)? = null,
    ): PackUpdateResult {
        if (manifestUrl.isBlank()) {
            return PackUpdateResult(
                status = PackUpdateStatus.NO_MANIFEST,
                installedVersion = installedVersion,
                installedSignature = installedSignature,
                message = "Manifest URL is empty",
            )
        }

        return runCatching {
            if (wifiOnly && !isOnWifi()) {
                return PackUpdateResult(
                    status = PackUpdateStatus.SKIPPED_NETWORK,
                    installedVersion = installedVersion,
                    installedSignature = installedSignature,
                    message = "Wi-Fi only mode is enabled. Disable it in Settings to download over mobile data.",
                )
            }

            onProgress?.invoke(
                PackUpdateProgress(
                    phase = "Fetching manifest",
                    percent = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    bytesPerSecond = 0L,
                    detail = manifestUrl,
                )
            )
            val manifest = fetchManifest(manifestUrl)
            val remoteSignature = manifest.signature()
            val isSameOrOlderVersion = manifest.version <= installedVersion
            if (isSameOrOlderVersion && remoteSignature == installedSignature) {
                return PackUpdateResult(
                    status = PackUpdateStatus.UP_TO_DATE,
                    installedVersion = installedVersion,
                    installedSignature = installedSignature,
                    message = "No new pack available",
                )
            }

            val updateRoot = File(context.filesDir, "doompedia/updates/${manifest.packId}/v${manifest.version}")
            updateRoot.mkdirs()

            val deltaApplied = tryApplyDelta(
                manifestUrl = manifestUrl,
                manifest = manifest,
                updateRoot = updateRoot,
                wifiOnly = wifiOnly,
                installedVersion = installedVersion,
                onProgress = onProgress,
            )

            if (!deltaApplied) {
                applyFullPack(
                    manifestUrl = manifestUrl,
                    manifest = manifest,
                    updateRoot = updateRoot,
                    wifiOnly = wifiOnly,
                    onProgress = onProgress,
                )
            }

            onProgress?.invoke(
                PackUpdateProgress(
                    phase = "Completed",
                    percent = 100f,
                    downloadedBytes = manifest.shards.sumOf { it.bytes },
                    totalBytes = manifest.shards.sumOf { it.bytes },
                    bytesPerSecond = 0L,
                    detail = "Installed pack v${manifest.version}",
                )
            )
            PackUpdateResult(
                status = PackUpdateStatus.UPDATED,
                installedVersion = manifest.version,
                installedSignature = remoteSignature,
                message = "Updated to pack version ${manifest.version}",
            )
        }.getOrElse { error ->
            PackUpdateResult(
                status = PackUpdateStatus.FAILED,
                installedVersion = installedVersion,
                installedSignature = installedSignature,
                message = error.message ?: "Unknown update failure",
            )
        }
    }

    private suspend fun fetchManifest(manifestUrl: String): PackManifest {
        return withContext(Dispatchers.IO) {
            val payload = URL(manifestUrl).openStream().bufferedReader().use { it.readText() }
            json.decodeFromString(PackManifest.serializer(), payload)
        }
    }

    private suspend fun tryApplyDelta(
        manifestUrl: String,
        manifest: PackManifest,
        updateRoot: File,
        wifiOnly: Boolean,
        installedVersion: Int,
        onProgress: ((PackUpdateProgress) -> Unit)? = null,
    ): Boolean {
        val delta = manifest.delta ?: return false
        if (delta.baseVersion != installedVersion) return false

        val deltaUrl = resolveUrl(manifestUrl, delta.url)
        val localDelta = File(updateRoot, delta.url.substringAfterLast('/'))
        onProgress?.invoke(
            PackUpdateProgress(
                phase = "Downloading delta",
                percent = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                bytesPerSecond = 0L,
                detail = localDelta.name,
            )
        )
        downloader.download(
            sourceUrl = deltaUrl,
            destination = localDelta,
            wifiOnly = wifiOnly,
            resume = true,
            onProgress = { snapshot ->
                onProgress?.invoke(
                    snapshot.toPackProgress(
                        phase = "Downloading delta",
                        detail = localDelta.name,
                    )
                )
            }
        )

        val digest = localDelta.sha256()
        if (!digest.equals(delta.sha256, ignoreCase = true)) {
            localDelta.delete()
            return false
        }

        deltaApplier.apply(localDelta)
        return true
    }

    private suspend fun applyFullPack(
        manifestUrl: String,
        manifest: PackManifest,
        updateRoot: File,
        wifiOnly: Boolean,
        onProgress: ((PackUpdateProgress) -> Unit)? = null,
    ) {
        val totalBytes = manifest.shards.sumOf { it.bytes }.coerceAtLeast(1L)
        var completedBytes = 0L

        val localManifest = manifest.copy(
            shards = manifest.shards.map { shard ->
                val localName = shard.url.substringAfterLast('/')
                val localFile = File(updateRoot, localName)
                downloader.download(
                    sourceUrl = resolveUrl(manifestUrl, shard.url),
                    destination = localFile,
                    wifiOnly = wifiOnly,
                    resume = true,
                    onProgress = { snapshot ->
                        val globalDownloaded = (completedBytes + snapshot.downloadedBytes).coerceAtMost(totalBytes)
                        val percent = ((globalDownloaded.toDouble() / totalBytes.toDouble()) * 100.0).toFloat()
                        onProgress?.invoke(
                            PackUpdateProgress(
                                phase = "Downloading shards",
                                percent = percent,
                                downloadedBytes = globalDownloaded,
                                totalBytes = totalBytes,
                                bytesPerSecond = snapshot.bytesPerSecond,
                                detail = shard.id,
                            )
                        )
                    }
                )
                val digest = localFile.sha256()
                require(digest.equals(shard.sha256, ignoreCase = true)) {
                    "Checksum mismatch for shard ${shard.id}"
                }
                completedBytes += localFile.length().coerceAtMost(shard.bytes)

                PackShard(
                    id = shard.id,
                    url = localName,
                    sha256 = shard.sha256,
                    records = shard.records,
                    bytes = localFile.length(),
                )
            }
        )

        onProgress?.invoke(
            PackUpdateProgress(
                phase = "Installing",
                percent = 100f,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                bytesPerSecond = 0L,
                detail = "Applying downloaded content",
            )
        )
        File(updateRoot, "manifest.json").writeText(
            json.encodeToString(PackManifest.serializer(), localManifest),
        )
        packInstaller.installFromDirectory(updateRoot, expectedPackId = manifest.packId)
    }

    private fun resolveUrl(manifestUrl: String, shardUrl: String): String {
        return runCatching {
            URL(URL(manifestUrl), shardUrl).toString()
        }.getOrElse {
            if (shardUrl.startsWith("http://") || shardUrl.startsWith("https://")) {
                shardUrl
            } else {
                "$manifestUrl/$shardUrl"
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

private fun PackManifest.signature(): String {
    val raw = buildString {
        append(packId).append('|')
        append(language).append('|')
        append(version).append('|')
        append(createdAt).append('|')
        append(recordCount).append('|')
        append(compression).append('|')
        append(shards.size).append('|')
        shards.forEach { shard ->
            append(shard.id).append(':')
            append(shard.records).append(':')
            append(shard.bytes).append(':')
            append(shard.sha256).append(';')
        }
        delta?.let {
            append("|delta:")
                .append(it.baseVersion).append(':')
                .append(it.targetVersion).append(':')
                .append(it.sha256)
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}

private fun DownloadProgressSnapshot.toPackProgress(
    phase: String,
    detail: String,
): PackUpdateProgress {
    val boundedTotal = totalBytes.coerceAtLeast(0L)
    val boundedDownloaded = downloadedBytes.coerceAtLeast(0L)
    val percent = if (boundedTotal > 0L) {
        ((boundedDownloaded.toDouble() / boundedTotal.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .toFloat()
    } else {
        0f
    }
    return PackUpdateProgress(
        phase = phase,
        percent = percent,
        downloadedBytes = boundedDownloaded,
        totalBytes = boundedTotal,
        bytesPerSecond = bytesPerSecond.coerceAtLeast(0L),
        detail = detail,
    )
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
