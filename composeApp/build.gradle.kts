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
    alias(libs.plugins.sqldelight)
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
            
            // Database - Android
            implementation(libs.sqldelight.android.driver)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            
            // Navigation
            implementation(libs.navigation.compose)
            
            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            
            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            
            // Database
            implementation(libs.sqldelight.runtime)
            
            // Logging
            implementation(libs.kermit)
            
            // Date/Time
            implementation(libs.kotlinx.datetime)
            
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
            implementation(libs.mediamp.vlc.compose)
            implementation(libs.mediamp.vlc.loader) // Automatically load VLC binaries
            
            // Database - Desktop
            implementation(libs.sqldelight.sqlite.driver)
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

// Configure development runs to use appResources for VLC binaries
tasks.withType<JavaExec> {
    systemProperty("compose.application.resources.dir", file("appResources").absolutePath)
}

sqldelight {
    databases {
        create("PeriphyllidaeDatabase") {
            packageName.set("now.link.periphyllidae.database")
        }
    }
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

            modules("java.net.http")
        }
    }
}
