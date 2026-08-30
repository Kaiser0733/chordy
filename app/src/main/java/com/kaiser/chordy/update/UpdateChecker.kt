package com.kaiser.chordy.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kaiser.chordy.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updater: CI publishes every green build to the GitHub release tagged
 * "latest" (chordy-update.apk + update-info.json). The app compares versionCode
 * and can download + launch the Android package installer directly — an
 * in-place update, so granted permissions survive. No more uninstall/reinstall
 * + permission re-granting after every build.
 *
 * First install attempt triggers Android's "install unknown apps" consent for
 * Chordy — granted ONCE, then every future update is a single tap.
 */
class UpdateChecker(private val context: Context) {

    @Serializable
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Stable public URL of the manifest published by CI. */
    private val manifestUrl =
        "https://github.com/Kaiser0733/chordy/releases/download/latest/update-info.json"

    /** null = up to date (or check failed — never block the UI on this). */
    fun checkForUpdate(): UpdateInfo? {
        return try {
            val response = http.newCall(
                Request.Builder().url(manifestUrl).build()
            ).execute()
            if (!response.isSuccessful) return null
            val info = json.decodeFromString(
                UpdateInfo.serializer(),
                response.body?.string() ?: return null
            )
            // Newer build published? (versionCode is the single source of truth)
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download the APK into the cache dir and hand it to the system installer.
     * Returns true if the install intent fired; false on download failure.
     * Long-running: call from Dispatchers.IO.
     */
    fun downloadAndInstall(onProgress: (Int) -> Unit): Boolean {
        return try {
            val info = checkForUpdate() ?: return false
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "chordy-update.apk")
            if (apk.exists()) apk.delete()

            val response = http.newCall(Request.Builder().url(info.apkUrl).build()).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val total = body.contentLength()
            apk.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt())
                    }
                }
            }
            launchInstaller(apk)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
