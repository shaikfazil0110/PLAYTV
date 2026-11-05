plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fzfalcon.webarcade"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fzfalcon.playtv.legacy"
        minSdk = 28            // Fire OS 7 = Android 9 = API 28
        targetSdk = 34
        versionCode = 4
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed on purpose: this is a sideloaded APK, not a Play Store build.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    // The launcher UI and the game catalogue are shared with the Chromium variant.
    sourceSets {
        getByName("main") {
            assets.srcDir("../shared/assets")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // WebViewAssetLoader serves the launcher from a virtual https origin.
    implementation("androidx.webkit:webkit:1.11.0")
}
