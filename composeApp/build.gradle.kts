import org.gradle.kotlin.dsl.libs
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
//    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()
    
    sourceSets {
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // Networking - Desktop
            implementation(libs.ktor.client.java)

            // System Tray and Native Integration
            implementation(libs.jna)
            implementation(libs.jna.platform)

            // Database - Desktop
//            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

compose.desktop {
    application {
        mainClass = "now.link.periphyllidae.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Periphyllidae"
            packageVersion = "1.0.2"

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
