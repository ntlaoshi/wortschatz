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
    const val SEED_VERSION = 5

    private val ASSET_FILES = listOf(
        "words_a1.json",
        "words_a2.json",
        "words_b1.json",
        "words_b2.json",
        // 由中文维基词典扩充的词条，见 data_src/merge_ext.py
        "words_ext.json"
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
     * 读取词表顶层的 name 字段，导入时用作新单词书的名称。
     * 没有该字段则返回 null，由调用方给出默认名。
     */
    fun parseName(text: String): String? = try {
        JSONObject(text).optString("name").ifBlank { null }
    } catch (e: Exception) {
        null
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
            val word = jsonString(o, "w", "word")?.trim() ?: continue
            val zh = jsonString(o, "zh", "meaning") ?: ""
            val lemma = jsonString(o, "l", "lemma") ?: word
                .removePrefix("der ").removePrefix("die ").removePrefix("das ")
            out.add(
                Word(
                    word = word,
                    lemma = lemma,
                    pos = jsonString(o, "p", "pos") ?: "",
                    gender = jsonString(o, "g", "gender"),
                    plural = jsonString(o, "pl", "plural"),
                    zh = zh,
                    en = jsonString(o, "en"),
                    example = jsonString(o, "ex", "example"),
                    exampleZh = jsonString(o, "exZh", "example_zh"),
                    level = jsonString(o, "lv", "level") ?: "A1",
                    tags = jsonString(o, "tg", "tags"),
                    source = jsonString(o, "src") ?: Word.SRC_BUILTIN
                )
            )
        }
        return out
    }

    /**
     * 读取字符串字段。
     *
     * 必须显式判断 isNull：`JSONObject.optString()` 遇到 JSON null 时返回字符串 "null"
     * 而不是空串，直接落库会让名词显示成「复数 null」。字段缺失、显式 null、
     * 以及字面量 "null" 三种情况统一返回 null。
     */
    private fun jsonString(o: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!o.has(key) || o.isNull(key)) continue
            val v = o.optString(key).trim()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) return v
        }
        return null
    }
}
