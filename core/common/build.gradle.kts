plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.youxiang8727.mymediaplayer.core.common"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)
}
