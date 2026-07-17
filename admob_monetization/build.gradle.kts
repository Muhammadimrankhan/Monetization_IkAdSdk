import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
//    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.monetization.ikadplugin"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        buildConfig =true
        compose = true
    }
    publishing {
        singleVariant("release")
    }
}
group = "com.github.Muhammadimrankhan"
version = "0.1.21_meta"


afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Muhammadimrankhan"
                artifactId = "Monetization_IkAdSdk"
                version = "0.1.21_meta"
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.compose.runtime:runtime:1.11.4")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    //Text and Size-ing Libs
    implementation("com.intuit.sdp:sdp-android:1.1.1")
    implementation("com.intuit.ssp:ssp-android:1.1.1")
    implementation(libs.koin)

    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))

    // Add the dependency for the Analytics library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.ads.mediation:facebook:6.21.0.4")


}
