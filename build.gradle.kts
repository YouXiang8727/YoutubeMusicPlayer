// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // 多模組必須：讓 com.android.library 以「已知版本」進入 classpath，
    // 否則 AGP 9 fat-jar 會使其以未知版本存在，子模組無法透過 catalog 套用。
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
