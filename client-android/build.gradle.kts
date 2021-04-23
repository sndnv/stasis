import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.21")
        classpath("com.android.tools.build:gradle:4.1.3")
        classpath("com.squareup.wire:wire-gradle-plugin:3.5.0")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.12.0-RC1"
    jacoco
}

subprojects {
    version = "1.0.0-SNAPSHOT"
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
