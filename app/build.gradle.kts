import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.screenshot)
}

// Boost paths resolve in this order: environment variable, then local.properties
// (gitignored, same place as sdk.dir — handy when building from the IDE where shell
// exports aren't visible), then the macOS Homebrew default. See README "Linux".
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun boostProperty(name: String, default: String): String =
    System.getenv(name) ?: localProperties.getProperty(name) ?: default

android {
    namespace = "com.jpcottin.simpletorrent"
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "com.jpcottin.simpletorrent"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // BOOST_CMAKE_DIR (env or local.properties) overrides the Homebrew default path
                val boostDir = boostProperty("BOOST_CMAKE_DIR",
                    "/opt/homebrew/lib/cmake/Boost-1.90.0")
                // BOOST_INCLUDE_DIR: passed to CMake as a cache var; used via
                // target_compile_options() in CMakeLists.txt so it bypasses both
                // CMake's implicit-include filtering AND configure-time feature checks.
                // On Linux this must be a Boost-only dir, never /usr/include (see CMakeLists.txt).
                val boostInclude = boostProperty("BOOST_INCLUDE_DIR",
                    "/opt/homebrew/include")
                arguments(
                    // libtorrent is in libs/libtorrent submodule — no path override needed
                    "-DBoost_DIR=$boostDir",
                    "-DBOOST_INCLUDE_DIR=$boostInclude",
                    "-Dencryption=OFF",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-Dbuild_tests=OFF",
                    "-Dbuild_examples=OFF",
                    "-Dbuild_tools=OFF",
                    "-Dpython-bindings=OFF"
                )
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Extended Material icons (Pause, PlayArrow, FolderOpen, Delete)
  implementation(libs.androidx.compose.material.icons.extended)

  // JSON serialization (for JNI data bridge)
  implementation(libs.kotlinx.serialization.json)

  // Media3 ExoPlayer for in-app video/audio playback
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)

  // Compose Preview Screenshot Testing
  screenshotTestImplementation(libs.screenshot.validation.api)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
