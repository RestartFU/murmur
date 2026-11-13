import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// sherpa-onnx runs Parakeet on-device. The AAR (~49 MB, all ABIs) is not committed;
// it is downloaded once from the upstream GitHub release and checked against a pinned SHA-256.
val sherpaOnnxVersion = "2.0.1"
val sherpaOnnxSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"

abstract class FetchVerifiedFile : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun fetch() {
        val out = output.get().asFile
        if (out.exists() && sha256Of(out) == sha256.get()) return
        out.parentFile.mkdirs()
        val tmp = File(out.parentFile, out.name + ".part")
        logger.lifecycle("Downloading ${url.get()}")
        URI(url.get()).toURL().openStream().use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        val actual = sha256Of(tmp)
        if (actual != sha256.get()) {
            tmp.delete()
            throw GradleException("SHA-256 mismatch for ${url.get()}: expected ${sha256.get()}, got $actual")
        }
        if (!tmp.renameTo(out)) throw GradleException("Could not move $tmp to $out")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val fetchSherpaOnnx = tasks.register<FetchVerifiedFile>("fetchSherpaOnnx") {
    url.set("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar")
    sha256.set(sherpaOnnxSha256)
    output.set(layout.projectDirectory.file("libs/sherpa-onnx-$sherpaOnnxVersion.aar"))
}

android {
    namespace = "com.restartfu.murmur"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.restartfu.murmur"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Phones are arm64. Debug builds also carry x86_64 for the emulator.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable APK.
            // Swap in a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    implementation(files(fetchSherpaOnnx.flatMap { it.output }))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
