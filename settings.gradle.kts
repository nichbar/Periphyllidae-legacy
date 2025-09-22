rootProject.name = "Periphyllidae"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

// Include MediaMP submodule
includeBuild("mediamp") {
    dependencySubstitution {
        substitute(module("org.openani.mediamp:mediamp-api")).using(project(":mediamp-api"))
        substitute(module("org.openani.mediamp:mediamp-compose")).using(project(":mediamp-compose"))
        substitute(module("org.openani.mediamp:mediamp-exoplayer")).using(project(":mediamp-exoplayer"))
        substitute(module("org.openani.mediamp:mediamp-vlc")).using(project(":mediamp-vlc"))
        // substitute(module("org.openani.mediamp:mediamp-vlc-compose")).using(project(":mediamp-ui"))
        substitute(module("org.openani.mediamp:mediamp-vlc-loader")).using(project(":mediamp-vlc-loader"))
    }
}