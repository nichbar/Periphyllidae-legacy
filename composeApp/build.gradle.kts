import jdk.tools.jlink.resources.plugins
import org.gradle.kotlin.dsl.libs
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
//    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            
            // Networking - Android
            implementation(libs.ktor.client.okhttp)
            
            // MediaMP - ExoPlayer backend for Android
            implementation(libs.mediamp.exoplayer)
            
            // Media Session & Notifications
            implementation(libs.androidx.media)
            implementation(libs.androidx.core.ktx)
            
            // Database - Android
//            implementation(libs.sqldelight.android.driver)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            
            // Navigation
//            implementation(libs.navigation.compose)
            
            // Decompose
            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            
            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            
            // Cache Server
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            
            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            
            // Database
//            implementation(libs.sqldelight.runtime)
            
            // Logging
            implementation(libs.kermit)
            
            // Date/Time
            implementation(libs.kotlinx.datetime)
            
            // I/O
            implementation(libs.okio)
            implementation(libs.kache.file.kache)
            
            // Image Loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            
            // Settings
            implementation(libs.multiplatform.settings)
            
            // MediaMP - Common API
            implementation(libs.mediamp.api)
            implementation(libs.mediamp.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            
            // Networking - Desktop
            implementation(libs.ktor.client.java)
            
            // MediaMP - VLC backend for JVM
            implementation(libs.mediamp.vlc)
            // implementation(libs.mediamp.vlc.compose) // Temporarily disabled due to compilation issues
            implementation(libs.mediamp.vlc.loader) // Automatically load VLC binaries
            
            // System Tray and Native Integration
            implementation(libs.jna)
            implementation(libs.jna.platform)
            
            // Database - Desktop
//            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "now.link.periphyllidae"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "now.link.periphyllidae"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "now.link.periphyllidae.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "now.link.periphyllidae"
            packageVersion = "1.0.0"

            // Configure appResources for VLC binaries
            appResourcesRootDir.set(file("appResources"))

            // Configure icons
            macOS {
                iconFile.set(file("src/jvmMain/resources/icons/periphyllidae_icon.icns"))
            }
            windows {
                iconFile.set(file("src/jvmMain/resources/icons/periphyllidae_icon.ico"))
            }
            linux {
                iconFile.set(file("src/jvmMain/resources/icons/periphyllidae_icon.png"))
            }

            modules("java.net.http")
        }
    }
}
