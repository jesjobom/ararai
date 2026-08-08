plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.mikepenz.aboutlibraries.plugin.android") version "14.1.0" apply false
    id("com.diffplug.spotless") version "7.2.1"
    id("dev.detekt") version "2.0.0-alpha.5" apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "whisper-runtime/src/**/*.kt")
        ktlint("1.7.1").editorConfigOverride(
            mapOf("ktlint_standard_function-naming" to "disabled"),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "whisper-runtime/*.gradle.kts")
        ktlint("1.7.1")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
