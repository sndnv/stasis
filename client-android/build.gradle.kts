import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.squareup.wire:wire-gradle-plugin:4.9.9")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.52")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.8.5")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
}

subprojects {
    version = "1.3.0"
}

allprojects {
    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = true
            events("passed", "skipped", "failed", "standardOut", "standardError")
        }
    }
}
