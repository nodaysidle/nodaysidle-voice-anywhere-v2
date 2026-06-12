plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nodaysidle.voiceanywhere"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nodaysidle.voiceanywhere"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = System.getenv("VOICE_ANYWHERE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("VOICE_ANYWHERE_KEY_ALIAS") ?: "nodaysidle-va"
            keyPassword = System.getenv("VOICE_ANYWHERE_KEY_PASSWORD") ?: ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst {
        val missing = listOf(
            "VOICE_ANYWHERE_STORE_PASSWORD",
            "VOICE_ANYWHERE_KEY_ALIAS",
            "VOICE_ANYWHERE_KEY_PASSWORD"
        ).filter { System.getenv(it).isNullOrBlank() }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing requires these environment variables: ${missing.joinToString(", ")}"
            )
        }
    }
}
