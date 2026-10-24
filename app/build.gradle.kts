plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mysmartring"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mysmartring"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // 與 ring-protocol 共用同一份側錄回放資料
    sourceSets["test"].resources.srcDir("../ring-protocol/src/test/resources")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // 匯出結構描述，之後做資料庫遷移時才有依據
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":ring-protocol"))

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation(kotlin("test"))
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
