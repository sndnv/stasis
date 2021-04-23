plugins {
    id("com.android.application")
    kotlin("android")
}

dependencies {
    implementation(project(":lib"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.google.android.material:material:1.4.0-alpha02")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("androidx.recyclerview:recyclerview:1.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.2")

    testImplementation("junit:junit:4.13.1")

    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test:rules:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

android {
    compileSdkVersion(30)
    buildToolsVersion("30.0.2")

    defaultConfig {
        minSdkVersion(26)
        targetSdkVersion(30)

        applicationId = "eventually.client"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        exclude("META-INF/atomicfu.kotlin_module")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.register("qa") {
    dependsOn("check")
}
