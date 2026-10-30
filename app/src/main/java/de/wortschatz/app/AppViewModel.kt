package de.wortschatz.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.wortschatz.app.data.Calendar0
import de.wortschatz.app.data.OnlineDict
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

    // ---- 查词
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Word>>(emptyList())
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

    /** 正在组装学习队列，用于避免切换到学习页时闪出完成页 */
    var sessionLoading by mutableStateOf(false)
        private set

    /** 本轮学过的词，用于结束后的拼写测试 */
    var sessionWords by mutableStateOf<List<Word>>(emptyList())
        private set

    // ---- 拼写测试
    var spellActive by mutableStateOf(false)
        private set
    var spellIndex by mutableStateOf(0)
        private set
    var spellInput by mutableStateOf("")
        private set
    /** 当前题是否已提交判定 */
    var spellChecked by mutableStateOf(false)
        private set
    var spellLastCorrect by mutableStateOf(false)
        private set
    var spellResults by mutableStateOf<List<Boolean>>(emptyList())
        private set

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

    /** 用户自建单词书 */
    var customBooks by mutableStateOf(loadBooks())
        private set

    /** 内置 + 自建，供设置页展示 */
    val allBooks: List<WordBook> get() = WordBook.BUILTINS + customBooks

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
            val snapshot = withContext(Dispatchers.IO) {
                Triple(db.stats(levels), db.countByLevel(), db.countWords())
            }
            stats = snapshot.first
            levelCounts = snapshot.second
            totalWords = snapshot.third
            loadStarred()
            loadCheckins()
            loadRecords()
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
                when (mode) {
                    SessionMode.REVIEW -> db.dueWords(now, levels, sessionSize)
                    SessionMode.NEW -> db.newWords(levels, sessionSize)
                        .map { WordWithProgress(it, Progress(it.id)) }
                    SessionMode.MIXED -> {
                        val due = db.dueWords(now, levels, sessionSize)
                        val remaining = (sessionSize - due.size).coerceAtLeast(0)
                        val fresh = if (remaining == 0) emptyList()
                        else db.newWords(levels, remaining)
                        due + fresh.map { WordWithProgress(it, Progress(it.id)) }
                    }
                }
            }
            sessionLoading = false
            if (items.isEmpty()) return@launch
            queue.clear()
            queue.addAll(items)
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.setMastered(item.word.id, true) }
            queue.removeAll { it.word.id == item.word.id }
            // 已掌握的词不再参与拼写测试
            sessionDone++
            revealed = false
            nextCard()
        }
    }

    fun rate(rating: Rating) {
        val item = current ?: return
        viewModelScope.launch {
            val isNew = item.progress.state == 0
            val updated = Sm2.update(item.progress, rating)
            withContext(Dispatchers.IO) {
                db.saveProgress(updated)
                db.logReview(Calendar0.todayString(), isNew)
            }
            // 忘记的词在本轮末尾再出现一次，强化记忆
            if (rating == Rating.AGAIN) {
                queue.add(WordWithProgress(item.word, updated))
                sessionTotal = sessionTotal.coerceAtLeast(sessionDone + queue.size + 1)
            }
            if (sessionWords.none { it.id == item.word.id }) {
                sessionWords = sessionWords + item.word
            }
            sessionDone++
            revealed = false
            nextCard()
        }
    }

    fun speak(text: String) = speaker.speak(text)

    fun speakWord(w: Word) {
        // 名词带冠词朗读更接近真实语流，且能听出词性
        speaker.speak(w.word)
    }

    // ---------------------------------------------------------------- 拼写测试

    /** 当前要拼写的词 */
    val spellWord: Word? get() = sessionWords.getOrNull(spellIndex)

    val spellTotal: Int get() = sessionWords.size

    val spellCorrect: Int get() = spellResults.count { it }

    /** 是否已完成全部拼写题 */
    val spellFinished: Boolean get() = spellIndex >= sessionWords.size

    fun beginSpelling() {
        if (sessionWords.isEmpty()) return
        spellActive = true
        spellIndex = 0
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
        spellResults = emptyList()
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
        spellResults = spellResults + ok
    }

    fun nextSpelling() {
        if (!spellChecked) return
        spellIndex++
        spellInput = ""
        spellChecked = false
        spellLastCorrect = false
    }

    fun exitSpelling() {
        spellActive = false
        spellIndex = 0
        spellInput = ""
        spellChecked = false
        spellResults = emptyList()
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
        prefs.edit().putString(KEY_LEVELS, next.joinToString(",")).apply()
        refresh()
    }

    /** 选择单词书：整本替换当前学习范围 */
    fun selectBook(bookLevels: Set<String>) {
        if (bookLevels.isEmpty()) return
        levels = bookLevels
        prefs.edit().putString(KEY_LEVELS, bookLevels.joinToString(",")).apply()
        refresh()
    }

    // ---------------------------------------------------------------- 单词书管理

    fun addBook(name: String, bookLevels: Set<String>) {
        val clean = name.trim()
        if (clean.isEmpty() || bookLevels.isEmpty()) return
        val book = WordBook(java.util.UUID.randomUUID().toString(), clean, bookLevels)
        customBooks = customBooks + book
        saveBooks()
        selectBook(bookLevels)
    }

    fun updateBook(id: String, name: String, bookLevels: Set<String>) {
        val clean = name.trim()
        if (clean.isEmpty() || bookLevels.isEmpty()) return
        customBooks = customBooks.map {
            if (it.id == id) it.copy(name = clean, levels = bookLevels) else it
        }
        saveBooks()
        // 改的是当前正在用的书，学习范围要跟着变
        if (customBooks.firstOrNull { it.id == id }?.levels == levels) refresh()
    }

    fun deleteBook(id: String) {
        val removed = customBooks.firstOrNull { it.id == id } ?: return
        customBooks = customBooks.filterNot { it.id == id }
        saveBooks()
        if (removed.levels == levels) selectBook(DEFAULT_LEVELS)
    }

    private fun saveBooks() {
        val arr = JSONArray()
        customBooks.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("levels", b.levels.joinToString(","))
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
                    val lv = o.optString("levels").split(",")
                        .filter { it.isNotBlank() }.toSet()
                    if (lv.isNotEmpty()) {
                        add(WordBook(o.getString("id"), o.getString("name"), lv))
                    }
                }
            }
        }.getOrDefault(emptyList())
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

    /** 导入用户自定义词表（JSON 格式），返回新增条数，失败返回 -1 */
    fun importCustomWords(jsonText: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val words = SeedLoader.parseJson(jsonText).map {
                        it.copy(source = Word.SRC_CUSTOM)
                    }
                    if (words.isEmpty()) -1 else db.insertWords(words)
                } catch (e: Exception) {
                    -1
                }
            }
            refresh()
            onDone(count)
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
        private const val PREFS_NAME = "wortschatz_prefs"
        private const val KEY_LEVELS = "levels"
        private const val KEY_SESSION_SIZE = "session_size"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_SPEED = "tts_speed"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SPELL_TEST = "spell_test"
        private const val KEY_BOOKS = "custom_books"
        val ALL_LEVELS = listOf("A1", "A2", "B1", "B2")
        val DEFAULT_LEVELS = setOf("A1", "A2", "B1", "B2")
    }
}
