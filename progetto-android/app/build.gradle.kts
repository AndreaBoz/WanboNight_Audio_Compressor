plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.andrea.wanbonight"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "it.andrea.wanbonight"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.2-prototype"
    }
    if (rootProject.file("../firma/wanbo-debug.jks").exists()) signingConfigs.getByName("debug") {
        storeFile = rootProject.file("../firma/wanbo-debug.jks")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
