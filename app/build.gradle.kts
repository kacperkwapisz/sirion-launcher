import java.util.Properties

plugins {
    id("com.android.application")
}

// Versions live in version.properties so the release script can bump them.
val versions = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "pl.perier.sirion"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.perier.sirion"
        // The head unit runs Android 8.1 (API 27) and nothing else.
        minSdk = 27
        targetSdk = 27
        versionCode = versions.getProperty("versionCode").toInt()
        versionName = versions.getProperty("versionName")
    }

    // The release key lives outside the repo (~/.android/sirion-release.jks);
    // its path and password come from ~/.gradle/gradle.properties. Every OTA
    // update must be signed with it, or Android refuses to install it.
    signingConfigs {
        create("release") {
            val keystore = providers.gradleProperty("SIRION_KEYSTORE").orNull
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("SIRION_KEYSTORE_PASSWORD").get()
                keyAlias = "sirion"
                keyPassword = providers.gradleProperty("SIRION_KEYSTORE_PASSWORD").get()
            }
        }
    }

    lint {
        // Never published on Google Play; the only target is the unit's Android 8.1.
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
