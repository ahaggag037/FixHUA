plugins {
    id("com.android.application")
}

android {
    namespace = "com.fixhua.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fixhua.guard"
        minSdk = 28
        targetSdk = 35
        versionCode = 24
        versionName = "2.2.0"
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
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
