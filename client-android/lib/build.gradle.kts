import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.gitlab.arturbosch.detekt")
    id("com.squareup.wire")
}

dependencies {
    api("com.squareup.wire:wire-runtime:4.4.1")

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.squareup.okio:okio:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation("com.squareup.moshi:moshi-adapters:1.13.0")
    implementation("at.favre.lib:hkdf:1.1.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.21")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.13.0")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.3.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.4.1")
    testImplementation("io.kotest:kotest-property-jvm:5.4.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
    testImplementation("com.google.jimfs:jimfs:1.2")
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=compatibility")
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
