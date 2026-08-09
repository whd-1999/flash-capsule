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
        if (settings.apiKey.isBlank()) {
            settings.aiError = "未配置 API Key"
            return@withContext null
        }
        try {
            val payload = JSONObject()
                .put("model", "deepseek-chat")
                .put("temperature", 0.2)
                .put("max_tokens", 150)
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
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                settings.aiError = "API 错误 HTTP ${conn.responseCode}: ${err.take(120)}"
                return@withContext null
            }
            val content = conn.inputStream.bufferedReader().use { it.readText() }
                .let { body ->
                    JSONObject(body).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                }
            val result = parse(content)
            if (result.title.isBlank()) {
                settings.aiError = "AI 返回空标题"
                null
            } else {
                settings.aiError = ""
                result
            }
        } catch (e: Exception) {
            settings.aiError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    /** 容错解析：LLM 可能返回 markdown 包裹或前后带杂音，提取 JSON 块再解析。 */
    private fun parse(content: String): Enrichment {
        val jsonStr = extractJson(content)
        val j = JSONObject(jsonStr)
        val title = j.optString("title", "").trim()
        val color = runCatching { ColorTag.valueOf(j.optString("colorTag", "").uppercase()) }.getOrNull()
        val tags = j.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.take(3)
        } ?: emptyList()
        val kind = j.optString("kind", "note").trim().lowercase()
            .let { if (it in setOf("note", "search", "reminder", "calendar")) it else "note" }
        return Enrichment(title, color, tags, kind)
    }

    private fun extractJson(s: String): String {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    companion object {
        private const val API = "https://api.deepseek.com/chat/completions"
        private const val SYSTEM_PROMPT =
            "你是一个闪念胶囊整理助手。给定一条闪念内容，判断它最像哪种（kind）：" +
            "note=纯记录/备忘；search=想去搜索了解的内容；reminder=待办/要记住去做的事；calendar=有时间地点的事件。" +
            "只输出一个 JSON 对象，不要任何其他文字、不要 markdown 代码块：" +
            "{\"title\":\"不超过12个字的中文摘要标题\",\"colorTag\":\"RED|ORANGE|YELLOW|GREEN|BLUE|PURPLE|GRAY 之一\"," +
            "\"tags\":[\"短标签\"],\"kind\":\"note|search|reminder|calendar 之一\"}，tags 最多 3 个。"
    }
}
