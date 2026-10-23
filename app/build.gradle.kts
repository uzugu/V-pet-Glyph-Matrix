plugins {
    id("com.chaquo.python")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.digimon.glyph"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.digimon.glyph"
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        targetSdk = 35
        versionCode = 8
        versionName = "1.4-dpower.preview4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.caverock:androidsvg-aar:1.4")
}

chaquopy { defaultConfig { version = "3.11" } }

// Keep Android's standard build output and provide a versioned download artifact.
tasks.register<Copy>("packageVersionedDebug") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) { include("app-debug.apk") }
    into(layout.buildDirectory.dir("outputs/distributions"))
    rename { "V-Pet-Glyph-${android.defaultConfig.versionName}-debug.apk" }
}
