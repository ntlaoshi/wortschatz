package de.wortschatz.app.data

/**
 * 拼写测试的答案比对规则。
 *
 * 德语键盘输入变音字母不便，因此 ä/ö/ü/ß 与 ae/oe/ue/ss 视为等价；
 * 冠词、大小写、多余空格与句末标点都不计入差异。
 */
object SpellTest {

    private val ARTICLE = Regex("^(der|die|das)\\s+", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")
    private val EDGE_PUNCT = charArrayOf('.', ',', '!', '?', ';', ':', '…', ' ')

    fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        s = ARTICLE.replace(s, "")
        s = s.replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        s = SPACES.replace(s, " ")
        return s.trim(*EDGE_PUNCT)
    }

    /** 用户输入是否与词条拼写一致 */
    fun matches(input: String, word: Word): Boolean {
        val answer = word.word.ifBlank { word.lemma }
        return normalize(input) == normalize(answer)
    }

    /** 展示用标准答案（去掉冠词后的词形） */
    fun answerOf(word: Word): String = ARTICLE.replace(word.word, "").trim()

    /** 首字母提示，帮助回忆但不直接给答案 */
    fun hintOf(word: Word): String {
        val a = answerOf(word)
        return a.mapIndexed { i, c -> if (i == 0) c else '·' }.joinToString("")
    }
}
