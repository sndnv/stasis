plugins {
    id("com.android.application")
    id("dagger.hilt.android.plugin")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
}

dependencies {
    implementation(project(":lib"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.25")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.15.0")
    implementation("io.github.amrdeveloper:treeview:1.2.0")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.14")

    debugImplementation("androidx.fragment:fragment-testing:1.8.5") {
        exclude(group = "androidx.test", module = "monitor")
    }
}

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        targetSdk = 34

        applicationId = "stasis.client.android"
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    }

    namespace = "stasis.client_android"
}

kotlin {
    jvmToolchain(17)
}

tasks.register("qa") {
    dependsOn("check")
}
