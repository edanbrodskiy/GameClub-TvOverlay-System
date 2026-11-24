plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.mvplisboa.sessionoverlay"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.mvplisboa.sessionoverlay"
        minSdk = 26
        // Deliberately limited to the Android TV 12 proof-of-concept in the guide.
        targetSdk = 32
        versionCode = 3
        versionName = "0.2.1-compact"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // This sideloaded TV 12 PoC intentionally targets 32 (guide, section 4).
        // Keep the finding visible; Play distribution requires a target SDK update.
        warning.add("ExpiredTargetSdkVersion")
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
    testImplementation("junit:junit:4.13.2")
}

