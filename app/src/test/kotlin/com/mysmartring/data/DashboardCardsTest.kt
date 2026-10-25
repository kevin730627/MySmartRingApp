package com.mysmartring.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardCardsTest {
    private lateinit var db: AppDatabase
    private lateinit var store: DashboardCards

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        store = DashboardCards(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `第一次寫入預設版面，再呼叫不會覆蓋使用者的修改`() = runTest {
        store.ensureDefaults()
        assertEquals(DashboardLayout.defaults, store.cards.first())

        store.edit { DashboardLayout.remove(it, 0) }
        store.ensureDefaults()
        assertEquals(DashboardLayout.defaults.drop(1), store.cards.first())
    }

    @Test
    fun `排序與時間範圍的修改會保存`() = runTest {
        store.ensureDefaults()
        store.edit { DashboardLayout.move(it, 4, -4) }
        store.edit { DashboardLayout.setRange(it, 2, CardRange.MONTH) }

        val cards = store.cards.first()
        assertEquals(CardType.SLEEP, cards[0].type)
        assertEquals(CardRange.MONTH, cards[2].range)
        assertEquals(5, cards.size)
    }

    @Test
    fun `無法辨識的舊資料被略過`() = runTest {
        db.dashboardCardDao().insertAll(
            listOf(
                DashboardCardEntity(metric = "HEART_RATE", cardType = "FUTURE_TYPE", range = "DAY", position = 0),
                DashboardCardEntity(metric = "HEART_RATE", cardType = "LATEST", range = "DAY", position = 1),
            ),
        )
        assertEquals(listOf(CardConfig("HEART_RATE", CardType.LATEST)), store.cards.first())
    }
}
