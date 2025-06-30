dependencies {
    val cloudstream by configurations
    val implementation by configurations
    
    // Use the same version as in the main project
    cloudstream("com.lagradost:cloudstream3:pre-release")
    
    // Other dependencies from main project
    implementation(kotlin("stdlib"))
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
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
    iconUrl = "https://anichin.cafe/favicon.ico"
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