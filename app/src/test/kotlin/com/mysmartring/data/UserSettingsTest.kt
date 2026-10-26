package com.mysmartring.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `沒設定過時用預設的每日步數目標`() {
        assertEquals(UserSettings.DEFAULT_STEP_GOAL, UserSettings(context).stepGoal.value)
    }

    @Test
    fun `設定後立即生效，重新建立後仍然保留`() {
        val settings = UserSettings(context)
        settings.setStepGoal(8_000)
        assertEquals(8_000, settings.stepGoal.value)
        assertEquals(8_000, UserSettings(context).stepGoal.value)
    }

    @Test
    fun `超出範圍的目標被拒絕，原本的目標不變`() {
        val settings = UserSettings(context)
        settings.setStepGoal(7_500)
        assertFailsWith<IllegalArgumentException> { settings.setStepGoal(0) }
        assertFailsWith<IllegalArgumentException> { settings.setStepGoal(100_001) }
        assertEquals(7_500, settings.stepGoal.value)
        assertEquals(7_500, UserSettings(context).stepGoal.value)
    }

    @Test
    fun `範圍的上下限都可以設定`() {
        val settings = UserSettings(context)
        settings.setStepGoal(UserSettings.STEP_GOAL_RANGE.first)
        assertEquals(1_000, settings.stepGoal.value)
        settings.setStepGoal(UserSettings.STEP_GOAL_RANGE.last)
        assertEquals(100_000, settings.stepGoal.value)
    }
}
