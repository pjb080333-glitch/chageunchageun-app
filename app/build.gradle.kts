import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 릴리스 서명: 키와 비밀번호는 저장소 밖·gitignore 파일에만 둔다 (keystore.properties).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.chageunchageun.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chageunchageun.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // mock  : 차량 데이터를 시나리오 목업으로 대체. 일반 안드로이드 에뮬레이터에서도 실행·데모 영상 촬영 가능.
    // pleos : Pleos Vehicle SDK 실연동. Pleos Connect 에뮬레이터/실차에서 실행.
    flavorDimensions += "vehicle"
    productFlavors {
        create("mock") {
            dimension = "vehicle"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
        create("pleos") {
            dimension = "vehicle"
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
        buildConfig = true // 시연 자동화 인텐트를 debug 빌드에서만 받기 위해
    }

    // AAOS Car API(시동·주차 브레이크·주행 제한). pleos 플레이버 코드에서만 쓴다. 기기에서는 시스템 라이브러리로 제공.
    useLibrary("android.car")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pleos Vehicle SDK (공식 샘플과 같은 버전). pleos 플레이버에서만 사용.
    "pleosImplementation"("ai.pleos.playground:Vehicle:2.3.2")
    // NaviHelper (개발자 문서 importing-sdk 의 버전 그대로)
    "pleosImplementation"("ai.pleos.playground:NaviHelper:3.0.5")
    // TTS·STT: 데모(mock)도 Pleos 에뮬레이터에서 소리가 나도록 두 플레이버 공통. 음성 서비스가 없으면 안드로이드 TTS 로 전환.
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    testImplementation("junit:junit:4.13.2")
}
