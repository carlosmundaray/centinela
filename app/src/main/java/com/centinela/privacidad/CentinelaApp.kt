package com.centinela.privacidad

import android.app.Application
import com.centinela.privacidad.data.SensorRepository

class CentinelaApp : Application() {
    lateinit var repository: SensorRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SensorRepository(this)
    }
}
