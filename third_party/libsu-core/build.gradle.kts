plugins { id("com.android.library") }
android {
    namespace = "com.topjohnwu.superuser"
    compileSdk = 36
    defaultConfig { minSdk = 19; consumerProguardFiles("proguard-rules.pro") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies { compileOnly("androidx.annotation:annotation:1.6.0") }
