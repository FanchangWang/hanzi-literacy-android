plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The merged cnchar assets (draw_all, explanation_all, voice) are committed
// to git. To regenerate them after the source moves, run
//   uv run tools/merge_assets.py <cnchar-data-dir> app/src/main/assets
// manually; the build does not need this step.

android {
    namespace = "com.pupil.hanzi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pupil.hanzi"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Both variants sign with jks/key, committed on purpose; credentials are in
    // jks/readme.txt. One key for everything means a debug build installs over
    // a release install (and the other way round) instead of failing with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE, which is what the default per-machine
    // ~/.android/debug.keystore caused. The config is named "shared" rather
    // than "debug" because release uses it too, so AGP's implicit debug signing
    // config is left unused.
    //
    // storeType is stated even though the build also succeeds without it:
    // jks/key is PKCS12 (keytool reports PKCS12, the file starts 30 82), while
    // jks/readme.txt calls it "(jks)" and the name carries no extension, so
    // leaving the type implicit makes the load depend on the JVM default.
    //
    // Consequence of signing release with a committed key: anyone with a
    // checkout can sign an update, and losing jks/key means no future build can
    // be installed over an existing one. Fine for sideloading; Play App Signing
    // is the answer if this ever ships there.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("jks/key")
            storeType = "PKCS12"
            storePassword = "hanzi123"
            keyAlias = "hanzi"
            keyPassword = "hanzi123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            optimization {
                enable = true
                // No packageScope. Naming packages here switches R8 into
                // partial shrinking, and anything outside the named scope is
                // then treated as an external dependency. The release build
                // died on launch with
                //   IllegalAccessError: Class kotlin.sequences.d extended by
                //   class lg1 is inaccessible
                // because kotlinx.coroutines could no longer reach the Kotlin
                // stdlib. The default, "**", shrinks everything together and
                // keeps the runtime consistent.
            }
            // Custom keep rules. Even with full shrinking R8 drops
            // kotlin.sequences.SequenceScope (marked R8$$REMOVED$$CLASS in
            // mapping.txt) while Compose/coroutines still subclass it at
            // runtime, crashing on launch. See proguard-rules.pro.
            proguardFiles(file("proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hanlp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
