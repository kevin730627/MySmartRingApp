package com.mysmartring.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 儀表板版面的儲存。資料表是空的時候（第一次開啟）會寫入預設版面 */
class DashboardCards(private val db: AppDatabase) {
    private val dao get() = db.dashboardCardDao()

    val cards: Flow<List<CardConfig>> = dao.observeAll().map { rows -> rows.mapNotNull(::toConfig) }

    suspend fun ensureDefaults() {
        db.withTransaction { if (dao.all().isEmpty()) write(DashboardLayout.defaults) }
    }

    /** 讀出目前版面、套用 [edit]、寫回，三步在同一個交易內 */
    suspend fun edit(edit: (List<CardConfig>) -> List<CardConfig>) {
        db.withTransaction { write(edit(dao.all().mapNotNull(::toConfig))) }
    }

    private suspend fun write(cards: List<CardConfig>) {
        dao.deleteAll()
        dao.insertAll(cards.mapIndexed { i, c -> DashboardCardEntity(metric = c.metric, cardType = c.type.name, range = c.range.name, position = i) })
    }

    /** 無法辨識的舊資料略過，不讓整個儀表板壞掉 */
    private fun toConfig(e: DashboardCardEntity): CardConfig? {
        val type = CardType.entries.firstOrNull { it.name == e.cardType } ?: return null
        val range = CardRange.entries.firstOrNull { it.name == e.range } ?: CardRange.DAY
        return CardConfig(e.metric, type, range)
    }
}
