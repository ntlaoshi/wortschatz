package de.wortschatz.app.data

/**
 * 单词书：一本书对应一组学习等级。
 * 内置 5 本不可编辑，用户自建的可以改名、改范围、删除。
 */
data class WordBook(
    val id: String,
    val name: String,
    val levels: Set<String>,
    val builtin: Boolean = false
) {
    companion object {
        val BUILTINS = listOf(
            WordBook("builtin_a1", "A1 入门", setOf("A1"), true),
            WordBook("builtin_a2", "A2 基础", setOf("A2"), true),
            WordBook("builtin_b1", "B1 进阶", setOf("B1"), true),
            WordBook("builtin_b2", "B2 提高", setOf("B2"), true),
            WordBook("builtin_all", "全部词库", setOf("A1", "A2", "B1", "B2"), true)
        )

        /** 内置书的一句话说明，自定义书没有 */
        fun descriptionOf(book: WordBook): String = when (book.id) {
            "builtin_a1" -> "基础词汇与日常表达"
            "builtin_a2" -> "生活场景与简单交流"
            "builtin_b1" -> "工作、社会与观点表达"
            "builtin_b2" -> "抽象话题与书面表达"
            "builtin_all" -> "所有等级混合"
            else -> book.levels.sorted().joinToString(" + ")
        }
    }
}
