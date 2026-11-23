import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Huawei App ID comes from local.properties (huawei.appId=...), never from git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.wallwallfacer.yourhealthsync"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.wallwallfacer.yourhealthsync"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        val huaweiAppId = localProps.getProperty("huawei.appId", "MISSING_HUAWEI_APP_ID")
        manifestPlaceholders["huaweiAppId"] = huaweiAppId
        buildConfigField("String", "HUAWEI_APP_ID", "\"$huaweiAppId\"")
    }

    // Release signing: keystore path and password come from the environment, never from git.
    //   YHS_KEYSTORE=~/.android/yourhealthsync-release.jks
    //   YHS_KEYSTORE_PASSWORD=$(security find-generic-password -s yourhealthsync-release-keystore -w)
    val releaseKeystore = System.getenv("YHS_KEYSTORE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("YHS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YHS_KEY_ALIAS") ?: "yourhealthsync"
                keyPassword = System.getenv("YHS_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.huawei.health)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
