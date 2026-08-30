plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kaiser.chordy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kaiser.chordy"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.1"

        // Bundled LLM config. Keys arrive via GitHub Actions secrets
        // (NVIDIA_NIM_KEY, GROQ_API_KEY) and land only in the built APK via
        // BuildConfig — never in a commit, never in source control.
        buildConfigField("String", "NIM_BASE_URL", "\"https://integrate.api.nvidia.com/v1\"")
        buildConfigField("String", "NIM_MODEL", "\"openai/gpt-oss-120b\"")
        buildConfigField("String", "NIM_API_KEY", "\"${System.getenv("NVIDIA_NIM_KEY") ?: ""}\"")
        buildConfigField("String", "GROQ_BASE_URL", "\"https://api.groq.com/openai/v1\"")
        buildConfigField("String", "GROQ_MODEL", "\"openai/gpt-oss-120b\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${System.getenv("GROQ_API_KEY") ?: ""}\"")
    }

    signingConfigs {
        create("stable") {
            // THE one debug keystore — same key forever, so in-app updates
            // install over the previous APK instead of dying on "apk conflict".
            // CI restores this exact keystore from the DEBUG_KEYSTORE_B64 secret
            // into ~/.android/debug.keystore before every build. If a local
            // build can't find it, fall back to AGP's default debug keystore.
            val ks = File(System.getProperty("user.home"), ".android/debug.keystore")
            storeFile = ks
            storePassword = "chordy"
            keyAlias = "chordy"
            keyPassword = "chordy"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.lifecycle.service)
    implementation(libs.activity.compose)
    implementation(libs.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.core:core-ktx:1.16.0")
}
