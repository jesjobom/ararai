import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("dev.detekt")
}

fun buildTimestampVersion(): String =
    providers.environmentVariable("ARARAI_VERSION_TIMESTAMP").orNull
        ?: ZonedDateTime
            .now(ZoneId.of("America/Toronto"))
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

val configuredDebugKeystore =
    providers
        .environmentVariable("ANDROID_DEBUG_KEYSTORE")
        .orNull
        ?.takeIf(String::isNotBlank)
        ?.let(::file)

val liteRtLmVersion = "0.14.0"

android {
    namespace = "com.jesjobom.ararai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jesjobom.ararai"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = buildTimestampVersion()
        buildConfigField("String", "LITERT_LM_VERSION", "\"$liteRtLmVersion\"")
        buildConfigField("boolean", "EXPERIMENTAL_WEB_SEARCH", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    implementation(composeBom)
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
