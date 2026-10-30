package de.wortschatz.app.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 间隔重复算法（SuperMemo 2）。
 *
 * 核心思想：每次成功回忆后按难度系数（ease）延长复习间隔，
 * 回忆失败则重置间隔但不完全归零，避免挫败感过强。
 */
object Sm2 {

    private const val DAY_MS = 86_400_000L
    private const val MIN_EASE = 1.3

    /**
     * 根据本次评分计算新的进度。
     *
     * @param current 当前进度
     * @param rating  本次评分
     * @param now     当前时间戳（毫秒）
     */
    fun update(current: Progress, rating: Rating, now: Long = System.currentTimeMillis()): Progress {
        val q = rating.quality
        var ease = current.ease
        var reps = current.reps
        var laps = current.lapses
        var interval = current.interval

        // ease 更新公式：EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
        ease += 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)
        if (ease < MIN_EASE) ease = MIN_EASE

        if (q < 3) {
            // 未通过：重新进入学习队列，当天再过一遍
            reps = 0
            laps += 1
            interval = 0
            return current.copy(
                ease = ease,
                reps = reps,
                lapses = laps,
                interval = 0,
                due = now + 10 * 60_000L, // 10 分钟后重现
                lastReview = now,
                state = 1
            )
        }

        reps += 1
        interval = when (reps) {
            1 -> 1
            2 -> if (q >= 5) 3 else 2
            else -> max(1, (interval * ease).roundToInt())
        }
        // 单次间隔上限 365 天，避免长期不复习
        if (interval > 365) interval = 365

        return current.copy(
            ease = ease,
            reps = reps,
            lapses = laps,
            interval = interval,
            due = now + interval * DAY_MS,
            lastReview = now,
            state = 2
        )
    }
}
