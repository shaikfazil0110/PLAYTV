plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.cefrium") version "0.16.0"
}

android {
    namespace = "com.fzfalcon.webarcade"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fzfalcon.playtv"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "2.0.0-chromium"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.16.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
