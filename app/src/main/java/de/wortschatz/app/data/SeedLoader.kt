package de.wortschatz.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 内置词库导入。
 *
 * 词库以 assets 目录下的 JSON 文件形式随 APK 分发，采用短键名压缩体积。
 * 通过 SEED_VERSION 控制增量导入：版本号变化时重新扫描一次，
 * 依赖 words 表的唯一索引做去重，因此重复导入不会产生脏数据。
 */
object SeedLoader {

    private const val PREFS = "wortschatz_prefs"
    private const val KEY_SEED_VERSION = "seed_version"
    const val SEED_VERSION = 4

    private val ASSET_FILES = listOf(
        "words_a1.json",
        "words_a2.json",
        "words_b1.json",
        "words_b2.json"
    )

    data class ImportResult(val imported: Int, val skipped: Boolean)

    /** 首次启动或词库版本升级时导入内置词库 */
    fun importIfNeeded(context: Context, db: WordDb): ImportResult {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_SEED_VERSION, 0)
        if (current >= SEED_VERSION && db.countWords() > 0) {
            return ImportResult(0, skipped = true)
        }
        var total = 0
        ASSET_FILES.forEach { file ->
            total += parseAsset(context, file).let { db.insertWords(it) }
        }
        prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
        return ImportResult(total, skipped = false)
    }

    /** 解析单个 assets 词库文件 */
    fun parseAsset(context: Context, name: String): List<Word> = try {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        parseJson(text)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 解析词条 JSON。兼容两种写法：短键名（内置数据）与全名键（用户导入）。
     */
    fun parseJson(text: String): List<Word> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("words") ?: return emptyList()
        val out = ArrayList<Word>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val word = o.optString("w").ifBlank { o.optString("word") }.trim()
            if (word.isEmpty()) continue
            val zh = o.optString("zh").ifBlank { o.optString("meaning") }.trim()
            val lemma = o.optString("l").ifBlank {
                o.optString("lemma").ifBlank {
                    // 去掉冠词作为检索词根
                    word.removePrefix("der ").removePrefix("die ").removePrefix("das ")
                }
            }
            out.add(
                Word(
                    word = word,
                    lemma = lemma,
                    pos = o.optString("p").ifBlank { o.optString("pos", "") },
                    gender = o.optString("g").ifBlank { o.optString("gender") }.ifBlank { null },
                    plural = o.optString("pl").ifBlank { o.optString("plural") }.ifBlank { null },
                    zh = zh,
                    en = o.optString("en").ifBlank { null },
                    example = o.optString("ex").ifBlank { o.optString("example") }.ifBlank { null },
                    exampleZh = o.optString("exZh").ifBlank { o.optString("example_zh") }
                        .ifBlank { null },
                    level = o.optString("lv").ifBlank { o.optString("level", "A1") },
                    tags = o.optString("tg").ifBlank { o.optString("tags") }.ifBlank { null },
                    source = o.optString("src", Word.SRC_BUILTIN)
                )
            )
        }
        return out
    }
}
