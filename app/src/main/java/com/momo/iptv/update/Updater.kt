package com.momo.iptv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.momo.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Mises à jour depuis les Releases GitHub : chaque compilation publie « v1.0.N »
 * où N est le versionCode de l'APK.
 */
object Updater {
    private const val LATEST_RELEASE = "https://api.github.com/repos/Mirmo-DEV/momo-iptv/releases/latest"

    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String)

    fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST_RELEASE).header("Accept", "application/vnd.github+json").build()
        Http.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub a répondu HTTP ${resp.code}")
            val root = Json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@use null
            val tag = (root["tag_name"] as? JsonPrimitive)?.content ?: return@use null
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@use null
            val apkUrl = (root["assets"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["name"] as? JsonPrimitive)?.content?.endsWith(".apk") == true }
                ?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
                ?: return@use null
            Release(code, tag.removePrefix("v"), apkUrl)
        }
    }

    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "MomoIPTV.apk")
        val tmp = File(dir, "MomoIPTV.apk.part")
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("téléchargement refusé (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("réponse vide")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        file.delete()
        if (!tmp.renameTo(file)) throw IOException("impossible d'enregistrer la mise à jour")
        file
    }

    /** Android 8+ : l'appli doit être autorisée une fois à installer des applis. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Ouvre l'écran d'installation du système (l'utilisateur confirme, l'appli est remplacée). */
    fun install(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
