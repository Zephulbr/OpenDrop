plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.opendrop.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.opendrop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }

    signingConfigs {
        // A fixed debug key, so every CI build can update the previous one.
        // Debug keys are not secret; release builds will use their own key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release key from the environment (the release workflow decodes it from
        // secrets). Without it, release builds are unsigned, which is what
        // F-Droid wants: it builds and signs on its own.
        val keystore = System.getenv("OPENDROP_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("OPENDROP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OPENDROP_KEY_ALIAS")
                keyPassword = System.getenv("OPENDROP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    dependenciesInfo {
        // F-Droid can't read Google's encrypted dependency blob.
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Lists the translated languages for Android 13's per-app language setting.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:transport-classic"))
    implementation(project(":core:transport-usb"))
    implementation(project(":core:dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
