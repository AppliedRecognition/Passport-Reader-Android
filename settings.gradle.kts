pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://maven.microblink.com") }
        maven { url = uri("https://mvnrepository.com/artifact/org.apache.commons/commons-math3") }
        maven {
            url = uri("https://maven.pkg.github.com/AppliedRecognition/Ver-ID-3D-Android-Libraries")
            credentials {
                username = settings.extra["gpr.user"] as String?
                password = settings.extra["gpr.token"] as String?
            }
        }
    }
}

rootProject.name = "Passport reader"
include(":mrtdreader", ":testapp")