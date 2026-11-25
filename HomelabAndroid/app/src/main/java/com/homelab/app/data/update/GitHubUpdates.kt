package com.homelab.app.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.homelab.app.BuildConfig
import com.homelab.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
internal data class GitHubAsset(val name: String, val size: Long)

sealed interface GitHubUpdate {
    data object NoRelease : GitHubUpdate
    data object UpToDate : GitHubUpdate
    data class Available(val tag: String, val size: Long) : GitHubUpdate
}

internal fun GitHubRelease.evaluate(installedVersion: String): GitHubUpdate {
    val version = numericVersion(tag) ?: return GitHubUpdate.NoRelease
    val current = numericVersion(installedVersion) ?: return GitHubUpdate.NoRelease
    if (draft || prerelease) return GitHubUpdate.NoRelease
    for (index in version.indices) {
        if (version[index] < current[index]) return GitHubUpdate.UpToDate
        if (version[index] > current[index]) {
            val asset = assets.firstOrNull { it.name == APK_NAME && it.size in 1..MAX_APK_BYTES }
                ?: return GitHubUpdate.NoRelease
            return GitHubUpdate.Available(tag, asset.size)
        }
    }
    return GitHubUpdate.UpToDate
}

private fun numericVersion(tag: String): List<Int>? {
    val version = tag.removePrefix("v")
    if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) return null
    return version.split('.').map { it.toIntOrNull() ?: return null }
}

class GitHubUpdateException(val messageRes: Int) : IOException()

@Singleton
class GitHubUpdates @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val releaseUrl = "https://api.github.com".toHttpUrl().newBuilder()
        .addPathSegment("repos").addPathSegments(BuildConfig.GITHUB_UPDATE_REPOSITORY)
        .addPathSegments("releases/latest").build()

    suspend fun check(): GitHubUpdate = withContext(Dispatchers.IO) {
        check(BuildConfig.ENABLE_UPDATE_CHECK)
        val request = Request.Builder().url(releaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Homelab-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext GitHubUpdate.NoRelease
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty release")
            json.decodeFromString<GitHubRelease>(body.string()).evaluate(BuildConfig.VERSION_NAME)
        }
    }

    suspend fun download(update: GitHubUpdate.Available): File = withContext(Dispatchers.IO) {
        check(BuildConfig.ENABLE_UPDATE_CHECK && numericVersion(update.tag) != null && update.size in 1..MAX_APK_BYTES)
        val url = "https://github.com".toHttpUrl().newBuilder()
            .addPathSegments(BuildConfig.GITHUB_UPDATE_REPOSITORY)
            .addPathSegments("releases/download")
            .addPathSegment(update.tag)
            .addPathSegment(APK_NAME)
            .build()
        val directory = File(context.cacheDir, "updates")
        directory.mkdirs()
        val temporary = File.createTempFile("homelab-", ".apk", directory)
        try {
            downloadTo(url, update.size, temporary)
            verifyArchive(temporary)
            val verified = File(directory, APK_NAME)
            if (!temporary.renameTo(verified)) throw IOException("Could not save APK")
            verified
        } finally {
            temporary.delete()
        }
    }

    private fun downloadTo(url: HttpUrl, size: Long, file: File) {
        val downloadClient = client.newBuilder().followSslRedirects(false).build()
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Empty APK")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        written += count
                        if (written > size) throw IOException("Unexpected APK size")
                        output.write(buffer, 0, count)
                    }
                    if (written != size) throw IOException("Incomplete APK")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = manager.getPackageArchiveInfo(file.path, flags)
            ?: throw GitHubUpdateException(R.string.github_update_incompatible)
        val installed = manager.getPackageInfo(context.packageName, flags)
        val currentSigners = installed.apkSigners
        if (archive.packageName != context.packageName ||
            archive.apkVersion <= installed.apkVersion ||
            currentSigners.isEmpty() ||
            currentSigners != archive.apkSigners
        ) {
            throw GitHubUpdateException(R.string.github_update_incompatible)
        }
    }

    @Suppress("DEPRECATION")
    private val PackageInfo.apkSigners: Set<Signature>
        get() = (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures)
            ?.toSet().orEmpty()

    @Suppress("DEPRECATION")
    private val PackageInfo.apkVersion: Long
        get() = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
}

private const val APK_NAME = "Homelab.apk"
private const val MAX_APK_BYTES = 128L * 1024 * 1024
