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
val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
fun credential(environment: String, key: String): String =
    providers.environmentVariable(environment).orNull ?: localProperties.getProperty(key).orEmpty()
fun signingValue(environment: String, key: String): String =
    providers.environmentVariable(environment).orNull ?: signingProperties.getProperty(key).orEmpty()
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val telegramApiId = credential("TG_API_ID", "tg.api.id").toIntOrNull() ?: 0
val telegramApiHash = credential("TG_API_HASH", "tg.api.hash")
val tmdbApiKey = credential("TMDB_API_KEY", "tmdb.api.key")
val releaseStorePath = signingValue("RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
val splitApks = providers.gradleProperty("splitApks").orNull == "true"
val supportedAbis = listOf("arm64-v8a", "x86_64")

android {
    namespace = "com.aeswox.arcplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aeswox.arcplayer"
        minSdk = 31
        targetSdk = 35
        versionCode = releaseVersion.getProperty("VERSION_CODE").toInt()
        versionName = releaseVersion.getProperty("VERSION_NAME")

        if (!splitApks) {
            ndk { abiFilters += supportedAbis }
        }

        buildConfigField("int", "TG_API_ID", telegramApiId.toString())
        buildConfigField("String", "TG_API_HASH", quoted(telegramApiHash))
        buildConfigField("String", "TMDB_API_KEY", quoted(tmdbApiKey))
    }

    splits {
        abi {
            isEnable = splitApks
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        // Universal splits otherwise include every ABI bundled in the native dependencies.
        jniLibs.excludes += setOf("lib/armeabi-v7a/**", "lib/x86/**")
    }

    signingConfigs {
        create("release") {
            if (releaseStorePath.isNotBlank()) storeFile = rootProject.file(releaseStorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

val validateReleaseInputs by tasks.registering {
    doLast {
        check(telegramApiId > 0 && telegramApiHash.isNotBlank() && tmdbApiKey.isNotBlank()) {
            "Release builds require TG_API_ID, TG_API_HASH and TMDB_API_KEY (or local.properties)."
        }
        check(releaseStorePath.isNotBlank() && rootProject.file(releaseStorePath).isFile &&
            releaseStorePassword.isNotBlank() && releaseKeyAlias.isNotBlank() &&
            releaseKeyPassword.isNotBlank()) {
            "Release builds require a private signing key. Configure signing.properties or RELEASE_* environment variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseInputs)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
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
