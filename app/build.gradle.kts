import java.util.Properties

// Release signing comes from keystore.properties (git-ignored, local) or environment variables
// (CI). Never commit the keystore or its passwords. Without either, builds are unsigned.
val keyProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String, env: String): String? = keyProps.getProperty(name) ?: System.getenv(env)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ambr.reloaded"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ambr.reloaded"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "3.0.3"
    }

    val storePath = secret("storeFile", "AMBR_KEYSTORE_FILE")
    val ambrSigning = storePath?.let { path ->
        signingConfigs.create("ambr") {
            storeFile = rootProject.file(path)
            storePassword = secret("storePassword", "AMBR_KEYSTORE_PASSWORD")
            keyAlias = secret("keyAlias", "AMBR_KEY_ALIAS")
            keyPassword = secret("keyPassword", "AMBR_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Pseudo-locales (en-XA accented, ar-XB right-to-left) catch untranslated text.
            isPseudoLocalesEnabled = true
            ambrSigning?.let { signingConfig = it }
            applicationIdSuffix = ".debug"
        }
        release {
            ambrSigning?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // No Google-only encrypted dependency metadata in the APK (IzzyOnDroid / F-Droid require this).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Lists every translated language (values-xx folders) in Android 13+'s per-app language setting.
    androidResources { generateLocaleConfig = true }

    // yt-dlp ships Python + FFmpeg as native libs that must be extracted on install.
    packaging { jniLibs.useLegacyPackaging = true }

    // One APK per CPU type keeps each download small (arm64 = nearly every modern phone).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation-graphics")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    // Cram Session: hardware re-encoding.
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")

    // Mind Meld: tiny HTTP server, QR codes, camera.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    implementation("androidx.palette:palette-ktx:1.1.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // yt-dlp for Android (the engine Seal uses).
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}
