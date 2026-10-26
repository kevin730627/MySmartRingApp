package com.mysmartring

import android.app.Application
import androidx.room.Room
import com.mysmartring.ble.AndroidRingTransport
import com.mysmartring.ble.RingTransport
import com.mysmartring.data.AppDatabase
import com.mysmartring.data.DashboardCards
import com.mysmartring.data.UserSettings
import com.mysmartring.data.RingRepository

/** 手動注入的依賴容器，依賴很少，不引入 Hilt */
class AppContainer(application: Application) {
    val database: AppDatabase =
        Room.databaseBuilder(application, AppDatabase::class.java, "ring.db").build()
    val repository = RingRepository(database)
    val dashboardCards = DashboardCards(database)
    val settings = UserSettings(application)
    val transport: RingTransport = AndroidRingTransport(application)
}

class RingApp : Application() {
    val container by lazy { AppContainer(this) }
}
