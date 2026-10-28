plugins {
    id("com.android.application")
}

android {
    namespace = "com.fzfalcon.webarcade"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fzfalcon.playtv.legacy"
        minSdk = 23
        targetSdk = 28
        versionCode = 2
        versionName = "1.1-legacy"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}
