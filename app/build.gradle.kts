plugins {
    id("com.android.application")
}

android {
    namespace = "com.fixhua.diagnostics"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fixhua.diagnostics"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
