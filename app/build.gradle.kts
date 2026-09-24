import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Tez reliz (R8 bilan siqilgan): faqat so'ralganda — `-Povoz.fastRelease=true`.
// Bayroqsiz hamma narsa avvalgidek: debug va sinovlar o'zgarmaydi.
val fastRelease = providers.gradleProperty("ovoz.fastRelease").orNull == "true"

android {
    namespace = "uz.ovozstudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.ovozstudio.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // V1 hali imzolanmagan debug build sifatida tarqatiladi,
            // shuning uchun standart holda minify o'chirilgan — xatolarni
            // topish osonroq.
            //
            // `-Povoz.fastRelease=true` bilan esa R8 yoqiladi: kod
            // optimallashtiriladi va ishlatilmagan qismlar tashlanadi. Debug
            // build ishlaydigan Compose ilovasi uchun eng sezilarli sekinlik
            // manbai — aynan optimallashtirilmagan kod, shuning uchun tez
            // reliz debug'dan sezilarli tez ishlaydi.
            isMinifyEnabled = fastRelease
            isShrinkResources = fastRelease
            if (fastRelease) {
                // SINOV uchun: debug kaliti bilan imzolanadi, ya'ni APK o'rnatiladi,
                // lekin do'kon yoki keyingi yangilash uchun yaroqsiz. Haqiqiy
                // reliz uchun o'zingizning kalitingiz kerak.
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Kutubxonalar (PDFBox, jump3r) o'z litsenziya fayllarini bir xil
            // nom bilan olib keladi; APK'ga bittasi ham kerak emas, takror
            // esa yig'ishni to'xtatadi («More than one file was found»).
            excludes += listOf(
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // MP3 kodlovchisi. Android'da MP3 uchun faqat DEKODER bor
    // (MediaCodec), kodlovchi yo'q — shuning uchun LAME'ning sof Java
    // porti ishlatiladi. Uning `mp3` paketi Android'ga bog'liq emas;
    // `lowlevel` o'rami esa `javax.sound.sampled` ga tayanadi va biz uni
    // ishlatmaymiz (proguard qoidasi bilan chiqarib tashlangan).
    implementation(libs.jump3r)

    // PDF: sahifalarni kesib olish, o'chirish va matnini o'qish. PDF'ni o'zimiz
    // tahlil qilmaymiz: fayllar juda xilma-xil (siqilgan obyekt oqimlari,
    // shifrlash, buzuq havolalar, murakkab shriftlar) va sinalgan kutubxona
    // ularning deyarli hammasini o'qiydi. PDFBox'ning Android porti, Apache-2.0.
    implementation(libs.pdfbox.android)

    debugImplementation(libs.androidx.ui.tooling)

    // Sof JVM testlari: WAV va vaqt mantiqi Android'siz tekshiriladi.
    testImplementation(libs.junit)
}
