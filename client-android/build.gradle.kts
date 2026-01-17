import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("com.squareup.wire:wire-gradle-plugin:5.4.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57.2")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.6")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
}

subprojects {
    version = "1.6.2-SNAPSHOT"
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
