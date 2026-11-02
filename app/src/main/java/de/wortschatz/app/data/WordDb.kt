package de.wortschatz.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 基于原生 SQLite 的词库与学习记录存储。
 *
 * 未引入 Room 的原因：本项目表结构简单、以批量导入为主，
 * 手写 SQL 可省去注解处理器依赖，构建更快且行为完全可控。
 */
class WordDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                lemma TEXT NOT NULL,
                pos TEXT NOT NULL DEFAULT '',
                gender TEXT,
                plural TEXT,
                zh TEXT NOT NULL,
                en TEXT,
                example TEXT,
                example_zh TEXT,
                level TEXT NOT NULL DEFAULT 'A1',
                tags TEXT,
                source TEXT NOT NULL DEFAULT 'builtin'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_words_lemma ON words(lemma)")
        db.execSQL("CREATE UNIQUE INDEX idx_words_unique ON words(word, pos, zh)")

        db.execSQL(
            """
            CREATE TABLE progress (
                word_id INTEGER PRIMARY KEY,
                ease REAL NOT NULL DEFAULT 2.5,
                interval_days INTEGER NOT NULL DEFAULT 0,
                reps INTEGER NOT NULL DEFAULT 0,
                lapses INTEGER NOT NULL DEFAULT 0,
                due INTEGER NOT NULL DEFAULT 0,
                last_review INTEGER NOT NULL DEFAULT 0,
                state INTEGER NOT NULL DEFAULT 0,
                mastered INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_progress_due ON progress(due)")

        db.execSQL(
            """
            CREATE TABLE starred (
                word_id INTEGER PRIMARY KEY,
                created INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE daily_log (
                day TEXT PRIMARY KEY,
                reviews INTEGER NOT NULL DEFAULT 0,
                learned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE book_words (
                book_id TEXT NOT NULL,
                word_id INTEGER NOT NULL,
                added INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (book_id, word_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_book_words_book ON book_words(book_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 增量迁移：学习进度是用户数据，不能因为加字段就丢掉。
        var version = oldVersion
        if (version < 2) {
            // v2：新增「已掌握」标记，已掌握的词不再进入复习队列
            db.execSQL("ALTER TABLE progress ADD COLUMN mastered INTEGER NOT NULL DEFAULT 0")
            version = 2
        }
        if (version < 3) {
            // v3：自建单词书改为「自选词集合」，需要书与词的关联表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS book_words (
                    book_id TEXT NOT NULL,
                    word_id INTEGER NOT NULL,
                    added INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (book_id, word_id)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_words_book ON book_words(book_id)")
            version = 3
        }
        if (version < 4) {
            // v4：修复旧版解析器把 JSON null 写成字符串 "null" 的脏数据，
            // 否则名词会显示「复数 null」。只清理字面量 "null"，真实值不受影响。
            listOf("gender", "plural", "en", "example", "example_zh", "tags").forEach { col ->
                db.execSQL("UPDATE words SET $col = NULL WHERE LOWER($col) = 'null'")
            }
            version = 4
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    // ---------------------------------------------------------------- 写入

    /** 批量导入词条，已存在的（同 word+pos+zh）自动跳过。返回实际新增条数。 */
    fun insertWords(words: List<Word>): Int {
        if (words.isEmpty()) return 0
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT OR IGNORE INTO words
                (word, lemma, pos, gender, plural, zh, en, example, example_zh, level, tags, source)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            )
            words.forEach { w ->
                stmt.clearBindings()
                stmt.bindString(1, w.word)
                stmt.bindString(2, w.lemma)
                stmt.bindString(3, w.pos)
                w.gender?.let { stmt.bindString(4, it) } ?: stmt.bindNull(4)
                w.plural?.let { stmt.bindString(5, it) } ?: stmt.bindNull(5)
                stmt.bindString(6, w.zh)
                w.en?.let { stmt.bindString(7, it) } ?: stmt.bindNull(7)
                w.example?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
                w.exampleZh?.let { stmt.bindString(9, it) } ?: stmt.bindNull(9)
                stmt.bindString(10, w.level)
                w.tags?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
                stmt.bindString(12, w.source)
                if (stmt.executeInsert() != -1L) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun saveProgress(p: Progress) {
        val cv = ContentValues().apply {
            put("word_id", p.wordId)
            put("ease", p.ease)
            put("interval_days", p.interval)
            put("reps", p.reps)
            put("lapses", p.lapses)
            put("due", p.due)
            put("last_review", p.lastReview)
            put("state", p.state)
        }
        writableDatabase.insertWithOnConflict(
            "progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun logReview(day: String, isNewWord: Boolean) {
        val db = writableDatabase
        val esc = day.replace("'", "''")
        db.execSQL(
            "INSERT INTO daily_log(day, reviews, learned) VALUES('$esc',1,${if (isNewWord) 1 else 0}) " +
                "ON CONFLICT(day) DO UPDATE SET reviews = reviews + 1" +
                if (isNewWord) ", learned = learned + 1" else ""
        )
    }

    /** 撤销一次学习记录：把当天的复习/新学计数减回去 */
    fun undoLogReview(day: String, isNewWord: Boolean) {
        val esc = day.replace("'", "''")
        writableDatabase.execSQL(
            "UPDATE daily_log SET reviews = MAX(reviews - 1, 0)" +
                (if (isNewWord) ", learned = MAX(learned - 1, 0)" else "") +
                " WHERE day = '$esc'"
        )
    }

    /** 切换收藏，返回切换后的状态 */
    fun toggleStar(wordId: Long): Boolean {
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT 1 FROM starred WHERE word_id = ?", arrayOf(wordId.toString())
        ).use { it.moveToFirst() }
        return if (existing) {
            db.delete("starred", "word_id = ?", arrayOf(wordId.toString()))
            false
        } else {
            db.insertWithOnConflict(
                "starred", null,
                ContentValues().apply {
                    put("word_id", wordId)
                    put("created", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            true
        }
    }

    /** 查询这批 lemma 中已存在于词库的（统一小写比对），用于导入时避免重复词条 */
    /**
     * 按词形批量取词条（不分大小写），用于拍照识别后的匹配。
     * 返回顺序与库内顺序一致，调用方自行按需排序。
     */
    fun wordsByLemmas(lemmas: Collection<String>): List<Word> {
        val uniq = lemmas.map { foldUmlaut(it) }.filter { it.isNotBlank() }.distinct()
        if (uniq.isEmpty()) return emptyList()
        val out = mutableListOf<Word>()
        uniq.chunked(300).forEach { chunk ->
            val list = chunk.joinToString(",") { "'${it.replace("'", "''")}'" }
            // 双向折叠变音字母：文本里写 Gemuese 也能命中词库的 Gemüse
            readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM words WHERE $FOLDED_LEMMA IN ($list)", null
            ).use { c ->
                while (c.moveToNext()) out.add(readWord(c, c.getLong(0)))
            }
        }
        return out
    }


    /**
     * 按词元在原文中的先后顺序逐个匹配词库，每个词元只取一个最贴切的词条。
     *
     * 不再用一条 `FOLDED_LEMMA IN (...)` 批量查，原因有两个：
     * 1. 词库里存在只差大小写的同形词（`bitte`/`Bitte`、`leben`/`Leben`、`arm`/`Arm` 等 8 组），
     *    折叠匹配会把两个都带出来——文本里只写了 bitte，结果却多出一个名词；
     * 2. 批量查的返回顺序按词库 rowid，与原文顺序无关，看起来就像「有的词没进来、有的凭空出现」。
     */
    fun matchTokens(tokens: Collection<String>): List<Pair<String, Word>> {
        val out = mutableListOf<Pair<String, Word>>()
        val seen = mutableSetOf<String>()
        tokens.forEach { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@forEach
            // 按原样去重：Leben（名词）与 leben（动词）是两个词条，不能合并
            if (!seen.add(token)) return@forEach
            val hit = matchOne(token) ?: return@forEach
            out.add(token to hit)
        }
        return out
    }

    /** 单个词元的匹配：从最严格到最宽松依次尝试，命中即停 */
    private fun matchOne(token: String): Word? {
        val attempts = listOf(
            "lemma = ?" to token,                       // 大小写完全一致，最可信
            "LOWER(lemma) = ?" to token.lowercase(),    // 忽略大小写
            "$FOLDED_LEMMA = ?" to foldUmlaut(token),   // Gemuese → Gemüse
            "LOWER(word) = ?" to token.lowercase()      // 文本里带冠词：der Hund
        )
        attempts.forEach { (clause, arg) ->
            readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM words WHERE $clause ORDER BY level ASC, id ASC LIMIT 1",
                arrayOf(arg)
            ).use { c ->
                if (c.moveToNext()) return readWord(c, c.getLong(0))
            }
        }
        return null
    }

    fun existingLemmas(lemmas: List<String>): Set<String> {
        if (lemmas.isEmpty()) return emptySet()
        val out = mutableSetOf<String>()
        lemmas.chunked(300).forEach { chunk ->
            // 入参可能保留原始大小写，这里统一转小写再比对，避免判成「新词」重复入库
            val list = chunk.joinToString(",") {
                "'${it.lowercase().replace("'", "''")}'"
            }
            readableDatabase.rawQuery(
                "SELECT DISTINCT LOWER(lemma) FROM words WHERE LOWER(lemma) IN ($list)", null
            ).use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        }
        return out
    }

    fun deleteWordsBySource(source: String): Int =
        writableDatabase.delete("words", "source = ?", arrayOf(source))

    /**
     * 按词根批量取词条 id，用于把导入的词挂到单词书上。
     * 同一词根可能对应多条记录（不同词性或释义），每个词根只取一条，
     * 避免书里的词数比词表条目多出一截。
     */
    fun wordIdsByLemmas(lemmas: List<String>): List<Long> {
        if (lemmas.isEmpty()) return emptyList()
        val out = mutableListOf<Long>()
        val rest = mutableListOf<String>()

        lemmas.chunked(300).forEach { chunk ->
            val list = chunk.joinToString(",") { "'${it.replace("'", "''")}'" }
            // 先按原样匹配：sie（他们）与 Sie（您）只差大小写，LOWER 会把它们并成一条，
            // 导致导入后书里少词（实测 118 条只剩 117）。
            val matched = mutableSetOf<String>()
            readableDatabase.rawQuery(
                "SELECT lemma, MIN(id) FROM words WHERE lemma IN ($list) GROUP BY lemma", null
            ).use { c ->
                while (c.moveToNext()) {
                    matched.add(c.getString(0))
                    out.add(c.getLong(1))
                }
            }
            rest += chunk.filter { it !in matched }
        }

        // 原样没匹配到的（词库里写法大小写不同）再退化为忽略大小写匹配
        rest.chunked(300).forEach { chunk ->
            val list = chunk.joinToString(",") { "'${it.lowercase().replace("'", "''")}'" }
            readableDatabase.rawQuery(
                "SELECT MIN(id) FROM words WHERE LOWER(lemma) IN ($list) " +
                    "GROUP BY LOWER(lemma)", null
            ).use { c ->
                while (c.moveToNext()) out.add(c.getLong(0))
            }
        }
        return out
    }

    /** 清除单个词的学习进度，使其回到「未学」状态 */
    fun resetWordProgress(wordId: Long): Int =
        writableDatabase.delete("progress", "word_id = ?", arrayOf(wordId.toString()))

    /** 清除全部学习进度，返回受影响的词条数 */
    fun resetAllProgress(): Int = writableDatabase.delete("progress", null, null)

    /**
     * 标记 / 取消标记「已掌握」。
     * 已掌握的词不再进入复习队列；取消掌握后立即回到待复习队列。
     */
    fun setMastered(wordId: Long, mastered: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("mastered", if (mastered) 1 else 0)
            if (!mastered) put("due", System.currentTimeMillis())
        }
        val updated = db.update("progress", values, "word_id = ?", arrayOf(wordId.toString()))
        if (updated == 0) {
            // 尚未学过就直接标记掌握：补一条进度记录，避免它再被当成新词
            values.put("word_id", wordId)
            values.put("state", 1)
            db.insert("progress", null, values)
        }
    }

    fun isMastered(wordId: Long): Boolean =
        rawInt("SELECT COALESCE(MAX(mastered),0) FROM progress WHERE word_id = $wordId") == 1

    /** 已掌握的词 */
    fun masteredWords(): List<Word> {
        val sql = """
            SELECT ${prefix("w.")} FROM words w JOIN progress p ON p.word_id = w.id
            WHERE p.mastered = 1
            ORDER BY w.word ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    /** 已学习但未掌握的词，最近复习的排在前面 */
    fun learnedWords(): List<Word> {
        val sql = """
            SELECT ${prefix("w.")} FROM words w JOIN progress p ON p.word_id = w.id
            WHERE p.state > 0 AND p.mastered = 0
            ORDER BY p.last_review DESC, w.word ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    /** 移除单个词的学习记录（回到未学状态） */
    fun removeLearningRecord(wordId: Long): Int = resetWordProgress(wordId)

    // ---------------------------------------------------------------- 自建单词书

    /** 往书里添加词，返回实际新增条数（已存在的自动跳过） */
    fun addWordsToBook(bookId: String, wordIds: List<Long>): Int {
        if (wordIds.isEmpty()) return 0
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT OR IGNORE INTO book_words(book_id, word_id, added) VALUES(?,?,?)"
            )
            val now = System.currentTimeMillis()
            wordIds.forEach { id ->
                stmt.clearBindings()
                stmt.bindString(1, bookId)
                stmt.bindLong(2, id)
                stmt.bindLong(3, now)
                if (stmt.executeInsert() != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun removeWordFromBook(bookId: String, wordId: Long): Int = writableDatabase.delete(
        "book_words", "book_id = ? AND word_id = ?", arrayOf(bookId, wordId.toString())
    )

    /** 书里的全部词 */
    fun bookWords(bookId: String): List<Word> {
        val sql = """
            SELECT ${prefix("w.")} FROM words w
            JOIN book_words b ON b.word_id = w.id
            WHERE b.book_id = ?
            ORDER BY w.level ASC, w.word ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(bookId)).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    fun bookWordCount(bookId: String): Int = rawInt(
        "SELECT COUNT(*) FROM book_words WHERE book_id = '${bookId.replace("'", "''")}'"
    )

    /** 词是否已在这本书里 */
    fun isInBook(bookId: String, wordId: Long): Boolean = rawInt(
        "SELECT COUNT(*) FROM book_words WHERE book_id = '${bookId.replace("'", "''")}' " +
            "AND word_id = $wordId"
    ) > 0

    fun deleteBookWords(bookId: String): Int =
        writableDatabase.delete("book_words", "book_id = ?", arrayOf(bookId))

    /** 有练习记录的日期集合（yyyy-MM-dd），供打卡日历使用 */
    fun loggedDays(): Set<String> {
        val set = mutableSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT day FROM daily_log WHERE reviews > 0 ORDER BY day DESC LIMIT 800", null
        ).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    // ---------------------------------------------------------------- 查询

    fun countWords(): Int = rawInt("SELECT COUNT(*) FROM words")

    fun countByLevel(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT level, COUNT(*) FROM words GROUP BY level", null
        ).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getInt(1)
        }
        return map
    }

    fun getWord(id: Long): Word? =
        readableDatabase.rawQuery("SELECT $COLUMNS FROM words WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) readWord(it, id) else null }

    fun getProgress(wordId: Long): Progress? =
        readableDatabase.rawQuery(
            "SELECT word_id, ease, interval_days, reps, lapses, due, last_review, state " +
                "FROM progress WHERE word_id = ?", arrayOf(wordId.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else Progress(
                wordId = c.getLong(0),
                ease = c.getDouble(1),
                interval = c.getInt(2),
                reps = c.getInt(3),
                lapses = c.getInt(4),
                due = c.getLong(5),
                lastReview = c.getLong(6),
                state = c.getInt(7)
            )
        }

    /**
     * 搜索。排序优先级：完全匹配 > 前缀匹配 > 包含匹配，同级按字母序。
     */
    fun search(query: String, levels: Set<String>, limit: Int = 60): List<Word> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val levelClause = levelClause(levels)
        val sql = """
            SELECT $COLUMNS,
                CASE
                    WHEN LOWER(lemma) = LOWER(?) OR LOWER(word) = LOWER(?) THEN 0
                    WHEN LOWER(lemma) LIKE LOWER(?) OR LOWER(word) LIKE LOWER(?) THEN 1
                    ELSE 2
                END AS rank
            FROM words
            WHERE (LOWER(word) LIKE LOWER(?) OR LOWER(lemma) LIKE LOWER(?) OR zh LIKE ?)
              $levelClause
            ORDER BY rank ASC, LENGTH(word) ASC, word ASC
            LIMIT ?
        """.trimIndent()
        val like = "%$q%"
        val prefix = "$q%"
        val args = arrayOf(q, q, prefix, prefix, like, like, like, limit.toString())
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    /**
     * 待复习：state > 0 且 due <= now，已掌握的词永久排除。
     * bookId 非空时只取该单词书里的词，忽略等级条件。
     */
    fun dueWords(
        now: Long,
        levels: Set<String>,
        limit: Int,
        bookId: String? = null
    ): List<WordWithProgress> {
        val filter = bookFilter(bookId) ?: levelClause(levels, "w.")
        val sql = """
            SELECT ${prefix("w.")}, ${prefixProgress()}
            FROM words w JOIN progress p ON p.word_id = w.id
            WHERE p.state > 0 AND p.due <= ? AND p.mastered = 0 $filter
            ORDER BY p.due ASC
            LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(now.toString(), limit.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(readWordWithProgress(c)) } }
    }

    /** 未学过的新词，按等级由低到高取；bookId 非空时只取该书内的词 */
    fun newWords(
        levels: Set<String>,
        limit: Int,
        bookId: String? = null
    ): List<Word> {
        val filter = bookFilter(bookId) ?: levelClause(levels, "w.")
        val sql = """
            SELECT ${prefix("w.")} FROM words w
            LEFT JOIN progress p ON p.word_id = w.id
            WHERE p.word_id IS NULL $filter
            ORDER BY w.level ASC, w.id ASC
            LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    /** 自建书的过滤条件；bookId 为空返回 null 表示按等级筛选 */
    private fun bookFilter(bookId: String?): String? {
        if (bookId == null) return null
        val safe = bookId.replace("'", "''")
        return " AND w.id IN (SELECT word_id FROM book_words WHERE book_id = '$safe')"
    }

    fun starredWords(): List<Word> {
        val sql = "SELECT ${prefix("w.")} FROM words w JOIN starred s ON s.word_id = w.id " +
            "ORDER BY s.created DESC"
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
    }

    fun isStarred(wordId: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM starred WHERE word_id = ?", arrayOf(wordId.toString())
        ).use { it.moveToFirst() }

    fun progressOf(wordId: Long): Progress = getProgress(wordId) ?: Progress(wordId)

    fun stats(levels: Set<String>, bookId: String? = null): StudyStats {
        val now = System.currentTimeMillis()
        val today = todayString()
        val clause = bookFilter(bookId) ?: levelClause(levels, "w.")
        val learned = rawInt(
            "SELECT COUNT(*) FROM progress p JOIN words w ON w.id = p.word_id " +
                "WHERE p.state > 0 AND p.mastered = 0 $clause"
        )
        val due = rawInt(
            "SELECT COUNT(*) FROM progress p JOIN words w ON w.id = p.word_id " +
                "WHERE p.state > 0 AND p.due <= $now AND p.mastered = 0 $clause"
        )
        val newAvail = rawInt(
            "SELECT COUNT(*) FROM words w LEFT JOIN progress p ON p.word_id = w.id " +
                "WHERE p.word_id IS NULL $clause"
        )
        val starred = rawInt("SELECT COUNT(*) FROM starred")
        val reviewsToday = rawInt("SELECT COALESCE(reviews,0) FROM daily_log WHERE day = '$today'")
        return StudyStats(
            totalLearned = learned,
            dueToday = due,
            newAvailable = newAvail,
            starred = starred,
            streak = calcStreak(),
            reviewsToday = reviewsToday
        )
    }

    /** 连续打卡天数：从今天（或昨天）往回数连续有复习记录的天数 */
    private fun calcStreak(): Int {
        val days = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT day FROM daily_log WHERE reviews > 0 ORDER BY day DESC LIMIT 400", null
        ).use { c -> while (c.moveToNext()) days.add(c.getString(0)) }
        if (days.isEmpty()) return 0

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = Calendar0.today()
        var cursor = today
        // 今天还没复习时，从昨天开始算，不算断签
        if (days.first() != fmt.format(cursor.time)) {
            cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        for (d in days) {
            if (d == fmt.format(cursor.time)) {
                streak++
                cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else if (d < fmt.format(cursor.time)) {
                break
            }
        }
        return streak
    }

    private fun todayString(): String = Calendar0.todayString()

    // ---------------------------------------------------------------- 内部工具

    private fun rawInt(sql: String): Int =
        readableDatabase.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun levelClause(levels: Set<String>, alias: String = ""): String {
        if (levels.isEmpty() || levels.size >= 4) return ""
        val p = if (alias.isEmpty()) "" else alias
        val list = levels.joinToString(",") { "'${it.replace("'", "''")}'" }
        return " AND ${p}level IN ($list)"
    }

    private fun prefix(alias: String): String = COLUMNS.split(",")
        .joinToString(",") { alias + it.trim() }

    private fun prefixProgress(): String =
        "p.word_id AS p_word_id, p.ease AS p_ease, p.interval_days AS p_interval, " +
            "p.reps AS p_reps, p.lapses AS p_lapses, p.due AS p_due, " +
            "p.last_review AS p_last_review, p.state AS p_state"

    private fun readWord(c: android.database.Cursor, id: Long): Word = Word(
        id = id,
        word = c.getString(1),
        lemma = c.getString(2),
        pos = c.getString(3),
        gender = c.getStringOrNull(4),
        plural = c.getStringOrNull(5),
        zh = c.getString(6),
        en = c.getStringOrNull(7),
        example = c.getStringOrNull(8),
        exampleZh = c.getStringOrNull(9),
        level = c.getString(10),
        tags = c.getStringOrNull(11),
        source = c.getString(12)
    )

    private fun readWordWithProgress(c: android.database.Cursor): WordWithProgress {
        val word = readWord(c, c.getLong(0))
        val base = 13
        val progress = Progress(
            wordId = c.getLong(base),
            ease = c.getDouble(base + 1),
            interval = c.getInt(base + 2),
            reps = c.getInt(base + 3),
            lapses = c.getInt(base + 4),
            due = c.getLong(base + 5),
            lastReview = c.getLong(base + 6),
            state = c.getInt(base + 7)
        )
        return WordWithProgress(word, progress)
    }

    private fun android.database.Cursor.getStringOrNull(i: Int): String? =
        if (isNull(i)) null else getString(i)

    companion object {
        private const val DB_NAME = "wortschatz.db"
        private const val DB_VERSION = 4

        /** 与 [foldUmlaut] 对应的 SQL 表达式，用于变音字母的模糊匹配 */
        private const val FOLDED_LEMMA =
            "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(lemma," +
                "'ä','ae'),'ö','oe'),'ü','ue'),'ß','ss'),'Ä','ae'),'Ö','oe'),'Ü','ue'))"

        /** 小写并把变音字母转写为 ae/oe/ue/ss */
        fun foldUmlaut(raw: String): String = raw.trim().lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        private const val COLUMNS =
            "id, word, lemma, pos, gender, plural, zh, en, example, example_zh, level, tags, source"

        @Volatile
        private var instance: WordDb? = null

        fun get(context: Context): WordDb =
            instance ?: synchronized(this) {
                instance ?: WordDb(context).also { instance = it }
            }
    }
}

/** 日期辅助，统一按设备本地时区计算"今天" */
object Calendar0 {
    fun today(): java.util.Calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    fun todayString(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(today().time)
}
