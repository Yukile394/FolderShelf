// app/build.gradle.kts
//
// NOT: AGP 9.0+ dahili (built-in) Kotlin destegi sundugu icin bu modulde
// "org.jetbrains.kotlin.android" eklentisi UYGULANMAZ. AGP bunu otomatik
// saglar. Ayrica Room/KSP gibi ek bir "annotation processing" eklentisi
// bilerek KULLANILMADI: bu proje CI'da (GitHub Actions) derlenecegi ve
// yerel olarak test edilemeyecegi icin, hizla degisen KSP/Kotlin surum
// eslesme risklerinden kacinmak amaciyla veri katmani hafif bir
// SharedPreferences + JSON deposu (ShelfStorage) uzerine kuruldu.
// Mimari yine de tam MVVM'dir (Repository -> ViewModel -> View).
plugins {
    id("com.android.application")
}

android {
    namespace = "com.yukile.foldershelf"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yukile.foldershelf"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePathEnv = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!keystorePathEnv.isNullOrBlank() && file(keystorePathEnv).exists()) {
                storeFile = file(keystorePathEnv)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val keystorePathEnv = System.getenv("RELEASE_KEYSTORE_PATH")
            val hasCustomSigning = !keystorePathEnv.isNullOrBlank() && file(keystorePathEnv).exists()
            signingConfig = if (hasCustomSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")

    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
