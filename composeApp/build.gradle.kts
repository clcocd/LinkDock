import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("dev.hydraulic.conveyor") version "1.12"
}

group = "app.linkdock.desktop"
val appVersion = "1.5"
version = appVersion

val generateAppInfoSource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/appinfo")

    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile
        val packageDir = File(dir, "app/linkdock/desktop/app")
        packageDir.mkdirs()

        File(packageDir, "GeneratedAppInfo.kt").writeText(
            """
            package app.linkdock.desktop.app

            object GeneratedAppInfo {
                const val VERSION = "$appVersion"
            }
            """.trimIndent()
        )
    }
}


kotlin {
    jvm()
    jvmToolchain(22)
    
    sourceSets {
        jvmMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/appinfo"))
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

            // Conveyor API: Manage automatic updates.
            implementation("dev.hydraulic.conveyor:conveyor-control:1.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

val composeDesktopVersion = "1.10.2"

dependencies {
    macAarch64("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeDesktopVersion")
    windowsAmd64("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeDesktopVersion")
}

compose.desktop {
    application {
        mainClass = "app.linkdock.desktop.MainKt"

        nativeDistributions {
            windows{
                includeAllModules = true
            }
            macOS{
                includeAllModules = true
            }
        }
    }
}

// region Work around temporary Compose bugs.
configurations.all {
    attributes {
        // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}

tasks.named("compileKotlinJvm").configure {
    dependsOn(generateAppInfoSource)
}

tasks.register<Jar>("jar") {
    archiveBaseName.set("composeApp")
    from(kotlin.targets["jvm"].compilations["main"].output)

}

tasks.register<Exec>("convey") {
    val dir = layout.buildDirectory.dir("packages")
    outputs.dir(dir)
    commandLine("conveyor", "make", "--output-dir", dir.get(), "site")
    dependsOn("jar", "writeConveyorConfig")
}