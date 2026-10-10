plugins {
    id("com.android.application")
}

android {
    namespace = "com.fixhua.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fixhua.guard"
        minSdk = 29
        targetSdk = 35
        versionCode = 21
        versionName = "2.1.0-temp"
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
    testImplementation("junit:junit:4.13.2")
}
