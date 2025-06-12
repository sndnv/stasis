import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("com.squareup.wire")
}

dependencies {
    api("com.squareup.wire:wire-runtime:5.3.3")

    implementation(kotlin("stdlib"))
    implementation("com.squareup.okio:okio:3.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    implementation("at.favre.lib:hkdf:2.0.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.google.jimfs:jimfs:1.3.0")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all-compatibility")
}

kotlin {
    jvmToolchain(17)
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
