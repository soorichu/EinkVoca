plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cloud.einknote.einkvoca"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cloud.einknote.einkvoca"
        minSdk = 28
        targetSdk = 37
        versionCode = 11
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.github.bumptech.glide:glide:4.16.0")
}