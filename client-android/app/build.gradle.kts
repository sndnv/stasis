import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("dagger.hilt.android.plugin")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
}

dependencies {
    implementation(project(":lib"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.15.0")
    implementation("io.github.amrdeveloper:treeview:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-rc01")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("io.mockk:mockk-android:1.14.7")

    debugImplementation("androidx.fragment:fragment-testing:1.8.9") {
        exclude(group = "androidx.test", module = "monitor")
    }
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        applicationId = "stasis.client.android"
        versionCode = 11
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "BUILD_TIME", getBuildTime())
    }

    signingConfigs {
        create("release") {
            storeFile = file("secrets/signing.jks")
            storePassword = System.getenv("ANDROID_SIGNING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            applicationVariants.all {
                outputs
                    .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                    .forEach {
                        it.outputFileName = "${rootProject.name}_${defaultConfig.versionName}.apk"
                    }
            }

            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += setOf("META-INF/atomicfu.kotlin_module", "META-INF/LICENSE.md")
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        packaging { jniLibs { useLegacyPackaging = true } }
    }

    namespace = "stasis.client_android"
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.register("qa") {
    dependsOn("check")
}

fun getBuildTime(): String {
    val existingTags = providers.exec {
        commandLine("git", "tag", "--points-at", "HEAD")
    }.standardOutput.asText.get().trim()

    val buildTime = if (existingTags.isNotBlank()) {
        val commitTimestamp = providers.exec {
            commandLine("git", "show", "-s", "--format=%at")
        }.standardOutput.asText.get().trim()

        commitTimestamp.toLongOrNull()?.let { it * 1000 } ?: System.currentTimeMillis()
    } else {
        System.currentTimeMillis()
    }

    return buildTime.toString()
}
