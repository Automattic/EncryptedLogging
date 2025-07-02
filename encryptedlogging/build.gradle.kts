import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.ksp)
    alias(libs.plugins.automattic.publish)
}

val secretProperties = loadPropertiesFromFile(file("../secret.properties"))

android {
    namespace = "com.automattic.encryptedlogging"
    compileSdk = 35

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    packaging {
        resources {
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
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
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.volley)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)

    implementation(libs.terl.lazysodium.android.get().toString()) // TODO: https://github.com/gradle/gradle/issues/21267
    implementation(libs.jna.get().toString()) // TODO: https://github.com/gradle/gradle/issues/21267
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

tasks.named("generateReleaseBuildConfig").configure {
    enabled = false
    println("✅ Disabled task: generateReleaseBuildConfig")
}
}
