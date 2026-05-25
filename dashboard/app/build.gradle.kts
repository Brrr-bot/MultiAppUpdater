plugins {
    id("com.android.application")
}

val buildNumberFile = rootProject.file("build_number.txt")
val appVersionCode = buildNumberFile.readText().trim().toInt()
val appVersionName = "1.0.$appVersionCode"

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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
}
