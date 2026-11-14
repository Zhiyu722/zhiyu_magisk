package com.zhiyu.magisk.core

import android.app.Application
import android.content.Context
import com.zhiyu.magisk.StubApk
import com.zhiyu.magisk.core.utils.RootUtils

open class App() : Application() {

    // The stub instantiates this class with its own Data object, which is how the manager
    // learns that it is running inside the stub and where its real root service lives.
    constructor(o: Any) : this() {
        val data = StubApk.Data(o)
        data.classToComponent[RootUtils::class.java.name] = data.rootService.name
        data.rootService = RootUtils::class.java
        Info.stub = data
    }

    override fun attachBaseContext(context: Context) {
        if (context is Application) {
            AppContext.attachApplication(context)
        } else {
            super.attachBaseContext(context)
            AppContext.attachApplication(this)
        }
    }
}
