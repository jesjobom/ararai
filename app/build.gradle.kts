import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("com.google.gms.google-services")
    id("dev.detekt")
}

fun buildTimestampVersion(): String =
    providers.environmentVariable("ARARAI_VERSION_TIMESTAMP").orNull
        ?: ZonedDateTime
            .now(ZoneId.of("America/Toronto"))
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

fun buildTimestampVersionCode(): Int =
    providers
        .environmentVariable("ARARAI_VERSION_CODE")
        .orNull
        ?.toIntOrNull()
        ?.takeIf { it in 1..2_100_000_000 }
        ?: (System.currentTimeMillis() / 60_000L).toInt()

val configuredDebugKeystore =
    providers
        .environmentVariable("ANDROID_DEBUG_KEYSTORE")
        .orNull
        ?.takeIf(String::isNotBlank)
        ?.let(::file)

fun requiredReleaseSigningEnvironment(name: String): String =
    providers
        .environmentVariable(name)
        .orNull
        ?.takeIf(String::isNotBlank)
        ?: error("Missing required release-signing environment variable: $name")

fun releaseSigningPassword(pathEnvironmentName: String): String =
    file(requiredReleaseSigningEnvironment(pathEnvironmentName))
        .readText()
        .trimEnd()
        .takeIf(String::isNotEmpty)
        ?: error("Release-signing password file is empty: $pathEnvironmentName")

val releaseSigningRequested =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.substringAfterLast(':').endsWith("Release", ignoreCase = true)
    }

val liteRtLmVersion = "0.14.0"

android {
    namespace = "com.jesjobom.ararai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jesjobom.ararai"
        minSdk = 28
        targetSdk = 36
        versionCode = buildTimestampVersionCode()
        versionName = buildTimestampVersion()
        buildConfigField("String", "LITERT_LM_VERSION", "\"$liteRtLmVersion\"")
        buildConfigField("boolean", "EXPERIMENTAL_WEB_SEARCH", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (releaseSigningRequested) {
            create("release") {
                storeFile = file(requiredReleaseSigningEnvironment("ARARAI_UPLOAD_STORE_FILE"))
                storePassword = releaseSigningPassword("ARARAI_UPLOAD_STORE_PASSWORD_FILE")
                keyAlias = requiredReleaseSigningEnvironment("ARARAI_UPLOAD_KEY_ALIAS")
                keyPassword = releaseSigningPassword("ARARAI_UPLOAD_KEY_PASSWORD_FILE")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("boolean", "EXPERIMENTAL_WEB_SEARCH", "true")
            configuredDebugKeystore?.let { persistentKeystore ->
                signingConfig =
                    signingConfigs.getByName("debug").apply {
                        storeFile = persistentKeystore
                    }
            }
        }
        release {
            if (releaseSigningRequested) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("releaseCandidate") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets
        .getByName("releaseCandidate")
        .kotlin.directories
        .add("src/release/java")
}

detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
}

aboutLibraries {
    collect {
        configPath = file("../config/aboutlibraries")
        fetchRemoteLicense = false
        includePlatform = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.17.0")

    implementation(composeBom)
    implementation(firebaseBom)
    implementation(project(":whisper-runtime"))
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmVersion")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.ezylang:EvalEx:3.7.0")
    implementation("com.mikepenz:aboutlibraries-core:14.1.0")
    implementation("com.github.gkonovalov.android-vad:webrtc:2.0.10")
    implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("ru.noties:jlatexmath-android:0.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
