plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Gunakan integer untuk nomor versi
version = 1

cloudstream {
    //description = "Ekstensi Anichin untuk Kuramanime"
    authors = listOf("Ufsean")

    /**
    * Status integer dengan nilai sebagai berikut:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 3 // Awalnya di set ke beta

    // Jenis konten yang didukung
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")

    requiresResources = false
    language = "id"
    iconUrl = "https://animefreak.biz/avicon.ico"
}

android {
    buildFeatures {
        buildConfig = false
        viewBinding = false
    }
    
    // Nonaktifkan fitur yang tidak diperlukan
    dataBinding {
        enable = false
    }
    
    // Nonaktifkan fitur yang tidak diperlukan
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}