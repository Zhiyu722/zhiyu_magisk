package com.zhiyu.magisk.core

import android.app.Application
import android.content.Context

open class App : Application() {

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(context)
        AppContext.attachApplication(this)
    }
}
