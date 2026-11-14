plugins {
    alias(libs.plugins.android.application)
}

setupCommon()

android {
    namespace = "com.zhiyu.magisk"
    enableKotlin = false

    buildTypes {
        release {
            isShrinkResources = false
        }
    }
}
