plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aiah.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aiah.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../aiah-tracker-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "aiahTracker2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "aiah-tracker"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "aiahTracker2026"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // OSMDroid — карта OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // OkHttp — HTTP-клиент
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson — парсинг JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}