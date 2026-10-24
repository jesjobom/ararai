package com.jesjobom.ararai.validation

import android.content.Context
import android.os.Build
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

@Suppress("InjectDispatcher")
internal suspend fun widgetToolCallingDiagnosticEnvironment(
    context: Context,
    modelArtifactSha256: String,
): WidgetToolCallingDiagnosticEnvironment = withContext(Dispatchers.IO) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val apk = File(context.applicationInfo.sourceDir)
    WidgetToolCallingDiagnosticEnvironment(
        generatedAtUtc = Instant.now().toString(),
        manufacturer = Build.MANUFACTURER.ifBlank { UNKNOWN },
        deviceModel = Build.MODEL.ifBlank { UNKNOWN },
        androidRelease = Build.VERSION.RELEASE.ifBlank { UNKNOWN },
        sdkInt = Build.VERSION.SDK_INT,
        buildDisplay = Build.DISPLAY.ifBlank { UNKNOWN },
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        appVersion = packageInfo.versionName ?: UNKNOWN,
        versionCode = packageInfo.longVersionCode,
        buildType = BuildConfig.BUILD_TYPE,
        apkSha256 = apk.inputStream().use(::sha256Stream),
        modelArtifactSha256 = modelArtifactSha256,
    )
}

private fun sha256Stream(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(HASH_BUFFER_BYTES)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return buildString {
        digest.digest().forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }
}

private const val HASH_BUFFER_BYTES = 64 * 1024
private const val UNKNOWN = "unknown"
private const val HEX = "0123456789abcdef"
