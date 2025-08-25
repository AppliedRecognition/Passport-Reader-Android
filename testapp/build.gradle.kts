import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kapt)
}

android {
    namespace = "com.appliedrec.mrtd_reader_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.appliedrec.mrtd_reader_app"
        minSdk = 26
        targetSdk = 36
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            ndk {
                //noinspection ChromeOsAbiSupport
                abiFilters += setOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    lint {
        abortOnError = false
        disable += "NullSafeMutableLiveData"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    packaging {
        resources {
            // Pick one:
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // or, if you want to pick the first (rarely needed):
            // pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    api(project(":mrtdreader"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.android.material)
//    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation(libs.androidx.multidex)
    implementation(libs.commons.math3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room)
    implementation(libs.androidx.preference)
    implementation(libs.okhttp3)
    implementation(libs.kotlinx.serialization)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.verid.face.capture)
    implementation(libs.spoof.device.detection)
    implementation(libs.verid.face.detection)
    implementation(libs.verid.face.recognition.arcface)
    implementation(libs.verid.common.serialization)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
