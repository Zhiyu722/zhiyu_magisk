plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.lsparanoid)
}

lsparanoid {
    seed = if (RAND_SEED != 0) RAND_SEED else null
    includeDependencies = true
    classFilter = { true }
}

android {
    namespace = "com.zhiyu.magisk"
    enableKotlin = false

    // Where the stub downloads the full manager app from once the user taps "install".
    //
    // This used to point at topjohnwu/Magisk, the upstream project. That is the wrong app for this
    // fork: it is a different package (com.topjohnwu.magisk), signed with a different key, so the
    // stub would have installed an unrelated manager instead of upgrading itself.
    //
    // The file name has to match what the release workflow publishes. That workflow names the APK
    // Magisk-<short version>-zhiyu(<versionCode>).apk, deriving the short version from the
    // versionCode (31000 -> 31); GitHub then rewrites the parentheses to dots as it uploads, so the
    // asset that actually resolves is Magisk-31-zhiyu.31000.apk.
    val versionCodeValue = Config.versionCode
    val minor = versionCodeValue % 1000
    val shortVersion = if (minor == 0) {
        "${versionCodeValue / 1000}"
    } else {
        "${versionCodeValue / 1000}." + minor.toString().padStart(3, '0').trimEnd('0')
    }
    val url = "https://github.com/Zhiyu722/zhiyu_magisk/releases/download/" +
        "v${Config.version}/Magisk-$shortVersion-zhiyu.$versionCodeValue.apk"

    defaultConfig {
        applicationId = "com.zhiyu.magisk"
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "APK_URL", "\"$url\"")
        buildConfigField("int", "STUB_VERSION", Config.stubVersion)
    }

    buildTypes {
        release {
            proguardFiles("proguard-rules.pro")
            isMinifyEnabled = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

setupStubApk()

dependencies {
    implementation(project(":shared"))
}
