package de.wortschatz.app.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 德语 TTS 朗读封装。
 *
 * 使用系统自带 TTS 引擎（Google TTS / 厂商引擎）的 de-DE 语音，
 * 装好德语语音包后可完全离线朗读，无需网络与额外费用。
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    /** 引擎初始化完成 */
    var ready by mutableStateOf(false)
        private set

    /** 系统是否安装了德语语音数据 */
    var germanAvailable by mutableStateOf(false)
        private set

    /** 当前是否正在朗读，用于按钮动效 */
    var speaking by mutableStateOf(false)
        private set

    /** 语速，0.5x ~ 1.5x */
    var speed by mutableFloatStateOf(0.9f)
        private set

    private var pendingText: String? = null

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val result = engine.setLanguage(Locale.GERMANY)
                    germanAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!germanAvailable) {
                        // 德语不可用时退回德语（通用），再不行就用默认
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
                    pendingText?.let { speak(it) }
                    pendingText = null
                }
            } else {
                ready = false
                germanAvailable = false
            }
        }
    }

    /** 朗读文本。德语中名词冠词与单词分读会失真，调用方应传入完整短语。 */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pendingText = clean
            init()
            return
        }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "w_${System.nanoTime()}")
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun updateSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(speed)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        /** 跳转到系统 TTS 设置页，引导用户安装德语语音包 */
        fun ttsSettingsIntent(): Intent =
            Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** 直接唤起语音数据安装界面 */
        fun installTtsDataIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
