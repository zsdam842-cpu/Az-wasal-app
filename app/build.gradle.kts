plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wasal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wasal.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "2.3"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // تشغيل الفيديو داخل المحادثة وبشاشة كاملة
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // WebRTC الحقيقي للمكالمات الصوتية والمرئية (مُحزَّم من Maven Central).
    implementation("io.github.webrtc-sdk:android:125.6422.07")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

android {
    signingConfigs {
        create("release") {
            // نفس مفتاح التوقيع الافتراضي الذي يوقّع نسخة debug، حتى تُثبَّت
            // نسختا debug و release فوق بعضهما على الهاتف دون حذف التطبيق.
            val defaultStore = File(
                System.getProperty("user.home") ?: ".",
                ".android/debug.keystore"
            )

            val storeFileEnv = System.getenv("WASAL_KEYSTORE")
            storeFile = if (!storeFileEnv.isNullOrBlank()) {
                File(storeFileEnv)
            } else {
                defaultStore
            }

            storePassword = System.getenv("WASAL_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("WASAL_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("WASAL_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
