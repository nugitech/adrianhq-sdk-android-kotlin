plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

// JitPack sets GROUP and VERSION from the repository and tag it builds.
group = System.getenv("GROUP") ?: "com.adrianhq"
version = System.getenv("VERSION") ?: "0.1.0"

android {
    namespace = "com.adrianhq.chat"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.15.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "adrian-chat"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Adrian Chat")
                description.set("Adrian live chat for Android apps: a full-screen messenger with forms, action buttons, chat flows and files, plus an unread badge.")
                url.set("https://github.com/nugitech/adrianhq-sdk-android-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/nugitech/adrianhq-sdk-android-kotlin")
                    connection.set("scm:git:https://github.com/nugitech/adrianhq-sdk-android-kotlin.git")
                }
            }
        }
    }
}
