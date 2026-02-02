plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "at.bitfire.cert4android.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.bitfire.cert4android.demo"
        versionCode = 1
        versionName = "1.0.0"

        minSdk = 23
        targetSdk = 36

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activityCompose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.base)
    implementation(libs.compose.ui.graphics)
    debugImplementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.runtime.livedata)

    implementation(libs.okhttp)

    implementation(project(":lib"))
}