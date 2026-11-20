plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Fester Signaturschlüssel aus GitHub-Secrets (siehe README). Nur mit immer demselben Schlüssel
// kann Android eine installierte App aktualisieren, statt sie neu installieren zu müssen.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")

// Versionscode steigt mit jedem GitHub-Build automatisch.
// Versionsname: Release-Builds genau die Version, alle anderen version.txt plus Build-Nummer.
val runNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val baseVersion: String = rootProject.file("version.txt").readText().trim()
val tagName: String? = System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF")?.startsWith("refs/tags/") == true }
    ?.takeIf { it.matches(Regex("[vV]?\\d+(\\.\\d+)*")) }
val releaseVersion: String? = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
    ?: tagName?.trimStart('v', 'V')

android {
    namespace = "de.simplescan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.simplescan.app"
        // Android 10+: Speichern in „Download“ ohne Speicherberechtigung (MediaStore)
        minSdk = 29
        // Nur die Prozessortypen echter Handys, damit OpenCV die APK nicht unnötig aufbläht.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        targetSdk = 35
        versionCode = if (runNumber != null) 100 + runNumber else 1
        versionName = releaseVersion ?: if (runNumber != null) "$baseVersion-build$runNumber" else baseVersion
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (signingKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            if (signingKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        // org.json kommt in Unit-Tests aus der echten Bibliothek (siehe testImplementation unten).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Versuch eigener Scanner: Kamera (CameraX) und Bildverarbeitung (OpenCV, ab Schritt 2 für die Seitenerkennung)
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("org.opencv:opencv:4.14.0")

    // Hochladen zu Nextcloud/WebDAV (eigene Methoden wie PROPFIND und MKCOL)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google ML Kit Dokumentenscanner: Kamera, Seitenerkennung, Zuschneiden, Filter, PDF.
    // Läuft über die Google-Play-Dienste, braucht keine Kameraberechtigung in der App.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
