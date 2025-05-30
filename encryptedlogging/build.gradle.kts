import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.automatticPublish)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val secretProperties = loadPropertiesFromFile(file("../secret.properties"))

android {
    namespace = "com.automattic.encryptedlogging"
    compileSdk = 34

    android.buildFeatures.buildConfig = true

    defaultConfig {
        minSdk = 24
        buildConfigField(
            "String",
            "ENCRYPTION_KEY",
            "\"${secretProperties.getProperty("encryptionKey")}\""
        )
        buildConfigField("String", "APP_SECRET", "\"${secretProperties.getProperty("appSecret")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles.add(File("consumer-rules.pro"))
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

kotlin {
    explicitApi()
}

fun loadPropertiesFromFile(file: File): Properties {
    val properties = Properties()
    if (file.exists()) {
        file.inputStream().use { stream ->
            properties.load(stream)
        }
    }
    return properties
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    api(libs.androidx.room.ktx)
    implementation(libs.kotlin.coroutines)
    implementation(libs.volley)
    implementation(libs.wordpress.utils)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.assertj)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.coroutines.test)

    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}

project.afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])

                groupId = "com.automattic"
                artifactId = "encryptedlogging"
                // version is set by `publish-to-s3` plugin
            }
        }
    }
}
