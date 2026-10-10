plugins {
    id("com.android.application")
}

android {
    namespace = "com.fixhua.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fixhua.probe"
        minSdk = 29
        targetSdk = 35
        versionCode = 30
        versionName = "3.0.0"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
