package de.wortschatz.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.wortschatz.app.data.Calendar0
import de.wortschatz.app.data.OnlineDict
import de.wortschatz.app.data.PluralStyle
import de.wortschatz.app.data.Progress
import de.wortschatz.app.data.Rating
import de.wortschatz.app.data.SeedLoader
import de.wortschatz.app.data.Sm2
import de.wortschatz.app.data.SpellTest
import de.wortschatz.app.data.StudyStats
import de.wortschatz.app.data.Word
import de.wortschatz.app.data.WordBook
import de.wortschatz.app.data.WordDb
import de.wortschatz.app.data.WordWithProgress
import de.wortschatz.app.tts.Speaker
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = WordDb.get(app)
    val speaker = Speaker(app)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- 全局状态
    var initializing by mutableStateOf(true)
        private set
    var stats by mutableStateOf(StudyStats())
        private set
    var levelCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var totalWords by mutableStateOf(0)
        private set

    // ---- 设置（持久化）
    var levels by mutableStateOf(loadLevels())
        private set

    /** 每轮学习的词数（到期复习优先，不足部分补新词） */
    var sessionSize by mutableStateOf(prefs.getInt(KEY_SESSION_SIZE, 20))
        private set
    var autoSpeak by mutableStateOf(prefs.getBoolean(KEY_AUTO_SPEAK, true))
        private set
    var ttsSpeed by mutableStateOf(prefs.getFloat(KEY_SPEED, 0.9f))
        private set

    /** 主题：0 跟随系统 / 1 浅色 / 2 深色 */
    var themeMode by mutableStateOf(prefs.getInt(KEY_THEME, 0))
        private set

    /** 学完一轮后是否进入拼写测试 */
    var spellTestEnabled by mutableStateOf(prefs.getBoolean(KEY_SPELL_TEST, true))
        private set

    /** 复数展示：false = 完整形式（die Männer），true = 教材式词尾（¨-er） */
    var pluralSuffixOnly by mutableStateOf(prefs.getBoolean(KEY_PLURAL_SUFFIX, false))
        private set

    /** 供界面直接使用的复数展示方式 */
    val pluralStyle: PluralStyle
        get() = if (pluralSuffixOnly) PluralStyle.SUFFIX else PluralStyle.FULL

    // ---- 查词
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Word>>(emptyList())
        private set

    /** 最近查过的词（最近在前），存本地偏好 */
    var searchHistory by mutableStateOf(loadSearchHistory())
        private set
    var searching by mutableStateOf(false)
        private set
    var onlineResult by mutableStateOf<OnlineDict.Result?>(null)
        private set
    var onlineLoading by mutableStateOf(false)
        private set

    // ---- 学习会话
    private val queue = mutableListOf<WordWithProgress>()
    var current by mutableStateOf<WordWithProgress?>(null)
        private set
    var revealed by mutableStateOf(false)
        private set
    var sessionDone by mutableStateOf(0)
        private set
    var sessionTotal by mutableStateOf(0)
        private set

    /**
     * 本次学习中每个词「连续认识」的次数。
     * 只存在内存里：换一轮学习重新计，跨会话不保留。
     */
    private val streakMap = mutableMapOf<Long, Int>()

    /** 当前卡片的连续认识次数，供卡片上的进度点使用 */
    var currentStreak by mutableStateOf(0)
        private set

    /** 正在组装学习队列，用于避免切换到学习页时闪出完成页 */
    var sessionLoading by mutableStateOf(false)
        private set

    /** 本轮学过的词，用于结束后的拼写测试 */
    var sessionWords by mutableStateOf<List<Word>>(emptyList())
        private set

    // ---- 拼写测试
    var spellActive by mutableStateOf(false)
        private set
    var spellInput by mutableStateOf("")
        private set
    /** 当前题是否已提交判定 */
    var spellChecked by mutableStateOf(false)
        private set
    var spellLastCorrect by mutableStateOf(false)
        private set

    /** 已拼写通过的词数（拼对才算过） */
    var spellPassed by mutableStateOf(0)
        private set
    /** 第一次就拼对的词数 */
    var spellFirstTry by mutableStateOf(0)
        private set
    /** 累计尝试次数 */
    var spellAttempts by mutableStateOf(0)
        private set
    /** 被跳过的词数 */
    var spellSkipped by mutableStateOf(0)
        private set

    /**
     * 待拼写队列：拼错的词会挪到队尾重测，直到拼对。
     *
     * 必须用 SnapshotStateList：普通 MutableList 改动不会触发重组，
     * 界面会一直停在旧词上，而比对却已经换成新词，表现就是「怎么拼都判错」。
     */
    private val spellQueue = mutableStateListOf<Word>()
    private val spellFailed = mutableSetOf<Long>()

    /** 收藏 */
    var starred by mutableStateOf<List<Word>>(emptyList())
        private set
    var starState by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** 已打卡日期（yyyy-MM-dd），用于日历 */
    var checkinDays by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 已掌握的词 */
    var masteredWords by mutableStateOf<List<Word>>(emptyList())
        private set

    /** 已学习但未掌握的词 */
    var learnedWords by mutableStateOf<List<Word>>(emptyList())
        private set

    /** 当前使用的自建单词书 id；为 null 表示按内置书的等级范围学习 */
    var currentBookId by mutableStateOf(prefs.getString(KEY_CURRENT_BOOK, null))
        private set

    /** 当前书的词条（仅自建书有） */
    var bookWords by mutableStateOf<List<Word>>(emptyList())
        private set

    /** 用户自建单词书 */
    var customBooks by mutableStateOf(loadBooks())
        private set

    /** 各本书的词数（自建书来自 book_words，内置书来自等级统计） */
    var bookCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** 内置 + 自建，供设置页展示 */
    val allBooks: List<WordBook> get() = WordBook.BUILTINS + customBooks

    /** 当前单词书 */
    val currentBook: WordBook?
        get() = currentBookId?.let { id -> customBooks.firstOrNull { it.id == id } }

    init {
        speaker.init()
        speaker.updateSpeed(ttsSpeed)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SeedLoader.importIfNeeded(app, db)
            }
            refresh()
            initializing = false
        }
    }

    // ---------------------------------------------------------------- 刷新

    fun refresh() {
        viewModelScope.launch {
            stats = withContext(Dispatchers.IO) { db.stats(levels, currentBookId) }
            levelCounts = withContext(Dispatchers.IO) { db.countByLevel() }
            totalWords = withContext(Dispatchers.IO) { db.countWords() }
            bookCounts = withContext(Dispatchers.IO) {
                customBooks.associate { it.id to db.bookWordCount(it.id) }
            }
            loadStarred()
            loadCheckins()
            loadRecords()
            loadBookWords()
        }
    }

    fun loadStarred() {
        viewModelScope.launch {
            val (list, ids) = withContext(Dispatchers.IO) {
                val l = db.starredWords()
                l to l.map { it.id }.toSet()
            }
            starred = list
            starState = ids
        }
    }

    fun loadCheckins() {
        viewModelScope.launch {
            checkinDays = withContext(Dispatchers.IO) { db.loggedDays() }
        }
    }

    /** 加载已学习 / 已掌握列表 */
    fun loadRecords() {
        viewModelScope.launch {
            val pair = withContext(Dispatchers.IO) {
                db.learnedWords() to db.masteredWords()
            }
            learnedWords = pair.first
            masteredWords = pair.second
        }
    }

    /** 标记或取消「已掌握」 */
    fun setMastered(word: Word, mastered: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.setMastered(word.id, mastered) }
            refresh()
            loadRecords()
        }
    }

    fun isMastered(wordId: Long): Boolean = masteredWords.any { it.id == wordId }

    // ---------------------------------------------------------------- 查词

    /** 记录一次查词（去重、最近在前、最多保留 20 条） */
    fun recordSearch(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val next = (listOf(t) + searchHistory.filter { !it.equals(t, ignoreCase = true) }).take(20)
        searchHistory = next
        prefs.edit().putString(KEY_SEARCH_HISTORY, next.joinToString(HISTORY_SEP)).apply()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun onQueryChange(text: String) {
        query = text
        onlineResult = null
        if (text.isBlank()) {
            results = emptyList()
            return
        }
        viewModelScope.launch {
            searching = true
            val list = withContext(Dispatchers.IO) { db.search(text, levels) }
            results = list
            searching = false
        }
    }

    /** 本地无结果时手动触发在线查询 */
    fun lookupOnline() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            onlineLoading = true
            onlineResult = OnlineDict.lookupGerman(q)
            onlineLoading = false
        }
    }

    /** 把在线查询结果保存为自定义词条 */
    fun saveOnlineAsWord(level: String = "A1") {
        val r = onlineResult
        val q = query.trim()
        if (r !is OnlineDict.Result.Success || q.isEmpty()) return
        viewModelScope.launch {
            val word = Word(
                word = q,
                lemma = q.removePrefix("der ").removePrefix("die ").removePrefix("das "),
                pos = "",
                gender = null,
                plural = null,
                zh = r.text,
                en = null,
                example = null,
                exampleZh = null,
                level = level,
                tags = "在线收录",
                source = Word.SRC_ONLINE
            )
            withContext(Dispatchers.IO) { db.insertWords(listOf(word)) }
            refresh()
            onQueryChange(q)
        }
    }

    fun toggleStar(wordId: Long) {
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { db.toggleStar(wordId) }
            starState = if (newState) starState + wordId else starState - wordId
            loadStarred()
        }
    }

    fun isStarred(wordId: Long): Boolean = starState.contains(wordId)

    // ---------------------------------------------------------------- 学习

    /** 学习会话模式：只复习、只学新词、或两者混合 */
    enum class SessionMode { MIXED, REVIEW, NEW }

    /**
     * 开始一轮学习。
     * REVIEW 只取到期词，NEW 只取新词，MIXED 先复习再用新词补足每轮词数。
     */
    fun startSession(mode: SessionMode = SessionMode.MIXED) {
        if (sessionLoading) return
        sessionLoading = true
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val book = currentBookId
                when (mode) {
                    SessionMode.REVIEW -> db.dueWords(now, levels, sessionSize, book)
                    SessionMode.NEW -> db.newWords(levels, sessionSize, book)
                        .map { WordWithProgress(it, Progress(it.id)) }
                    SessionMode.MIXED -> {
                        val due = db.dueWords(now, levels, sessionSize, book)
                        val remaining = (sessionSize - due.size).coerceAtLeast(0)
                        val fresh = if (remaining == 0) emptyList()
                        else db.newWords(levels, remaining, book)
                        due + fresh.map { WordWithProgress(it, Progress(it.id)) }
                    }
                }
            }
            sessionLoading = false
            if (items.isEmpty()) return@launch
            queue.clear()
            queue.addAll(items)
            streakMap.clear()
            currentStreak = 0
            sessionDone = 0
            sessionTotal = queue.size
            sessionWords = emptyList()
            spellActive = false
            revealed = false
            nextCard()
        }
    }

    private fun nextCard() {
        val item = queue.removeFirstOrNull()
        current = item
        currentStreak = item?.let { streakMap[it.word.id] ?: 0 } ?: 0
        if (item == null) {
            revealed = false
            refresh()
            // 一轮学完，按设置进入拼写测试
            if (spellTestEnabled && sessionWords.isNotEmpty()) beginSpelling()
        }
    }

    fun reveal() {
        revealed = true
    }

    /**
     * 把当前词标记为「已掌握」：此后它不会再出现在复习队列里。
     * 直接进入下一张，本张算作已完成。
     */
    fun masterCurrent() {
        val item = current ?: return
        revealed = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.setMastered(item.word.id, true) }
            queue.removeAll { it.word.id == item.word.id }
            // 已掌握的词不再参与拼写测试
            sessionDone++
            nextCard()
        }
    }

    fun rate(rating: Rating) {
        val item = current ?: return
        // 先同步合上背面：评分要写数据库，若等写完再复位，
        // 卡片会弹回原位并停留几百毫秒，期间释义一直露着
        revealed = false
        viewModelScope.launch {
            val isNew = item.progress.state == 0
            val updated = Sm2.update(item.progress, rating)
            withContext(Dispatchers.IO) {
                db.saveProgress(updated)
                db.logReview(Calendar0.todayString(), isNew)
            }

            // 只有「认识」算一次通过；「模糊」「忘了」把连续计数清零
            val positive = rating == Rating.GOOD
            val streak = if (positive) (streakMap[item.word.id] ?: 0) + 1 else 0
            streakMap[item.word.id] = streak

            if (positive && streak >= PASS_STREAK) {
                // 连续认识达标，本词才算过：移出队列，计入进度与拼写测试
                if (sessionWords.none { it.id == item.word.id }) {
                    sessionWords = sessionWords + item.word
                }
                sessionDone++
            } else {
                // 没达标：随机插回队列靠后的位置，隔几张卡再判一次
                enqueueLater(WordWithProgress(item.word, updated))
            }
            nextCard()
        }
    }

    /**
     * 把未达标的词随机插回队列。
     *
     * 位置取在当前词之后至少隔一个的位置，避免同一张卡紧接着重复出现；
     * 越靠后越容易再次遇到，但不固定顺序，防止靠位置记忆单词。
     */
    private fun enqueueLater(item: WordWithProgress) {
        if (queue.isEmpty()) {
            queue.add(item)
            return
        }
        val min = 1.coerceAtMost(queue.size)
        val index = min + Random.nextInt(queue.size - min + 1)
        queue.add(index, item)
    }

    fun speak(text: String) = speaker.speak(text)

    fun speakWord(w: Word) {
        // 名词带冠词朗读更接近真实语流，且能听出词性
        speaker.speak(w.word)
    }

    // ---------------------------------------------------------------- 拼写测试

    /** 当前要拼写的词 */
    val spellWord: Word? get() = spellQueue.firstOrNull()

    val spellTotal: Int get() = sessionWords.size

    /** 剩余待测词数，用于最后一题的按钮文案 */
    val spellRemaining: Int get() = spellQueue.size

    /** 队列清空即结束：拼对出队、跳过的也出队 */
    val spellFinished: Boolean get() = spellTotal > 0 && spellQueue.isEmpty()

    fun beginSpelling() {
        if (sessionWords.isEmpty()) return
        spellActive = true
        spellQueue.clear()
        spellQueue.addAll(sessionWords)
        spellFailed.clear()
        spellPassed = 0
        spellFirstTry = 0
        spellAttempts = 0
        spellSkipped = 0
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
    }

    fun onSpellInput(text: String) {
        spellInput = text
    }

    fun checkSpelling() {
        if (spellChecked) return
        val w = spellWord ?: return
        val ok = SpellTest.matches(spellInput, w)
        spellLastCorrect = ok
        spellChecked = true
        spellAttempts++
        if (ok) {
            spellPassed++
            if (w.id !in spellFailed) spellFirstTry++
        } else {
            spellFailed.add(w.id)
        }
    }

    /** 提交后推进：拼对的出队，拼错的挪到队尾稍后重测 */
    fun nextSpelling() {
        if (!spellChecked) return
        val w = spellQueue.firstOrNull() ?: return
        if (spellLastCorrect) {
            spellQueue.removeAt(0)
        } else {
            // 拼错：移到队尾，继续测下一个，等这轮其余词测完再回来
            spellQueue.removeAt(0)
            spellQueue.add(w)
        }
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
    }

    /** 跳过当前词：不计入通过，直接出队，结果页会统计跳过数量 */
    fun skipSpelling() {
        if (spellQueue.isEmpty()) return
        spellSkipped++
        spellQueue.removeAt(0)
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
    }

    fun exitSpelling() {
        spellActive = false
        spellQueue.clear()
        spellFailed.clear()
        spellPassed = 0
        spellFirstTry = 0
        spellAttempts = 0
        spellSkipped = 0
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
    }

    // ---------------------------------------------------------------- 学习记录

    /** 清除全部学习进度，所有词回到「未学」状态。返回清除的条数。 */
    fun resetAllProgress(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { db.resetAllProgress() }
            refresh()
            onDone(n)
        }
    }

    /** 清除单个词的学习进度 */
    fun resetWordProgress(wordId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.resetWordProgress(wordId) }
            refresh()
            loadRecords()
        }
    }

    /** 移除单个词的学习记录（回到未学状态） */
    fun removeLearningRecord(word: Word) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.removeLearningRecord(word.id) }
            refresh()
            loadRecords()
        }
    }

    // ---------------------------------------------------------------- 设置

    fun toggleLevel(level: String) {
        val next = if (levels.contains(level)) levels - level else levels + level
        if (next.isEmpty()) return
        levels = next
        // 手动调等级即退出自建书模式，回到按等级学习
        currentBookId = null
        prefs.edit()
            .putString(KEY_LEVELS, next.joinToString(","))
            .remove(KEY_CURRENT_BOOK)
            .apply()
        refresh()
    }

    /** 选择单词书：内置书设置等级范围，自建书按书内词集合学习 */
    fun selectBook(book: WordBook) {
        if (book.builtin) {
            currentBookId = null
            levels = book.levels
            prefs.edit()
                .putString(KEY_LEVELS, book.levels.joinToString(","))
                .remove(KEY_CURRENT_BOOK)
                .apply()
        } else {
            currentBookId = book.id
            prefs.edit().putString(KEY_CURRENT_BOOK, book.id).apply()
        }
        refresh()
    }

    // ---------------------------------------------------------------- 单词书管理

    fun addBook(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val book = WordBook(java.util.UUID.randomUUID().toString(), clean)
        customBooks = customBooks + book
        saveBooks()
        selectBook(book)
    }

    fun updateBook(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        customBooks = customBooks.map {
            if (it.id == id) it.copy(name = clean) else it
        }
        saveBooks()
    }

    fun deleteBook(id: String) {
        if (customBooks.none { it.id == id }) return
        customBooks = customBooks.filterNot { it.id == id }
        saveBooks()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteBookWords(id) }
        }
        if (currentBookId == id) {
            currentBookId = null
            prefs.edit().remove(KEY_CURRENT_BOOK).apply()
        }
        refresh()
    }

    // ---------------------------------------------------------------- 书内词

    fun loadBookWords() {
        val id = currentBookId
        if (id == null) {
            bookWords = emptyList()
            return
        }
        viewModelScope.launch {
            bookWords = withContext(Dispatchers.IO) { db.bookWords(id) }
        }
    }

    /** 往指定书里加词，返回新增条数 */
    fun addWordsToBook(bookId: String, words: List<Word>, onDone: (Int) -> Unit = {}) {
        if (words.isEmpty()) return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                db.addWordsToBook(bookId, words.map { it.id })
            }
            refresh()
            onDone(n)
        }
    }

    fun removeFromBook(word: Word) {
        val id = currentBookId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.removeWordFromBook(id, word.id) }
            refresh()
        }
    }

    /** 词是否在当前书里 */
    fun isInCurrentBook(wordId: Long): Boolean = bookWords.any { it.id == wordId }

    /** 管理单词书时用的搜索（不受全局查词状态影响） */
    fun searchForBook(text: String, onResult: (List<Word>) -> Unit) {
        if (text.isBlank()) {
            onResult(emptyList())
            return
        }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                db.search(text, ALL_LEVELS.toSet(), 40)
            }
            onResult(list)
        }
    }

    private fun saveBooks() {
        val arr = JSONArray()
        customBooks.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                }
            )
        }
        prefs.edit().putString(KEY_BOOKS, arr.toString()).apply()
    }

    private fun loadBooks(): List<WordBook> {
        val raw = prefs.getString(KEY_BOOKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(WordBook(o.getString("id"), o.getString("name")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return raw.split(HISTORY_SEP).filter { it.isNotBlank() }
    }

    fun updateSessionSize(n: Int) {
        sessionSize = n.coerceIn(5, 100)
        prefs.edit().putInt(KEY_SESSION_SIZE, sessionSize).apply()
    }

    fun updateThemeMode(mode: Int) {
        themeMode = mode.coerceIn(0, 2)
        prefs.edit().putInt(KEY_THEME, themeMode).apply()
    }

    fun updateSpellTest(v: Boolean) {
        spellTestEnabled = v
        prefs.edit().putBoolean(KEY_SPELL_TEST, v).apply()
    }

    /** 切换复数展示方式：完整形式 / 教材式词尾 */
    fun updatePluralSuffixOnly(v: Boolean) {
        pluralSuffixOnly = v
        prefs.edit().putBoolean(KEY_PLURAL_SUFFIX, v).apply()
    }

    fun updateAutoSpeak(v: Boolean) {
        autoSpeak = v
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, v).apply()
    }

    fun updateTtsSpeed(v: Float) {
        ttsSpeed = v
        speaker.updateSpeed(v)
        prefs.edit().putFloat(KEY_SPEED, v).apply()
    }

    fun getWord(id: Long, onResult: (Word?) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { db.getWord(id) })
        }
    }

    fun progressFor(wordId: Long, onResult: (Progress) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { db.progressOf(wordId) })
        }
    }

    /**
     * 导入词表并**新建一本单词书**，词全部挂到这本书下。
     *
     * 返回 (新增词条数, 书名)；解析失败时词条数为 -1。
     * 同名书已存在时复用，避免反复导入产生一堆同名词书。
     */
    fun importCustomWords(jsonText: String, onDone: (Int, String) -> Unit) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val words = SeedLoader.parseJson(jsonText)
                        .map { it.copy(source = Word.SRC_CUSTOM) }
                    words to SeedLoader.parseName(jsonText)
                }.getOrNull()
            }
            if (parsed == null || parsed.first.isEmpty()) {
                onDone(-1, "")
                return@launch
            }
            val (words, jsonName) = parsed
            val bookName = jsonName?.takeIf { it.isNotBlank() } ?: "导入词表"
            // 保留原始大小写：sie（他们）与 Sie（您）是两个词，
            // 统一转小写会少建一条关联（实测 118 条只入书 117 条）
            val lemmas = words.map { it.lemma }.distinct()

            val inserted = withContext(Dispatchers.IO) {
                val existing = db.existingLemmas(lemmas)
                val fresh = words.filter { it.lemma.lowercase() !in existing }
                db.insertWords(fresh)
                fresh.size
            }

            // 词条 id 包含词库里本来就有的，这样整份词表都能归到书里
            val ids = withContext(Dispatchers.IO) { db.wordIdsByLemmas(lemmas) }

            val book = customBooks.firstOrNull { it.name == bookName } ?: run {
                val created = WordBook(java.util.UUID.randomUUID().toString(), bookName)
                customBooks = customBooks + created
                saveBooks()
                created
            }

            withContext(Dispatchers.IO) { db.addWordsToBook(book.id, ids) }
            selectBook(book)
            refresh()
            onDone(inserted, book.name)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speaker.shutdown()
    }

    private fun loadLevels(): Set<String> {
        val raw = prefs.getString(KEY_LEVELS, null) ?: return DEFAULT_LEVELS
        val set = raw.split(",").filter { it.isNotBlank() }.toSet()
        return set.ifEmpty { DEFAULT_LEVELS }
    }

    companion object {
        /** 一个词需要连续认识多少次才算通过 */
        const val PASS_STREAK = 3

        private const val PREFS_NAME = "wortschatz_prefs"
        private const val KEY_LEVELS = "levels"
        private const val KEY_SESSION_SIZE = "session_size"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_SPEED = "tts_speed"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SPELL_TEST = "spell_test"
        private const val KEY_PLURAL_SUFFIX = "plural_suffix_only"
        private const val KEY_BOOKS = "custom_books"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val HISTORY_SEP = "\u001F"
        private const val KEY_CURRENT_BOOK = "current_book_id"
        val ALL_LEVELS = listOf("A1", "A2", "B1", "B2")
        val DEFAULT_LEVELS = setOf("A1", "A2", "B1", "B2")
    }
}
