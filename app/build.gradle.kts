import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "np.com.siriri.keyboard"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "np.com.siriri.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uses keystore.properties when present; falls back to debug key in CI if absent.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")
}

// No third-party dependencies at all: pure Android framework + Kotlin stdlib.
dependencies {}

// ---- Rust core -------------------------------------------------------------
// Requires: rustup targets for Android, `cargo install cargo-ndk`, and
// ANDROID_NDK_HOME pointing at an installed NDK. Always built with --release:
// a debug-built Rust core would make typing noticeably slower.
val rustDir = rootProject.file("rust-core")
val jniOut = file("src/main/jniLibs")

val cargoNdkBuild = tasks.register<Exec>("cargoNdkBuild") {
    group = "rust"
    description = "Builds the Rust core for all Android ABIs into src/main/jniLibs"
    workingDir = rustDir
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64",
        "-o", jniOut.absolutePath,
        "build", "--release",
    )
    inputs.dir(rustDir.resolve("src"))
    inputs.file(rustDir.resolve("Cargo.toml"))
    outputs.dir(jniOut)
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(cargoNdkBuild) }
