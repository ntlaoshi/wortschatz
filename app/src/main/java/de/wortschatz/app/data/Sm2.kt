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

    // ---------------------------------------------------------------- 会话内调度

    /**
     * 会话内的一次评分（还没达成「连续认识 N 次」）。
     *
     * 不推进复习计划：一个词在几分钟内被评 3 次，若每次都用 SM-2 累乘间隔，
     * 会得到十几个工作日的荒谬结果。真正的时间安排留到 [onPass]。
     */
    fun inSession(current: Progress, now: Long): Progress =
        current.copy(state = 1, lastReview = now, due = now)

    /**
     * 本轮达成连对后，按这一轮的吃力程度决定下次复习时间。
     *
     * 设计依据：遗忘曲线的间隔应当反映「这次记住得有多牢」——
     * 顺畅通过的词遗忘得慢，可以拉远；反复不过的词说明编码很弱，必须尽快再见。
     *
     * @param fails 本轮里评「模糊/忘了」的次数，0 表示一口气连对通过
     */
    fun onPass(current: Progress, fails: Int, now: Long): Progress {
        val easeDelta = when {
            fails == 0 -> 0.12
            fails == 1 -> 0.0
            fails == 2 -> -0.15
            else -> -0.30
        }
        val ease = (current.ease + easeDelta).coerceIn(MIN_EASE, 3.0)
        val lapses = current.lapses + fails

        // 反复不过（3 次及以上）：当天内再抽一次，不进长期计划
        if (fails >= 3) {
            return current.copy(
                ease = ease,
                reps = 0,
                lapses = lapses,
                interval = 0,
                due = now + 10 * 60_000L,
                lastReview = now,
                state = 1
            )
        }

        val interval = if (current.interval <= 0) {
            // 首次通过：4 天 / 2 天 / 1 天
            when (fails) {
                0 -> 4
                1 -> 2
                else -> 1
            }
        } else {
            // 已有间隔：顺畅则放大，吃力则缩短
            val factor = when (fails) {
                0 -> 2.4
                1 -> 1.2
                else -> 0.6
            }
            max(1, (current.interval * factor).roundToInt()).coerceAtMost(365)
        }

        return current.copy(
            ease = ease,
            reps = current.reps + 1,
            lapses = lapses,
            interval = interval,
            due = now + interval * DAY_MS,
            lastReview = now,
            state = 2
        )
    }
}
