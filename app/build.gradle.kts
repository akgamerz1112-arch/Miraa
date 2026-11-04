plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional fixed signing key. Supplied ONLY through environment variables
// (GitHub Actions secrets). Nothing secret is stored in this repository.
val ksPath: String? = System.getenv("MIRA_KEYSTORE_PATH")

android {
    namespace = "com.mira.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mira.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.9.0-livekit"
    }

    signingConfigs {
        if (ksPath != null) {
            create("mira") {
                storeFile = file(ksPath)
                storePassword = System.getenv("MIRA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIRA_KEY_ALIAS")
                keyPassword = System.getenv("MIRA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (ksPath != null) signingConfig = signingConfigs.getByName("mira")
        }
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("mira")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // LiveKit Android SDK (current per docs.livekit.io reference: 2.30.0). Not resolved/compiled in Session 3.
    implementation("io.livekit:livekit-android:2.30.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
