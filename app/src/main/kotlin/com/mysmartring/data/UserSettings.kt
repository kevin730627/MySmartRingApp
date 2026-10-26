package com.mysmartring.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 使用者偏好。只有幾個簡單的值，用 SharedPreferences 存，不放進資料庫。
 * 這些值不在資料庫備份裡，還原資料庫後需要重新設定。
 */
class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _stepGoal = MutableStateFlow(prefs.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL).coerceIn(STEP_GOAL_RANGE))
    /** 預期每日步數 */
    val stepGoal: StateFlow<Int> = _stepGoal

    /** 超出 [STEP_GOAL_RANGE] 的值會被拒絕（丟出 [IllegalArgumentException]），原本的目標不變 */
    fun setStepGoal(goal: Int) {
        require(goal in STEP_GOAL_RANGE) { "每日步數目標必須介於 ${STEP_GOAL_RANGE.first} 到 ${STEP_GOAL_RANGE.last}" }
        prefs.edit().putInt(KEY_STEP_GOAL, goal).apply()
        _stepGoal.value = goal
    }

    companion object {
        const val DEFAULT_STEP_GOAL = 10_000
        val STEP_GOAL_RANGE = 1_000..100_000
        private const val FILE = "settings"
        private const val KEY_STEP_GOAL = "daily_step_goal"
    }
}
