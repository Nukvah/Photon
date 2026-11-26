plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
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
        // The release workflow sets these from the run number and the git tag.
        versionCode = System.getenv("PHOTON_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("PHOTON_VERSION_NAME") ?: "0.1.0"
        // Google Play does not allow links to outside payment pages, so the Play build
        // (PHOTON_PLAY_BUILD=true, set by the release workflow) has no Donate row.
        val playBuild = System.getenv("PHOTON_PLAY_BUILD") == "true"
        buildConfigField("boolean", "SHOW_DONATE", (!playBuild).toString())
    }

    // The upload key for Google Play. CI writes the keystore from repository secrets;
    // without them the release build is unsigned (fine for local checks).
    val keystore = System.getenv("PHOTON_KEYSTORE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (keystore != null) {
            create("upload") {
                storeFile = keystore
                storePassword = System.getenv("PHOTON_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PHOTON_KEY_ALIAS")
                keyPassword = System.getenv("PHOTON_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric screenshot tests need the app's resources (strings, fonts).
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        // Keep .so files uncompressed and page-aligned in the APK (needed for 16 KB pages).
        jniLibs.useLegacyPackaging = false
    }

    sourceSets["main"].apply {
        jniLibs.srcDir(layout.buildDirectory.dir("rust/jniLibs"))
        java.srcDir(layout.buildDirectory.dir("rust/kotlin"))
        assets.srcDir(layout.buildDirectory.dir("generated/notices"))
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

// Writes the notices shown under Settings > Open source licenses (assets/licenses/).
val generateNotices by tasks.registering(Exec::class) {
    group = "build"
    description = "Collects the licence texts of every library in the app."
    val script = rootProject.file("scripts/gen-licenses.py")
    val out = layout.buildDirectory.dir("generated/notices").get().asFile
    inputs.file(script)
    inputs.file(rootProject.file("core/Cargo.lock"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    inputs.dir(rootProject.file("third_party"))
    outputs.dir(out)
    commandLine("python3", script.absolutePath, out.absolutePath)
}

tasks.named("preBuild") { dependsOn(buildRust, generateNotices) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // uniffi's Kotlin bindings call Rust through JNA.
    implementation(libs.jna) { artifact { type = "aar" } }
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot tests: Compose rendered on the JVM by Robolectric, captured by Roborazzi.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
