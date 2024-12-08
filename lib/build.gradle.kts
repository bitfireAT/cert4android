plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "at.bitfire.cert4android"

    compileSdk = 35

    defaultConfig {
        minSdk = 21            // Android 5

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
    implementation(libs.conscrypt)

    // Jetpack Compose
    implementation(libs.androidx.activityCompose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    implementation(libs.compose.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.toolingPreview)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.okttp3.mockwebserver)
    androidTestImplementation(libs.mockk.android)

    testImplementation(libs.junit)
}