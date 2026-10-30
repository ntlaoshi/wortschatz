package de.wortschatz.app.data

/** 一条词条。word 为展示形式（名词含定冠词），lemma 为检索用词根。 */
data class Word(
    val id: Long = 0,
    val word: String,
    val lemma: String,
    val pos: String,
    val gender: String?,
    val plural: String?,
    val zh: String,
    val en: String?,
    val example: String?,
    val exampleZh: String?,
    val level: String,
    val tags: String?,
    val source: String = SRC_BUILTIN
) {
    /** 词性的中文显示名 */
    val posLabel: String
        get() = when (pos) {
            "n" -> "名词"
            "v" -> "动词"
            "adj" -> "形容词"
            "adv" -> "副词"
            "prep" -> "介词"
            "pron" -> "代词"
            "conj" -> "连词"
            "num" -> "数词"
            "art" -> "冠词"
            "phr" -> "短语"
            "int" -> "感叹词"
            else -> pos
        }

    /** 名词的性数信息，用于详情页展示 */
    val grammarNote: String?
        get() {
            if (pos != "n") return null
            val g = when (gender) {
                "m" -> "阳性 der"
                "f" -> "阴性 die"
                "n" -> "中性 das"
                else -> null
            }
            val p = plural?.takeIf { it.isNotBlank() }?.let { "复数 $it" }
            return listOfNotNull(g, p).joinToString(" · ").ifBlank { null }
        }

    companion object {
        const val SRC_BUILTIN = "builtin"
        const val SRC_CUSTOM = "custom"
        const val SRC_ONLINE = "online"
    }
}

/** 学习进度记录（SM-2 算法参数） */
data class Progress(
    val wordId: Long,
    val ease: Double = 2.5,
    val interval: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val due: Long = 0L,
    val lastReview: Long = 0L,
    /** 0 = 新词从未学过, 1 = 学习中, 2 = 复习中 */
    val state: Int = 0
)

/** 词条 + 进度的组合，供列表与学习队列使用 */
data class WordWithProgress(
    val word: Word,
    val progress: Progress
)

/** 学习统计 */
data class StudyStats(
    val totalLearned: Int = 0,
    val dueToday: Int = 0,
    val newAvailable: Int = 0,
    val starred: Int = 0,
    val streak: Int = 0,
    val reviewsToday: Int = 0
)

/** 四档评分，映射 SM-2 的 quality */
enum class Rating(val quality: Int, val label: String) {
    AGAIN(0, "忘了"),
    HARD(3, "模糊"),
    GOOD(4, "认识"),
    EASY(5, "太简单")
}
