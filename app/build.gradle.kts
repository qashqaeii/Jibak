plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ir.jibik.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.jibik.app"
        minSdk = 26
        targetSdk = 35
        // Bump for every distributable update; never change applicationId.
        versionCode = 7
        versionName = "0.1.6"
        // Direct distribution: no access to SMS inbox or background messages.
        buildConfigField("boolean", "BANK_SMS_AVAILABLE", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        // Only the public Jibik API URL is injected at build time.
        // Never compile the Arvan API key or any server secret into Android.
        val apiBase = providers.gradleProperty("JIBIK_API_BASE_URL").orNull.orEmpty()
        val escaped = apiBase.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "JIBIK_API_BASE_URL", "\"$escaped\"")
    }

    buildTypes {
        // Separate sandbox: can restore a user's encrypted backup while the
        // previous installed app and its private data remain completely intact.
        create("recoveryDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".recovery"
            versionNameSuffix = "-recovery"
            buildConfigField("boolean", "BANK_SMS_AVAILABLE", "false")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:finance"))
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("junit:junit:4.13.2")
    // Only in the isolated instrumentation APK; never open the real ledger with this factory yet.
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("net.zetetic:sqlcipher-android:4.20.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
