plugins {
    id("com.android.application")
    id("dagger.hilt.android.plugin")
    kotlin("android")
    kotlin("kapt")
    id("androidx.navigation.safeargs.kotlin")
}

dependencies {
    implementation(project(":lib"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.fragment:fragment-ktx:1.4.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.4.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.4.2")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.4.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.room:room-runtime:2.4.2")
    implementation("androidx.room:room-ktx:2.4.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha03")
    implementation("org.bitbucket.b_c:jose4j:0.7.9")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")

    implementation("com.google.dagger:hilt-android:2.40.5")
    kapt("com.google.dagger:hilt-compiler:2.40.5")
    kapt("androidx.room:room-compiler:2.4.2")

    testImplementation("io.mockk:mockk:1.12.2")
    testImplementation("org.robolectric:robolectric:4.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.4.0")

    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.room:room-testing:2.4.2")
    androidTestImplementation("androidx.arch.core:core-testing:2.1.0")
    androidTestImplementation("io.mockk:mockk-android:1.12.2")

    debugImplementation("androidx.fragment:fragment-testing:1.4.1") {
        exclude(group = "androidx.test", module = "monitor")
    }
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 28
        targetSdk = 30

        applicationId = "stasis.client.android"
        versionCode = 1
        versionName = "1.0.0-SNAPSHOT"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        resources {
            excludes += setOf("META-INF/atomicfu.kotlin_module")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.register("qa") {
    dependsOn("check")
}
