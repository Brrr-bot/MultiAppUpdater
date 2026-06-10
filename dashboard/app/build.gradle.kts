plugins {
    id("com.android.application")
}

fun localProperty(name: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return ""
    return file.readLines()
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.trim()
        ?.replace("\\:", ":")
        ?.replace("\\\\", "\\")
        .orEmpty()
}

fun asBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: rootProject.file("build_number.txt").readText().trim().toInt()
val appVersionName = (findProperty("versionName") as String?) ?: "1.0.$appVersionCode"

android {
    namespace = "com.homehub.dashboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.homehub.dashboard"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        getByName("debug") {
            (findProperty("debugKeystore") as String?)?.let { ks ->
                storeFile = file(ks)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "CAMERA_HOST", asBuildConfigString(localProperty("camera.host")))
            buildConfigField("String", "CAMERA_USER", asBuildConfigString(localProperty("camera.user")))
            buildConfigField("String", "CAMERA_PASSWORD", asBuildConfigString(localProperty("camera.password")))
        }
        getByName("release") {
            buildConfigField("String", "CAMERA_HOST", "\"\"")
            buildConfigField("String", "CAMERA_USER", "\"\"")
            buildConfigField("String", "CAMERA_PASSWORD", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.ui)
}
