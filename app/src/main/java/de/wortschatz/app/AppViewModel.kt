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
import de.wortschatz.app.data.StudyStats
import de.wortschatz.app.data.Word
import de.wortschatz.app.data.WordDb
import de.wortschatz.app.data.WordWithProgress
import de.wortschatz.app.tts.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var dailyNewTarget by mutableStateOf(prefs.getInt(KEY_DAILY_NEW, 20))
        private set
    var autoSpeak by mutableStateOf(prefs.getBoolean(KEY_AUTO_SPEAK, true))
        private set
    var ttsSpeed by mutableStateOf(prefs.getFloat(KEY_SPEED, 0.9f))
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

    // ---- 收藏
    var starred by mutableStateOf<List<Word>>(emptyList())
        private set
    var starState by mutableStateOf<Set<Long>>(emptySet())
        private set

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

    /** 开始一轮学习：先复习到期词，再按每日目标补新词 */
    fun startSession() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val due = db.dueWords(now, levels, 200)
                val fresh = if (due.size >= dailyNewTarget) emptyList()
                else db.newWords(levels, dailyNewTarget - due.size)
                due + fresh.map { WordWithProgress(it, Progress(it.id)) }
            }
            queue.clear()
            queue.addAll(items)
            sessionDone = 0
            sessionTotal = queue.size
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
        }
    }

    fun reveal() {
        revealed = true
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

    // ---------------------------------------------------------------- 设置

    fun toggleLevel(level: String) {
        val next = if (levels.contains(level)) levels - level else levels + level
        if (next.isEmpty()) return
        levels = next
        prefs.edit().putString(KEY_LEVELS, next.joinToString(",")).apply()
        refresh()
    }

    fun setDailyNew(n: Int) {
        dailyNewTarget = n.coerceIn(5, 100)
        prefs.edit().putInt(KEY_DAILY_NEW, dailyNewTarget).apply()
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
        private const val KEY_DAILY_NEW = "daily_new"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_SPEED = "tts_speed"
        val ALL_LEVELS = listOf("A1", "A2", "B1", "B2")
        val DEFAULT_LEVELS = setOf("A1", "A2", "B1", "B2")
    }
}
