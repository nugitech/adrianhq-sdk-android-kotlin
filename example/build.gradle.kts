plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adrianhq.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adrianhq.example"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // ./gradlew :example:installDebug -PadrianWorkspaceId=ws_… [-PadrianWidgetUrl=http://localhost:5181]
        buildConfigField("String", "ADRIAN_WORKSPACE_ID", "\"${providers.gradleProperty("adrianWorkspaceId").getOrElse("ws_your_workspace_id")}\"")
        buildConfigField("String", "ADRIAN_WIDGET_URL", "\"${providers.gradleProperty("adrianWidgetUrl").getOrElse("https://chat.adrianhq.co")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":adrian-chat"))
}
