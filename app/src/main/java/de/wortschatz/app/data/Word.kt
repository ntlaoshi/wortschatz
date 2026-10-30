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
    fun grammarNote(style: PluralStyle = PluralStyle.FULL): String? {
        if (pos != "n") return null
        val g = when (gender) {
            "m" -> "阳性 der"
            "f" -> "阴性 die"
            "n" -> "中性 das"
            else -> null
        }
        return listOfNotNull(g, pluralNote(style)).joinToString(" · ").ifBlank { null }
    }

    /**
     * 名词复数提示，单独暴露给卡片正面与列表行使用。
     * 德语名词的复数形式变化无规律，背单词时必须与单数同时记住，
     * 因此除详情页外，凡是出现词条的地方都要带上这一行。
     */
    fun pluralNote(style: PluralStyle = PluralStyle.FULL): String? {
        if (pos != "n") return null
        val p = plural?.takeIf { it.isNotBlank() } ?: return null
        return when (style) {
            PluralStyle.FULL -> "复数 $p"
            PluralStyle.SUFFIX -> "复数 ${pluralSuffix(p)}"
        }
    }

    /**
     * 把复数化成一如教材的词尾写法：die Männer -> ¨-er，die Frauen -> -en，die Mädchen -> 同形。
     *
     * 做法：先去掉变音符号比较，求出单复数共有的词干，剩余部分即词尾；
     * 若词干里出现了单数没有的变音字母，则在词尾前加 ¨。
     * 单复数毫无共同前缀时（词形不规则）退回完整形式，避免输出无意义的词尾。
     */
    private fun pluralSuffix(pluralForm: String): String {
        val singularBody = word.dropArticle()
        val pluralBody = pluralForm.dropArticle()
        if (singularBody.isEmpty() || pluralBody.isEmpty()) return pluralForm

        val s = singularBody.map(::foldUmlaut)
        val p = pluralBody.map(::foldUmlaut)

        var i = 0
        while (i < s.size && i < p.size && s[i] == p[i]) i++
        if (i == 0) return pluralForm

        val rest = pluralBody.substring(i)
        val umlaut = pluralBody.take(i).any { it.isUmlaut() } &&
            !singularBody.take(i).any { it.isUmlaut() }
        // 只变音不加词尾（der Vater -> die Väter）：教材写作 "¨"
        if (rest.isEmpty()) return if (umlaut) "¨" else "同形"
        return (if (umlaut) "¨" else "") + "-" + rest
    }

    private fun String.dropArticle(): String = trim()
        .removePrefix("der ").removePrefix("die ").removePrefix("das ")
        .trim()

    private fun foldUmlaut(c: Char): Char = when (c) {
        'ä', 'Ä' -> 'a'
        'ö', 'Ö' -> 'o'
        'ü', 'Ü' -> 'u'
        'ß' -> 's'
        else -> c.lowercaseChar()
    }

    private fun Char.isUmlaut(): Boolean = this in "äöüÄÖÜ"

    companion object {
        const val SRC_BUILTIN = "builtin"
        const val SRC_CUSTOM = "custom"
        const val SRC_ONLINE = "online"
    }
}

/** 复数展示方式：完整形式（die Männer）或教材式词尾（¨-er） */
enum class PluralStyle {
    FULL,
    SUFFIX
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
