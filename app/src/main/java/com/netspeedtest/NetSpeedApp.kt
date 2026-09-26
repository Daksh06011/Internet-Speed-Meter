package com.netspeedtest

import android.app.Application
import android.content.Context

class NetSpeedApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

val Context.graph: AppGraph get() = (applicationContext as NetSpeedApp).graph
