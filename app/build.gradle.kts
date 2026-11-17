plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.airtouch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.airtouch"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0-fase5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // O modelo do MediaPipe não deve ser comprimido no APK.
        noCompress += "task"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // CameraX
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // Serviço com ciclo de vida (câmera em segundo plano no modo global)
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")

    // MediaPipe Hand Landmarker (processamento 100% local)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    // Reconhecimento de escrita à mão (modelo baixado uma vez; depois roda offline)
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")

    testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------
// Baixa o modelo hand_landmarker.task (~8 MB) na primeira compilação.
// Se preferir, baixe manualmente e salve em app/src/main/assets/hand_landmarker.task
// URL: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
// ---------------------------------------------------------------------------
val handModelFile = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile

tasks.register("downloadHandModel") {
    onlyIf { !handModelFile.exists() }
    outputs.file(handModelFile)
    doLast {
        handModelFile.parentFile.mkdirs()
        val url = uri(
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        ).toURL()
        val tmp = File(handModelFile.parentFile, "hand_landmarker.task.part")
        url.openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(handModelFile)
    }
}

tasks.named("preBuild") {
    dependsOn("downloadHandModel")
}
