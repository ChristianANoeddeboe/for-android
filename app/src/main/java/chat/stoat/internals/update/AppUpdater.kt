package chat.stoat.internals.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import chat.stoat.BuildConfig
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Checks the fork's GitHub releases for a newer signed APK, downloads it and hands it to the
 * system installer. The APK is signed with the same key, so it installs over the current app
 * and keeps the user's data.
 */
object AppUpdater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/ChristianANoeddeboe/for-android/releases/latest"
    private const val UPDATE_DIR = "updates"
    private const val UPDATE_FILE = "stoat-update.apk"

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    data class AvailableUpdate(
        val version: String,
        val notes: String?,
        val downloadUrl: String,
        val size: Long,
    )

    /** Update checks only make sense for the sideloaded release build. */
    val isSupported: Boolean
        get() = !BuildConfig.DEBUG

    /** Returns the latest release if it is newer than the installed version, else null. */
    suspend fun checkForUpdate(): AvailableUpdate? {
        val response = StoatHttp.get(LATEST_RELEASE_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw Exception("Failed to check for updates: ${response.status}")
        }

        val release = StoatJson.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
        if (release.draft || release.prerelease) return null
        if (!UpdateVersion.isNewer(release.tagName, BuildConfig.VERSION_NAME)) return null

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return AvailableUpdate(
            version = release.tagName.removePrefix("v"),
            notes = release.body?.let(::summary),
            downloadUrl = apk.downloadUrl,
            size = apk.size,
        )
    }

    /** First paragraph of the release notes, which is written as a plain summary. */
    private fun summary(body: String): String? =
        body.trim().split(Regex("\\r?\\n\\s*\\r?\\n")).firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("#") }

    /** Removes APKs left over from earlier downloads, including an already installed update. */
    fun clearDownloads(context: Context) {
        File(context.cacheDir, UPDATE_DIR).deleteRecursively()
    }

    /** Downloads the update APK into the cache, reporting progress from 0 to 1 when known. */
    suspend fun download(
        context: Context,
        update: AvailableUpdate,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(dir, UPDATE_FILE)
        val partial = File(dir, "$UPDATE_FILE.part")

        StoatHttp.prepareGet(update.downloadUrl).execute { response ->
            if (!response.status.isSuccess()) {
                throw Exception("Failed to download update: ${response.status}")
            }
            val total = response.contentLength() ?: update.size.takeIf { it > 0 }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L

            partial.outputStream().use { output ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(total?.let { downloaded.toFloat() / it })
                }
            }
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        target
    }

    /** Whether the user has allowed this app to install packages. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

    /** Opens the system setting where the user allows this app to install updates. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Hands the downloaded APK to the system package installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
