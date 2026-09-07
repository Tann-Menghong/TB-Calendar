plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.khmercalendar.ai"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}


dependencies {
    api(project(":calendar-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)

    // The only backend that ships as a plain Gradle dependency with no NDK build. It is
    // reached exclusively through AiEngine, so replacing it with llama.cpp or LiteRT is a
    // new class in this module and one line in AiEngineFactory - see docs/AI_MODELS.md.
    compileOnly(libs.mediapipe.tasks.genai)
    runtimeOnly(libs.mediapipe.tasks.genai)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
