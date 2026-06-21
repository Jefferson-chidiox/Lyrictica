plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

fun readKeyFromFile(file: java.io.File, key: String): String? {
    if (!file.isFile) return null

    return file.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { rawLine ->
            val normalized = rawLine.removePrefix("export ").trim()
            val separatorIndex = normalized.indexOf('=')
            if (separatorIndex <= 0) {
                null
            } else {
                val lineKey = normalized.substring(0, separatorIndex).trim()
                if (lineKey != key) {
                    null
                } else {
                    normalized.substring(separatorIndex + 1)
                        .trim()
                        .trim('"')
                        .trim('\'')
                }
            }
        }
        .firstOrNull()
}

fun readConfigValue(key: String): String {
    return listOf(
        providers.environmentVariable(key).orNull,
        providers.gradleProperty(key).orNull,
        readKeyFromFile(rootProject.file(".env"), key),
        readKeyFromFile(rootProject.file("local.properties"), key)
    )
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?: ""
}

fun escapeBuildConfigValue(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

val workerBaseUrl = escapeBuildConfigValue(
    readConfigValue("WORKER_BASE_URL").ifBlank { "http://10.0.2.2:8787" }
)

android {
    namespace = "com.oss.euphoriae"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lyrictica"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WORKER_BASE_URL", "\"$workerBaseUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    filter {
        include("com.lyrictica.**")
        include("com.oss.euphoriae.**")
    }
    warnings {
        noStartupProfileRulesGenerated = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-ui-compose:1.10.1")

    implementation("androidx.navigation:navigation-runtime-ktx:2.9.6")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.soywiz.korge:korge:5.4.0")

    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
