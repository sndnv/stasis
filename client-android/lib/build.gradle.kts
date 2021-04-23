import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.gitlab.arturbosch.detekt")
    id("com.squareup.wire")
    jacoco
}

dependencies {
    api("com.squareup.wire:wire-runtime:3.5.0")

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.squareup.okio:okio:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
    implementation("com.squareup.moshi:moshi-adapters:1.11.0")
    implementation("at.favre.lib:hkdf:1.1.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.3.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.3.1")
    testImplementation("io.kotest:kotest-property-jvm:4.3.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.0")
    testImplementation("com.google.jimfs:jimfs:1.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register("qa") {
    dependsOn("check")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.isEnabled = true
    }
}

wire {
    sourcePath {
        srcDir("src/main/protobuf")
    }
    kotlin {}
}
