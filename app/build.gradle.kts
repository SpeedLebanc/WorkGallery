plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.workgallery"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.workgallery"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // Ensure 16KB page alignment for Android 15. 确保 Android 15 的 16KB 页面对齐。
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Keep all your existing auto-generated libraries here. 将所有现有的自动生成的库保留在这里。
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // ... keep the test implementations as well ... // ... 也保留测试实现 ...
    // debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Google Media3 (ExoPlayer) 依赖，用于现代视频播放、进度条和倍速控制
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Add the CameraX version variable here. 在此处添加 CameraX 版本变量。
    //val camerax_version = "1.4.0-alpha04"
    val camerax_version = "1.4.0" // Or use "1.5.0-alpha01" if necessary. 或者如果需要，使用 "1.5.0-alpha01"。

    implementation("androidx.camera:camera-video:${camerax_version}")

    // Core camera functionality. 核心相机功能。
    implementation("androidx.camera:camera-core:${camerax_version}")

    // Add this line to download the extended Material icons, including CameraAlt. 添加这行代码以下载扩展的 Material 图标，包括 CameraAlt。
    implementation("androidx.compose.material:material-icons-extended")

    // Camera2 API integration for hardware control. 用于硬件控制的 Camera2 API 集成。
    implementation("androidx.camera:camera-camera2:${camerax_version}")

    // Lifecycle binding to automatically manage camera states. 自动管理相机状态的生命周期绑定。
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")

    // CameraX View class for the UI preview screen. 用于 UI 预览屏幕的 CameraX View 类。
    implementation("androidx.camera:camera-view:${camerax_version}")

    implementation("androidx.fragment:fragment-ktx:1.6.2")
}