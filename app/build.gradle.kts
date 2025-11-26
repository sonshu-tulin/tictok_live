plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bytedance.tictok_live"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.bytedance.tictok_live"
        minSdk = 23
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 布局
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // constraintlayout布局

    implementation("androidx.appcompat:appcompat:1.6.1") // appcompat
    implementation("androidx.recyclerview:recyclerview:1.3.2") //recyclerView

    // 播放器
    implementation("androidx.media3:media3-exoplayer:1.2.1") // 核心播放器
    implementation("androidx.media3:media3-ui:1.2.1") // 播放器控件
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1") // DASH 直播支持（.mpd 格式）

    // retrofit
    implementation("com.squareup.okhttp3:okhttp:4.12.0") //okhttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0") //retrofit
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // gson
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") //日志拦截器

    // 加载网络头像
    implementation("com.github.bumptech.glide:glide:4.16.0")
}