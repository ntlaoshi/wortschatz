package de.wortschatz.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 在线兜底查询。
 *
 * 当本地词库查不到时，调用 MyMemory 免费翻译接口获取中文释义。
 * 该接口无需 API Key，匿名额度约 1000 次/天，足以作为兜底使用。
 * 结果会明确标注来源为"在线翻译"，不与内置词库混淆。
 */
object OnlineDict {

    private const val ENDPOINT = "https://api.mymemory.translated.net/get"

    sealed interface Result {
        data class Success(val text: String, val provider: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun lookupGerman(rawQuery: String): Result = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return@withContext Result.Failure("请输入要查询的单词")

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("$ENDPOINT?q=$encoded&langpair=de%7Czh-CN")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WortschatzApp/1.0")
            }
            if (conn.responseCode != 200) {
                return@withContext Result.Failure("网络请求失败（HTTP ${conn.responseCode}）")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val status = root.optInt("responseStatus", 0)
            val data = root.optJSONObject("responseData")
            val text = data?.optString("translatedText").orEmpty().trim()

            when {
                status != 200 -> Result.Failure("查询服务暂时不可用")
                text.isEmpty() -> Result.Failure("未找到该词的在线释义")
                text.equals(query, ignoreCase = true) -> Result.Failure("在线服务未能给出中文释义")
                // MyMemory 常在无结果时返回这句提示文案
                text.contains("MYMEMORY WARNING", ignoreCase = true) ->
                    Result.Failure("在线翻译额度已用完，请稍后再试")
                else -> Result.Success(text, "MyMemory 在线翻译")
            }
        } catch (e: Exception) {
            Result.Failure("网络异常：${e.message ?: "无法连接"}")
        } finally {
            conn?.disconnect()
        }
    }
}
