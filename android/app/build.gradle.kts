import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Configuración local (NO se versiona): local.properties en la raíz del proyecto.
// Solo hace falta para «¿Qué ves?» desde el teléfono (inicio de sesión del operador).
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun local(key: String, default: String = "") = local.getProperty(key, default).trim()

android {
    namespace = "pe.edu.upn.llmia"
    compileSdk = 35
    defaultConfig {
        applicationId = "pe.edu.upn.llmia"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLOUD_ORIGIN",
            "\"${local("llmia.cloudOrigin", "https://llmia-demo-913797829604.us-central1.run.app")}\"")
        buildConfigField("String", "ROBOT_ID", "\"${local("llmia.robotId", "llmia-01")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${local("llmia.firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${local("llmia.firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${local("llmia.firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_WEB_CLIENT_ID", "\"${local("llmia.firebase.webClientId")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Inicio de sesión del operador (mismo Firebase que el panel web).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
