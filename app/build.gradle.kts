import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val telegramPrivate = Properties().apply {
    val privateFile = rootProject.file("telegram.local.properties")
    if (privateFile.isFile) privateFile.inputStream().use { load(it) }
}
val telegramIdText = telegramPrivate.getProperty("TELEGRAM_API_ID", "").trim()
val telegramHash = telegramPrivate.getProperty("TELEGRAM_API_HASH", "").trim()
require((telegramIdText.isEmpty() && telegramHash.isEmpty()) ||
    (telegramIdText.toIntOrNull()?.let { it > 0 } == true &&
        telegramHash.matches(Regex("[0-9a-fA-F]{32}")))) {
    "Configuración Telegram privada incompleta o inválida; revisá telegram.local.properties."
}
val telegramId = telegramIdText.toIntOrNull() ?: 0

android {
    namespace = "ar.com.miflix.client"
    compileSdk = 34
    defaultConfig {
        applicationId = "ar.com.miflix.client"
        minSdk = 26
        targetSdk = 34
        versionCode = 26
        versionName = "0.9.2-diagnostico"
        buildConfigField("int", "TELEGRAM_API_ID", telegramId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramHash\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // JNI precompilado de TDLib; la sesión se autoriza en el teléfono de cada usuario.
    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
