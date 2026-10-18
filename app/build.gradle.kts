plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kavach.guard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kavach.guard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-prototype"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
