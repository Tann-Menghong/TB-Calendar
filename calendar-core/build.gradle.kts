plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module, not an Android one. The Khmer calendar arithmetic, the
// holiday rules, the recurrence expander and the natural-language parser have no Android
// dependencies, so keeping them here means their tests run on the JVM in about a second
// instead of needing a device or Robolectric. It also makes it structurally impossible for
// UI concerns to leak into the date logic.
// Java 17 bytecode, produced with whatever JDK (17 or newer) is running Gradle. Pinning a
// toolchain instead would make a fresh clone download a second JDK for no benefit.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // The conversion tests sweep tens of thousands of dates.
    maxHeapSize = "1g"
}
