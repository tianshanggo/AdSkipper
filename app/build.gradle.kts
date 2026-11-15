plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adskipper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adskipper.app"
        minSdk = 24          // Android 7.0+，dispatchGesture 需要 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 有意保持零第三方依赖：只用 Android 框架 API，离线也能编译，且不引入任何数据收集代码
}
