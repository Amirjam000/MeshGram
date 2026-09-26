package com.meshgram.app.transfer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.meshgram.app.model.InstalledAppInfo
import java.io.File

/**
 * ماژول استخراج و ارسال مستقیم فایل APK برنامه‌های نصب‌شده روی گوشی بدون نیاز به اینترنت.
 */
class AppExtractor(private val context: Context) {

    fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledAppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val appList = mutableListOf<InstalledAppInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            if (!includeSystemApps && isSystem) {
                continue
            }

            // Exclude our own package from sending to self
            if (pkg.packageName == context.packageName) {
                continue
            }

            val appName = pm.getApplicationLabel(appInfo).toString()
            val sourceDir = appInfo.sourceDir
            val file = File(sourceDir)
            val size = if (file.exists()) file.length() else 0L
            val versionName = pkg.versionName ?: "1.0"

            appList.add(
                InstalledAppInfo(
                    appName = appName,
                    packageName = pkg.packageName,
                    sourceDir = sourceDir,
                    apkSize = size,
                    versionName = versionName,
                    isSystemApp = isSystem
                )
            )
        }

        return appList.sortedBy { it.appName.lowercase() }
    }

    /**
     * استخراج فایل APK به یک دایرکتوری موقت برای ارسال
     */
    fun extractApk(appInfo: InstalledAppInfo): File {
        val sourceFile = File(appInfo.sourceDir)
        val targetName = "${appInfo.appName.replace(" ", "_")}_v${appInfo.versionName}.apk"
        val cacheDir = File(context.cacheDir, "extracted_apks").apply { mkdirs() }
        val targetFile = File(cacheDir, targetName)

        if (!targetFile.exists() || targetFile.length() != sourceFile.length()) {
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            }
        }
        return targetFile
    }
}
