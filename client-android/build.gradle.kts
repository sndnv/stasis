import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("com.squareup.wire:wire-gradle-plugin:6.2.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.60")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.8")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp") version "2.3.6" apply false
}

subprojects {
    version = "1.7.6-SNAPSHOT"
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
