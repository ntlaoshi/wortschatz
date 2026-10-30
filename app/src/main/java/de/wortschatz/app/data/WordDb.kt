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
                state INTEGER NOT NULL DEFAULT 0
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 词库为可重灌数据，升级时直接重建，避免复杂的迁移逻辑。
        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS words")
            db.execSQL("DROP TABLE IF EXISTS progress")
            db.execSQL("DROP TABLE IF EXISTS starred")
            db.execSQL("DROP TABLE IF EXISTS daily_log")
            onCreate(db)
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

    fun deleteWordsBySource(source: String): Int =
        writableDatabase.delete("words", "source = ?", arrayOf(source))

    /** 清除单个词的学习进度，使其回到「未学」状态 */
    fun resetWordProgress(wordId: Long): Int =
        writableDatabase.delete("progress", "word_id = ?", arrayOf(wordId.toString()))

    /** 清除全部学习进度，返回受影响的词条数 */
    fun resetAllProgress(): Int = writableDatabase.delete("progress", null, null)

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

    /** 待复习：state > 0 且 due <= now */
    fun dueWords(now: Long, levels: Set<String>, limit: Int): List<WordWithProgress> {
        val sql = """
            SELECT ${prefix("w.")}, ${prefixProgress()}
            FROM words w JOIN progress p ON p.word_id = w.id
            WHERE p.state > 0 AND p.due <= ? ${levelClause(levels, "w.")}
            ORDER BY p.due ASC
            LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(now.toString(), limit.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(readWordWithProgress(c)) } }
    }

    /** 未学过的新词，按等级由低到高取 */
    fun newWords(levels: Set<String>, limit: Int): List<Word> {
        val sql = """
            SELECT ${prefix("w.")} FROM words w
            LEFT JOIN progress p ON p.word_id = w.id
            WHERE p.word_id IS NULL ${levelClause(levels, "w.")}
            ORDER BY w.level ASC, w.id ASC
            LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(readWord(c, c.getLong(0))) }
        }
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

    fun stats(levels: Set<String>): StudyStats {
        val now = System.currentTimeMillis()
        val today = todayString()
        val clause = levelClause(levels, "w.")
        val learned = rawInt(
            "SELECT COUNT(*) FROM progress p JOIN words w ON w.id = p.word_id " +
                "WHERE p.state > 0 $clause"
        )
        val due = rawInt(
            "SELECT COUNT(*) FROM progress p JOIN words w ON w.id = p.word_id " +
                "WHERE p.state > 0 AND p.due <= $now $clause"
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
        private const val DB_VERSION = 1
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
