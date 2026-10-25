package com.mysmartring.data

enum class CardType { LATEST, TREND, SLEEP }

enum class CardRange(val days: Int, val label: String) {
    DAY(1, "日"),
    WEEK(7, "週"),
    MONTH(30, "月"),
}

/** 儀表板上的一張 Card。[metric] 為 Metric 名稱，睡眠 Card 固定為 "SLEEP"；[range] 只有趨勢 Card 用得到 */
data class CardConfig(val metric: String, val type: CardType, val range: CardRange = CardRange.DAY) {
    companion object {
        const val SLEEP = "SLEEP"
    }
}

/** 儀表板版面的編輯規則。都是純函式，回傳新清單 */
object DashboardLayout {
    /** 預設儀表板：心率、血氧各一張最新值與一張趨勢（日），加上一張睡眠 */
    val defaults = listOf(
        CardConfig("HEART_RATE", CardType.LATEST),
        CardConfig("HEART_RATE", CardType.TREND),
        CardConfig("BLOOD_OXYGEN", CardType.LATEST),
        CardConfig("BLOOD_OXYGEN", CardType.TREND),
        CardConfig(CardConfig.SLEEP, CardType.SLEEP),
    )

    /** 可新增的 Card 種類 */
    val available = listOf(
        CardConfig("HEART_RATE", CardType.LATEST),
        CardConfig("HEART_RATE", CardType.TREND),
        CardConfig("BLOOD_OXYGEN", CardType.LATEST),
        CardConfig("BLOOD_OXYGEN", CardType.TREND),
        CardConfig(CardConfig.SLEEP, CardType.SLEEP),
    )

    /** 把第 [index] 張往前（[delta] 為負）或往後移，超出範圍時不動 */
    fun move(cards: List<CardConfig>, index: Int, delta: Int): List<CardConfig> {
        val target = index + delta
        if (index !in cards.indices || target !in cards.indices) return cards
        return cards.toMutableList().apply { add(target, removeAt(index)) }
    }

    /** 刪除第 [index] 張；至少要留一張，只剩一張時不動 */
    fun remove(cards: List<CardConfig>, index: Int): List<CardConfig> =
        if (index !in cards.indices || cards.size <= 1) cards else cards.filterIndexed { i, _ -> i != index }

    fun add(cards: List<CardConfig>, card: CardConfig): List<CardConfig> = cards + card

    fun setRange(cards: List<CardConfig>, index: Int, range: CardRange): List<CardConfig> =
        if (index !in cards.indices || cards[index].type != CardType.TREND) cards
        else cards.mapIndexed { i, c -> if (i == index) c.copy(range = range) else c }
}
