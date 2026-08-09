package com.flashcapsule.ai

import com.flashcapsule.data.Settings
import com.flashcapsule.model.ColorTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** DeepSeek API 实现：给一段文字出标题 + 颜色分类 + 标签。零新依赖，裸 HttpURLConnection。 */
class DeepSeekEnricher(private val settings: Settings) : CapsuleEnricher {

    override suspend fun enrich(text: String): Enrichment? = withContext(Dispatchers.IO) {
        runCatching {
            if (settings.apiKey.isBlank()) return@runCatching null
            val payload = JSONObject()
                .put("model", "deepseek-chat")
                .put("temperature", 0.2)
                .put("max_tokens", 120)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", text)))
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return@runCatching null
            val content = conn.inputStream.bufferedReader().use { it.readText() }
                .let { body ->
                    JSONObject(body).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                }
            parse(content)
        }.getOrNull()
    }

    private fun parse(content: String): Enrichment {
        val j = JSONObject(content)
        val title = j.optString("title", "").trim()
        val color = runCatching { ColorTag.valueOf(j.optString("colorTag", "").uppercase()) }.getOrNull()
        val tags = j.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.take(3)
        } ?: emptyList()
        return Enrichment(title, color, tags)
    }

    companion object {
        private const val API = "https://api.deepseek.com/chat/completions"
        private const val SYSTEM_PROMPT =
            "你是一个闪念胶囊整理助手。给定一条闪念内容，只输出一个 JSON（不要任何其他文字）：" +
            "{\"title\":\"不超过12个字的中文摘要标题\",\"colorTag\":\"RED|ORANGE|YELLOW|GREEN|BLUE|PURPLE|GRAY 之一\"," +
            "\"tags\":[\"短标签\"]}，tags 最多 3 个。"
    }
}
