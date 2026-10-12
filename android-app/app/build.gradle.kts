import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fisioApiBaseUrl = (project.findProperty("FISIO_API_BASE_URL") as String?)
    ?: "http://192.168.1.52:8081"

android {
    namespace = "it.simosw.fisioesport"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.simosw.fisioesport"
        manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
        manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "FISIO_API_BASE_URL", "\"$fisioApiBaseUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

android.applicationVariants.configureEach {
    outputs.configureEach {
        (this as ApkVariantOutputImpl).outputFileName = "FisioESport.apk"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
