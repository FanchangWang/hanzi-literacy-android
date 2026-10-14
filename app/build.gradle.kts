plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Regenerates app/src/main/assets/{draw_all,explanation_all,voice} from the
// cnchar-data checkout. The merged files are committed, so a clean clone
// builds without running this; it only refreshes them when the source moves.
val cncharDataDir: String =
    (findProperty("cncharDataDir") as String?)
        ?: "C:\\Users\\guyue\\code\\pupil-learning-tool\\cnchar-data\\cnchar-data"

val mergeAssets = tasks.register<Exec>("mergeAssets") {
    group = "build setup"
    description = "Merge cnchar-data per-char JSON into sharded assets."

    val outDir = layout.projectDirectory.dir("src/main/assets").asFile
    inputs.dir(File(cncharDataDir)).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outDir)

    commandLine(
        "uv", "run", "$rootDir/tools/merge_assets.py", cncharDataDir, outDir.absolutePath
    )
}

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

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
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

tasks.named("preBuild") { dependsOn(mergeAssets) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.houbb.pinyin)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}