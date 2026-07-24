plugins {
    id("com.android.library")
}

android {
    namespace = "com.jesjobom.ararai.whisper"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments +=
                    "-DFETCHCONTENT_BASE_DIR=${rootProject.layout.buildDirectory.dir("whisper-fetch-content").get().asFile.absolutePath}"
            }
        }
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
