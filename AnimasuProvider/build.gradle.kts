plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// use an integer for version numbers
version = 1

cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    description = "Ekstensi Kuramanime untuk CloudStream"
    authors = listOf("Ufsean")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 3 // will be 3 if unspecified
    tvTypes = listOf(
        "Anime",
        "AnimeMovie",
        "OVA",
        "Donghua"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=v1.animasu.top&sz=%size%"
}

android {
    namespace = "com.animasu"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // No specific dependencies for now
}
