plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Gateway-Basis-URL kommt NICHT hartkodiert in den Quelltext, sondern aus einer
// Gradle-Property oder Umgebungsvariable (CI-Secret). Der eingecheckte Default ist
// ein neutraler Loopback-Platzhalter, damit das oeffentliche Repo keine internen
// Netzdetails enthaelt. Reale Adresse: `-Pcockpit.gatewayUrl=...` oder
// COCKPIT_GATEWAY_URL im CI-Secret setzen.
// `takeIf { isNotBlank }`, damit ein gesetztes-aber-leeres Env (CI expandiert ein
// nicht definiertes Secret zu "") auf den Platzhalter zurueckfaellt statt eine
// leere URL zu backen. Oeffentliche CI-Builds bleiben bewusst auf dem Loopback-
// Platzhalter; die reale Adresse wird nur beim internen Build (mobile-api) gesetzt.
val gatewayUrl: String = (project.findProperty("cockpit.gatewayUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("COCKPIT_GATEWAY_URL")?.takeIf { it.isNotBlank() }
    ?: "http://127.0.0.1:8140"

val cockpitVersionCode: Int = (project.findProperty("cockpit.versionCode") as String?)?.toIntOrNull()
    ?: System.getenv("COCKPIT_VERSION_CODE")?.toIntOrNull()
    ?: 2

android {
    namespace = "de.djouhri.cockpit"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.djouhri.cockpit"
        minSdk = 28
        targetSdk = 35
        versionCode = cockpitVersionCode
        versionName = "2.0.$cockpitVersionCode"

        buildConfigField("String", "GATEWAY_URL", "\"$gatewayUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        // Vorhandene Warnungen sollen die CI nicht rot faerben; der HTML-Report
        // wird als Artefakt abgelegt und bleibt so sichtbar.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
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
        buildConfig = true
    }

    packaging {
        resources {
            // BouncyCastle (bcpkix/bcprov) liefert mehrfach identische META-INF-
            // Ressourcen; ohne Ausschluss bricht mergeDebugJavaResource mit
            // DuplicateRelativeFileException ab.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // DataStore
    implementation(libs.datastore.preferences)

    // CameraX + ML Kit (QR-Pairing-Scanner)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // PKCS#10-CSR-Bau fuer den geraetegebundenen Schluessel (AndroidKeyStore)
    implementation(libs.bouncycastle.pkix)

    // Lokale Zusatzbestaetigung (Biometrie/Geraete-PIN) fuer schreibende Aktionen
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    // Tests (JVM-Unit + Integration)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
