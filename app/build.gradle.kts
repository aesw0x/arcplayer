import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(key: String): String = (localProperties.getProperty(key) ?: "").replace("\"", "\\\"")

android {
    namespace = "com.aeswox.arcplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aeswox.arcplayer"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // TDLib ships ~22 MB of native code per ABI; these are the only two we run on.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        buildConfigField("int", "TG_API_ID", localProp("tg.api.id").ifEmpty { "0" })
        buildConfigField("String", "TG_API_HASH", "\"${localProp("tg.api.hash")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${localProp("tmdb.api.key")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Official VideoLAN LibVLC Android engine
    implementation("org.videolan.android:libvlc-all:3.6.0")

    // Frosted Jelly glass: Haze backdrop blur on API 34+ (LayerBackdrop covers 31-33)
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // TDLib (Boost-licensed official Telegram client library) with prebuilt Android JNI
    implementation("com.github.tdlibx:td:1.8.56")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
