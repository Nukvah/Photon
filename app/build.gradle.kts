plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nukvah.photon"
    compileSdk = 36
    // NDK r28+ links native code with 16 KB page alignment by default.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // The application ID is permanent once published on Google Play.
        applicationId = "com.nukvah.photon"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    buildFeatures {
        compose = true
    }

    packaging {
        // Keep .so files uncompressed and page-aligned in the APK (needed for 16 KB pages).
        jniLibs.useLegacyPackaging = false
    }

    sourceSets["main"].apply {
        jniLibs.srcDir(layout.buildDirectory.dir("rust/jniLibs"))
        java.srcDir(layout.buildDirectory.dir("rust/kotlin"))
    }
}

kotlin {
    jvmToolchain(17)
}

// Builds the Rust core for each Android ABI and generates its Kotlin bindings.
// Set PHOTON_ABIS (for example "arm64-v8a") to build fewer ABIs while developing.
val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds photon-core with cargo-ndk and generates uniffi Kotlin bindings."
    val script = rootProject.file("scripts/build-rust.sh")
    val jniOut = layout.buildDirectory.dir("rust/jniLibs").get().asFile
    val kotlinOut = layout.buildDirectory.dir("rust/kotlin").get().asFile
    inputs.dir(rootProject.file("core/src"))
    inputs.dir(rootProject.file("core/jpegli-sys/shim"))
    inputs.file(rootProject.file("core/jpegli-sys/build.rs"))
    inputs.file(rootProject.file("core/Cargo.toml"))
    inputs.file(rootProject.file("core/uniffi.toml"))
    inputs.file(script)
    outputs.dir(jniOut)
    outputs.dir(kotlinOut)
    commandLine("bash", script.absolutePath, jniOut.absolutePath, kotlinOut.absolutePath)
    doFirst {
        // Point cargo-ndk at the same NDK that Gradle uses, unless one is already set.
        if (System.getenv("ANDROID_NDK_HOME").isNullOrEmpty()) {
            environment("ANDROID_NDK_HOME", android.ndkDirectory.absolutePath)
        }
    }
}

tasks.named("preBuild") { dependsOn(buildRust) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // uniffi's Kotlin bindings call Rust through JNA.
    implementation(libs.jna) { artifact { type = "aar" } }
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
