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
        versionCode = 3
        versionName = "0.3.0"

        // Bundled LLM config. The key arrives via GitHub Actions secret
        // (NVIDIA_NIM_KEY) and lands only in the built APK via BuildConfig —
        // never in a commit, never in source control.
        buildConfigField("String", "NIM_BASE_URL", "\"https://integrate.api.nvidia.com/v1\"")
        buildConfigField("String", "NIM_MODEL", "\"openai/gpt-oss-120b\"")
        buildConfigField("String", "NIM_API_KEY", "\"${System.getenv("NVIDIA_NIM_KEY") ?: ""}\"")
    }

    buildTypes {
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
