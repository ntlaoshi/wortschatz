package de.wortschatz.app.tts

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 德语发音，三级降级策略：
 *
 * 1. 内置离线音频（assets/voice）：词库里的每个词都预置了真人级德语语音，
 *    不依赖任何系统组件，飞行模式也能读，这是主路径。
 * 2. 在线合成：词库外的新词（在线查询命中的词）首次联网时下载并缓存到本地，
 *    此后同样可离线播放。
 * 3. 系统 TTS：仅作为例句朗读的兜底，设备装有德语语音包时才可用。
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val assets = context.assets
    private val onlineDir = File(context.cacheDir, "voice_online").apply { mkdirs() }

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null

    /** 系统 TTS 引擎是否初始化完成 */
    var ready by mutableStateOf(false)
        private set

    /** 系统是否装有德语语音包（仅影响例句朗读） */
    var germanAvailable by mutableStateOf(false)
        private set

    /** 当前是否正在发声，用于按钮动效 */
    var speaking by mutableStateOf(false)
        private set

    /** 内置离线音频的词条数量 */
    var offlineCount by mutableIntStateOf(0)
        private set

    /** 最近一次发音失败的原因，null 表示无错误 */
    var lastError by mutableStateOf<String?>(null)
        private set

    /** 语速，0.5x ~ 1.5x */
    var speed by mutableFloatStateOf(0.95f)
        private set

    /** 内置音频文件名索引，首次访问时从 assets 读取 */
    private val clips: Set<String> by lazy {
        runCatching { assets.list("voice")?.toHashSet() ?: emptySet() }
            .getOrDefault(emptySet())
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun init() {
        // 音频索引有数千个文件名，放在后台读取，避免拖慢冷启动
        thread(name = "voice-index") {
            val count = clips.size
            mainHandler.post { offlineCount = count }
        }
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val result = engine.setLanguage(Locale.GERMANY)
                germanAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!germanAvailable) {
                    val fallback = engine.setLanguage(Locale.GERMAN)
                    germanAvailable = fallback != TextToSpeech.LANG_MISSING_DATA &&
                        fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                engine.setSpeechRate(speed)
                engine.setPitch(1.0f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        speaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        speaking = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        speaking = false
                    }
                })
                ready = true
            } else {
                ready = false
                germanAvailable = false
            }
        }
    }

    /** 该词是否有内置离线音频 */
    fun hasOffline(word: String): Boolean = clips.contains(fileKey(word))

    /**
     * 朗读。单词优先走内置音频，整句或未收录的词走系统 TTS / 在线合成。
     */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        lastError = null
        stop()

        val key = fileKey(clean)
        if (clips.contains(key)) {
            android.util.Log.d("Speaker", "speak=$clean -> offline asset $key")
            playAsset("voice/$key")
            return
        }
        val cached = File(onlineDir, key)
        if (cached.exists() && cached.length() > 512) {
            android.util.Log.d("Speaker", "speak=$clean -> cached file $key")
            playFile(cached)
            return
        }
        android.util.Log.d(
            "Speaker",
            "speak=$clean -> fallback (ttsReady=$ready german=$germanAvailable)"
        )
        // 单词走在线合成优先级高于系统 TTS：本机常缺德语语音包
        if (isSingleWord(clean)) {
            fetchOnlineThenPlay(clean, key)
            return
        }
        if (ready && germanAvailable) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "w_${System.nanoTime()}")
        } else {
            fetchOnlineThenPlay(clean, key)
        }
    }

    /** 只朗读单词本体，忽略失败（用于自动发音） */
    fun speakWord(word: String) = speak(word)

    private fun isSingleWord(text: String): Boolean =
        !text.contains(' ') || text.split(' ').count { it.isNotBlank() } <= 1

    private fun playAsset(path: String) {
        runCatching {
            val afd = assets.openFd(path)
            val mp = MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mp.prepare()
            afd.close()
            startPlayback(mp)
        }.onFailure {
            android.util.Log.w("Speaker", "asset 播放失败: $path", it)
            lastError = "音频文件读取失败"
            speaking = false
        }
    }

    private fun playFile(file: File) {
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            startPlayback(mp)
        }.onFailure {
            lastError = "音频播放失败"
            speaking = false
        }
    }

    private fun startPlayback(mp: MediaPlayer) {
        player = mp
        playingSpeed = speed
        runCatching {
            mp.playbackParams = mp.playbackParams.setSpeed(playingSpeed)
        }
        mp.setOnCompletionListener {
            speaking = false
            runCatching { it.release() }
            if (player === it) player = null
        }
        mp.setOnErrorListener { p, _, _ ->
            speaking = false
            runCatching { p.release() }
            if (player === p) player = null
            true
        }
        speaking = true
        mp.start()
    }

    private var playingSpeed = 1.0f

    private fun fetchOnlineThenPlay(text: String, key: String) {
        thread(name = "voice-fetch") {
            val file = File(onlineDir, key)
            val ok = runCatching {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://dict.youdao.com/dictvoice?audio=$encoded&le=de")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://dict.youdao.com/")
                }
                conn.inputStream.use { input ->
                    if (conn.responseCode == 200) {
                        file.outputStream().use { out -> input.copyTo(out) }
                        file.length() > 512
                    } else {
                        false
                    }
                }
            }.getOrDefault(false)

            mainHandler.post {
                if (ok) {
                    playFile(file)
                } else {
                    // 在线也拿不到时，退回系统 TTS
                    if (ready && germanAvailable) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w_${System.nanoTime()}")
                    } else {
                        lastError = "该词暂无发音"
                        speaking = false
                        // 未内置音频且联网也没取到时，明确告知，避免用户以为卡住
                        android.widget.Toast.makeText(
                            appContext, "该词暂无发音，换个词试试", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun stop() {
        speaking = false
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        tts?.stop()
    }

    fun updateSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(speed)
        // 正在播放时实时生效（Android 6.0+ 支持动态变速）
        if (playingSpeed != speed) {
            playingSpeed = speed
            runCatching { player?.playbackParams = player!!.playbackParams.setSpeed(speed) }
        }
    }

    fun clearError() {
        lastError = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        /** 把德语词转换为音频文件名，规则与生成脚本保持一致 */
        fun fileKey(raw: String): String {
            var s = raw.trim()
            s = s.replace(Regex("^(der|die|das)\\s+", RegexOption.IGNORE_CASE), "")
            s = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            s = s.replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
            s = Normalizer.normalize(s, Normalizer.Form.NFD)
            s = s.replace(Regex("\\p{Mn}+"), "")
            s = s.replace(Regex("[^A-Za-z0-9_\\-]+"), "_").trim('_')
            return s.lowercase() + ".mp3"
        }

        /** 跳转到系统 TTS 设置页，引导用户安装德语语音包 */
        fun ttsSettingsIntent(): Intent =
            Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
