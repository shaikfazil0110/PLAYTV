plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fzfalcon.webarcade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fzfalcon.webarcade"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    // Keep Java and Kotlin bytecode targets aligned to fix the GitHub Actions error:
    // Java compile target was 1.8 while Kotlin compile target was 17.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
