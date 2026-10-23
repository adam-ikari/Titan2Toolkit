plugins {
    id("com.android.application")
}

android {
    namespace = "com.titan2.toolkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.titan2.toolkit"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // Keep the libxposed module descriptors intact.
            excludes += "META-INF/xposed/native_init.list"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
}
