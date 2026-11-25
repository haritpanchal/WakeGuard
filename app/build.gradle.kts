plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haritpanchal.wakeguard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.haritpanchal.wakeguard"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    testImplementation(libs.junit)
}
