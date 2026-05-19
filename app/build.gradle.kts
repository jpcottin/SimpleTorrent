plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jpcexample.simpletorrent"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "com.jpcexample.simpletorrent"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // BOOST_CMAKE_DIR env var lets CI override the Homebrew default path
                val boostDir = System.getenv("BOOST_CMAKE_DIR")
                    ?: "/opt/homebrew/lib/cmake/Boost-1.90.0"
                // BOOST_INCLUDE_DIR: injected via cppFlags (-I flag) rather than CMake
                // include_directories(), because CMake suppresses -I/usr/include on Linux
                // (it detects it as a host-compiler implicit directory and drops the flag).
                val boostInclude = System.getenv("BOOST_INCLUDE_DIR")
                    ?: "/opt/homebrew/include"
                cppFlags += "-I$boostInclude"
                arguments(
                    // libtorrent is in libs/libtorrent submodule — no path override needed
                    "-DBoost_DIR=$boostDir",
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
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
}
