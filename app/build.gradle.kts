plugins {
    id("com.android.application")
}

android {
    namespace = "com.titan2.toolkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.adam_ikari.titan2toolkit"
        minSdk = 31
        targetSdk = 36
        versionCode = 7
        versionName = "0.5.0"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
