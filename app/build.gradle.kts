plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.monetization.ikadplugin"

    compileSdk=36

    defaultConfig {
//        applicationId = "com.monetization.ikadplugin"
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig =true
        resValues =true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
//    kotlinOptions {
//        jvmTarget = JavaVersion.VERSION_17.toString()
//    }
}
group = "com.github.Muhammadimrankhan"
version = "1.0"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "Monetization_IkAdSdk"
                version = project.version.toString()
            }
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.gms:play-services-ads:25.0.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation(libs.billing.ktx)


    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.perf)

    //Text and Size-ing Libs
    implementation(libs.ssp.android)
    implementation(libs.sdp.android)
//
//    //liftoff mediation
//    implementation("com.google.ads.mediation:vungle:7.7.1.0")
//
//    // facebook
//    implementation("com.google.ads.mediation:facebook:6.21.0.1")
//
//    // mintegral mediation
//    implementation("com.google.ads.mediation:mintegral:17.0.91.0")
}