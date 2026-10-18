import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("BillDatabase") {
            packageName.set("dev.splitthebill.shared.db")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "dev.splitthebill.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }

        // Host tests execute commonTest on the JVM. iOS tests run on a Mac.
        withHostTest { }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        // Host tests are created by withHostTest and have no generated accessor.
        named("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.sqlite.jdbc)
            }
        }
    }
}
