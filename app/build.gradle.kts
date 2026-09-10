import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val apkDisplayName = "零跑智控"
val apkVersionName = "3.1.12"

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String? =
    localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStorePath = localProperty("RELEASE_STORE_FILE")
val releaseStorePassword = localProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProperty("RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

android {
    namespace = "com.leapauto.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.leapauto.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3001012
        versionName = apkVersionName
        buildConfigField("String", "AMAP_WEB_KEY", "\"${localProperty("AMAP_WEB_KEY") ?: "468e462adad376c2aa08d252ae20fcba"}\"")
        resourceConfigurations += setOf("zh", "zh-rCN")
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.github.chuckerteam.chucker:library:4.1.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.1.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

fun registerApkCopyTask(buildType: String) {
    tasks.register<Copy>("package${buildType.replaceFirstChar { it.uppercase() }}Apk") {
        group = "distribution"
        description = "Builds the $buildType APK."
        dependsOn("assemble${buildType.replaceFirstChar { it.uppercase() }}")
        from(layout.buildDirectory.dir("outputs/apk/$buildType")) {
            include("*.apk")
        }
        into(layout.buildDirectory.dir("distributions/$buildType"))
        rename { "$apkDisplayName-$apkVersionName.apk" }
    }
}

registerApkCopyTask("debug")
registerApkCopyTask("release")

tasks.register("packageApks") {
    group = "distribution"
    description = "Compatibility alias that builds only the signed Release delivery APK."
    dependsOn("packageReleaseApk")
}
