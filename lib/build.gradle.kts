/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dokka)
    `maven-publish`
}

android {
    namespace = "at.bitfire.cert4android"

    compileSdk = 36

    defaultConfig {
        minSdk = 23            // Android 6

        aarMetadata {
            minCompileSdk = 29
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // Android libraries shouldn't be minified:
            // https://developer.android.com/studio/projects/android-library#Considerations
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    lint {
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("virtual") {
                    device = "Pixel 3"
                    apiLevel = 33
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                }
            }
        }
    }

    testFixtures {
        enable = true
    }

    packaging {
        resources {
            excludes.add("META-INF/*.md")
            excludes.add("META-INF/versions/*/OSGI-INF/MANIFEST.MF")
        }
    }

    publishing {
        // Configure publish variant
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    // Configure publishing data
    publications {
        register("release", MavenPublication::class.java) {
            groupId = "com.github.bitfireAT"
            artifactId = "cert4android"
            version = System.getenv("GIT_COMMIT")

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)

    // Jetpack Compose
    implementation(libs.androidx.activityCompose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.toolingPreview)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.conscrypt)
    androidTestImplementation(libs.kotlin.coroutines.test)
    androidTestImplementation(libs.okhttp3.mockwebserver)
    androidTestImplementation(libs.mockk.android)

    testImplementation(libs.junit)
}