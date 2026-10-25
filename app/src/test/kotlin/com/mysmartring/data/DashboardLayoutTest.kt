package com.mysmartring.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DashboardLayoutTest {
    private val a = CardConfig("HEART_RATE", CardType.LATEST)
    private val b = CardConfig("HEART_RATE", CardType.TREND)
    private val c = CardConfig(CardConfig.SLEEP, CardType.SLEEP)
    private val cards = listOf(a, b, c)

    @Test
    fun `往後移一格`() = assertEquals(listOf(b, a, c), DashboardLayout.move(cards, 0, 1))

    @Test
    fun `往前移一格`() = assertEquals(listOf(a, c, b), DashboardLayout.move(cards, 2, -1))

    @Test
    fun `移出範圍時不動`() {
        assertSame(cards, DashboardLayout.move(cards, 0, -1))
        assertSame(cards, DashboardLayout.move(cards, 2, 1))
        assertSame(cards, DashboardLayout.move(cards, 5, -1))
    }

    @Test
    fun `刪除指定的 Card`() = assertEquals(listOf(a, c), DashboardLayout.remove(cards, 1))

    @Test
    fun `至少保留一張`() {
        val one = listOf(a)
        assertSame(one, DashboardLayout.remove(one, 0))
    }

    @Test
    fun `新增加在最後，可重複加入同一種`() {
        assertEquals(listOf(a, b, c, a), DashboardLayout.add(cards, a))
    }

    @Test
    fun `只有趨勢 Card 能改時間範圍`() {
        assertEquals(CardRange.WEEK, DashboardLayout.setRange(cards, 1, CardRange.WEEK)[1].range)
        assertSame(cards, DashboardLayout.setRange(cards, 0, CardRange.WEEK))
    }

    @Test
    fun `預設儀表板依規格是心率與血氧各最新值與趨勢（日），加一張睡眠`() {
        val d = DashboardLayout.defaults
        assertEquals(5, d.size)
        assertEquals(true, d.filter { it.type == CardType.TREND }.all { it.range == CardRange.DAY })
        assertEquals(1, d.count { it.type == CardType.SLEEP })
    }
}
