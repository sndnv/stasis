import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("com.squareup.wire")
}

dependencies {
    api("com.squareup.wire:wire-runtime:4.9.1")

    implementation(kotlin("stdlib"))
    implementation("com.squareup.okio:okio:3.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.moshi:moshi-adapters:1.15.0")
    implementation("at.favre.lib:hkdf:2.0.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.7.2")
    testImplementation("io.kotest:kotest-property-jvm:5.7.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("com.google.jimfs:jimfs:1.3.0")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
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
    kotlin {}
}
