plugins {
    id("com.android.application")
    id("com.cefrium") version "0.16.0"
}

android {
    namespace = "com.fzfalcon.webarcade"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.fzfalcon.playtv"
        minSdk = 29
        targetSdk = 28
        versionCode = 2
        versionName = "1.1-chromium"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
}
