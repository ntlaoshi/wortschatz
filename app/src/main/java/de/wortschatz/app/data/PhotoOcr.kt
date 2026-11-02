package de.wortschatz.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 从照片里识别德语单词。
 *
 * 使用 ML Kit 的**内置**拉丁字母识别模型（`text-recognition-bundled-common`），
 * 模型随 APK 分发，不需要 Google Play 服务，也不需要联网。
 */
object PhotoOcr {

    /** 德语词形：字母（含变音字母）、内部可带连字符 */
    private val TOKEN = Regex("[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß\\-]{1,29}")

    /**
     * 高频虚词，几乎每行都有，识别进来没有学习价值。
     *
     * 注意只放纯功能词：`bitte`、`danke` 这类虽然常出现在练习指令里，但本身就是教材词汇，
     * 早前误放在这里，导致它们永远进不了单词书（表现为「文本里明明有，导入却没有」）。
     */
    private val STOPWORDS = setOf(
        "der", "die", "das", "den", "dem", "des", "und", "oder", "aber", "ich", "du",
        "er", "sie", "es", "wir", "ihr", "mich", "dich", "sich", "uns", "euch",
        "ist", "sind", "bin", "bist", "seid", "war", "waren", "hat", "haben", "habe",
        "ein", "eine", "einen", "einem", "einer", "eines", "nicht", "kein", "keine",
        "zu", "im", "in", "am", "an", "auf", "mit", "von", "vom", "für", "bei", "aus",
        "nach", "vor", "über", "unter", "zum", "zur", "auch", "noch", "nur", "schon",
        "sehr", "wie", "was", "wer", "wo", "wann", "warum", "dass", "wenn", "dann",
        "hier", "dort"
    )

    /**
     * 扫描结果。
     *
     * [tokens] 是全部候选词（含功能词），[stops] 是其中命中停用词表的部分。
     * 哪些真的丢掉由调用方结合词库决定——见 AppViewModel.applyScan。
     */
    data class Scan(val tokens: List<String>, val stops: Set<String>)

    suspend fun recognize(context: Context, uri: Uri): Scan =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        if (cont.isActive) cont.resume(scan(result.text))
                    }
                    .addOnFailureListener { e ->
                        recognizer.close()
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            } catch (e: Exception) {
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    /**
     * 从文本里挑出候选德语词：去重、跳过虚词与纯数字/缩写。
     *
     * 被跳过的虚词数量单独返回，用于向用户解释「为什么识别出的词比看到的少」。
     */
    fun scan(text: String): Scan {
        val seen = LinkedHashSet<String>()
        val stops = mutableSetOf<String>()
        TOKEN.findAll(text).forEach { m ->
            val raw = m.value.trim('-')
            if (raw.length < 2) return@forEach
            // 全大写且很短的多为缩写或等级标记（A1、CD、DVD）
            if (raw.length <= 3 && raw.all { it.isUpperCase() }) return@forEach
            if (raw.lowercase() in STOPWORDS) stops.add(raw)
            seen.add(raw)
        }
        return Scan(seen.toList(), stops)
    }
}
