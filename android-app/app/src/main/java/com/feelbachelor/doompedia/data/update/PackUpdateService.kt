package com.feelbachelor.doompedia.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.feelbachelor.doompedia.data.importer.DeltaApplier
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
    val message: String,
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
    ): PackUpdateResult {
        if (manifestUrl.isBlank()) {
            return PackUpdateResult(
                status = PackUpdateStatus.NO_MANIFEST,
                installedVersion = installedVersion,
                message = "Manifest URL is empty",
            )
        }

        return runCatching {
            if (wifiOnly && !isOnWifi()) {
                return PackUpdateResult(
                    status = PackUpdateStatus.SKIPPED_NETWORK,
                    installedVersion = installedVersion,
                    message = "Wi-Fi only mode is enabled",
                )
            }

            val manifest = fetchManifest(manifestUrl)
            if (manifest.version <= installedVersion) {
                return PackUpdateResult(
                    status = PackUpdateStatus.UP_TO_DATE,
                    installedVersion = installedVersion,
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
            )

            if (!deltaApplied) {
                applyFullPack(
                    manifestUrl = manifestUrl,
                    manifest = manifest,
                    updateRoot = updateRoot,
                    wifiOnly = wifiOnly,
                )
            }

            PackUpdateResult(
                status = PackUpdateStatus.UPDATED,
                installedVersion = manifest.version,
                message = "Updated to pack version ${manifest.version}",
            )
        }.getOrElse { error ->
            PackUpdateResult(
                status = PackUpdateStatus.FAILED,
                installedVersion = installedVersion,
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
    ): Boolean {
        val delta = manifest.delta ?: return false
        if (delta.baseVersion != installedVersion) return false

        val deltaUrl = resolveUrl(manifestUrl, delta.url)
        val localDelta = File(updateRoot, delta.url.substringAfterLast('/'))
        downloader.download(
            sourceUrl = deltaUrl,
            destination = localDelta,
            wifiOnly = wifiOnly,
            resume = true,
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
    ) {
        val localManifest = manifest.copy(
            shards = manifest.shards.map { shard ->
                val localName = shard.url.substringAfterLast('/')
                val localFile = File(updateRoot, localName)
                downloader.download(
                    sourceUrl = resolveUrl(manifestUrl, shard.url),
                    destination = localFile,
                    wifiOnly = wifiOnly,
                    resume = true,
                )
                val digest = localFile.sha256()
                require(digest.equals(shard.sha256, ignoreCase = true)) {
                    "Checksum mismatch for shard ${shard.id}"
                }

                PackShard(
                    id = shard.id,
                    url = localName,
                    sha256 = shard.sha256,
                    records = shard.records,
                    bytes = localFile.length(),
                )
            }
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
