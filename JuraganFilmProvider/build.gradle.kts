plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// use an integer for version numbers
version = 1

cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
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
        "AnimeMovie",
        "OVA",
        "Anime",
        "Movie",
        "Series",
        "AsianDrama",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://tv24.juragan.film&sz=%size%"
}

android {
    namespace = "com.juraganfilm"
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
