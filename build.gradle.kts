// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.library") version "8.0.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.21" apply false
    id("org.jetbrains.dokka") version "1.8.10" apply false
}

group = "at.bitfire"
version = project.properties["cert4android.version"]
