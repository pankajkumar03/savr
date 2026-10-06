plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// AdMob IDs: put your real ones in gradle.properties (ADMOB_APP_ID, ADMOB_BANNER_ID, ADMOB_INTERSTITIAL_ID).
// Debug builds always use Google's test IDs - never click your own live ads.
object TestAds { const val APP = "ca-app-pub-3940256099942544~3347511713"; const val BANNER = "ca-app-pub-3940256099942544/9214589741"; const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712" }
fun prop(k: String, fallback: String) = (project.findProperty(k) as String?)?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "com.savr.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.savr.app"
        minSdk = 26; targetSdk = 35
        versionCode = 1; versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"${prop("API_BASE_URL", "https://api.example.com/")}\"") // backend: set API_BASE_URL in gradle.properties
        buildConfigField("String", "PRIVACY_URL", "\"https://example.com/privacy\"") // <- your privacy policy (required by Play + AdMob)
        buildConfigField("String", "TERMS_URL", "\"https://example.com/terms\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"support@example.com\"")
        manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID", TestAds.APP)
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("ADMOB_BANNER_ID", TestAds.BANNER)}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${prop("ADMOB_INTERSTITIAL_ID", TestAds.INTERSTITIAL)}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = TestAds.APP
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${TestAds.BANNER}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${TestAds.INTERSTITIAL}\"")
        }
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0"); implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0"); implementation("io.coil-kt:coil-video:2.7.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1"); implementation("androidx.media3:media3-ui:1.4.1")
}
