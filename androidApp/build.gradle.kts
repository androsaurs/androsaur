plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.hram.androsaur"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.hram.androsaur"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "0.12.0-medication-bubble"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("app.rive:rive-android:10.0.5")
    implementation("com.github.espressif:esp-idf-provisioning-android:lib-2.4.3") {
        // Only BLETransport, Session and Security2 are used. The agent never opens a camera.
        exclude(group = "com.github.yuriy-budiyev")
        exclude(group = "com.google.android.gms", module = "play-services-vision")
        exclude(group = "androidx.camera")
        exclude(group = "com.google.mlkit")
    }
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
}
