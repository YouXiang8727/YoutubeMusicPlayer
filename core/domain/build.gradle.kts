plugins {
    // core:domain 是純 Kotlin module（零 Android 依賴），故用 JVM plugin 而非 com.android.library。
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // 對齊 Android modules 的 JavaVersion.VERSION_11（bitcode 相容，供 feature 依賴）
    jvmToolchain(11)
}

dependencies {
    // 只依賴 coroutines-core（Flow/StateFlow）；不依賴 coroutines-android（無 Dispatchers.Main 需求）
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
