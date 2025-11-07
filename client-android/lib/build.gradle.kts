import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("com.squareup.wire")
}

dependencies {
    api("com.squareup.wire:wire-runtime:5.4.0")

    implementation(kotlin("stdlib"))
    implementation("com.squareup.okio:okio:3.16.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    implementation("at.favre.lib:hkdf:2.0.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:6.0.4")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.4")
    testImplementation("io.kotest:kotest-property-jvm:6.0.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.0")
    testImplementation("com.google.jimfs:jimfs:1.3.1")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all-compatibility")
}

kotlin {
    jvmToolchain(21)
}

tasks.register("qa") {
    dependsOn("check")
}

wire {
    sourcePath {
        srcDir("src/main/protobuf")
    }
    sourcePath {
        srcDir("../../proto/src/main/protobuf")
        include("common.proto")
        include("commands.proto")
    }
    kotlin {}
}
