plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.expensetracker"
        minSdk = 26 // java.time without desugaring
        targetSdk = 36
        // CI passes these in (run number / git tag); local builds keep the defaults.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0"
    }

    // Release signing comes from CI secrets (see README "CI/CD"). Without them the release
    // build is produced unsigned, so the pipeline still runs on forks and before setup.
    val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    val releaseSigning = if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = false // turn on (with R8 testing) before a store release
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        sarifReport = true // shown in GitHub's code-scanning tab by CI
        textReport = true
        textOutput = file("stdout") // so CI can surface lint errors as annotations
    }
}

ksp {
    // Exported schema JSON is what Room migration tests diff against; commit app/schemas.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
