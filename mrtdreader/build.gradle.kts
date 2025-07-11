import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kapt)
    alias(libs.plugins.dokka)
    id("kotlin-parcelize")
    `maven-publish`
    signing
}

android {
    namespace = "com.appliedrec.mrtdreader"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = false
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
//    implementation(fileTree(dir: "libs", include: ["*.jar"]))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.jmrtd)
    implementation(libs.scuba.sc.android)
    implementation(libs.spongycastle)
    implementation(libs.kotlinx.serialization)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
//    implementation(libs.rxjava)
//    implementation(libs.rxandroid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("MRTD Reader")
    moduleVersion.set(project.version.toString())
    outputDirectory.set(file("../docs"))

    dokkaSourceSets.configureEach {
        suppressedFiles.from(file("src/main/java/jj2000"))
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "com.appliedrec"
            artifactId = "mrtd-reader"
            version = "3.0.1"

            pom {
                name.set("MRTD Reader")
                description.set("Scans NFC chips in machine readable travel documents (MRTDs) like passports")
                url.set("https://github.com/AppliedRecognition/Passport-Reader-Android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/AppliedRecognition/Passport-Reader-Android.git")
                    developerConnection.set("scm:git:ssh://github.com/AppliedRecognition/Passport-Reader-Android.git")
                    url.set("https://github.com/AppliedRecognition/Passport-Reader-Android")
                }
                developers {
                    developer {
                        id.set("appliedrecognition")
                        name.set("Applied Recognition Corp.")
                        email.set("support@appliedrecognition.com")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/AppliedRecognition/Ver-ID-3D-Android-Libraries")
            credentials {
                username = project.findProperty("gpr.user") as String?
                password = project.findProperty("gpr.token") as String?
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}