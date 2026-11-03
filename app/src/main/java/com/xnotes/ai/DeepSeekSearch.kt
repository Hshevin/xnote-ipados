package com.xnotes.ai

import java.net.URI
import org.json.JSONObject

/** One search result as the Search API returns it; [summary] is the model-facing excerpt, [snippet] the short one. */
data class AiWebSource(val url: String, val title: String = "", val snippet: String = "", val summary: String = "", val date: String = "")

/**
 * DeepSeek's own Search and Fetch APIs, one direct request each, as DeepSeek Harness's
 * `web-deepseek-api` uses them. The search key goes only to these official endpoints, never to the
 * chat provider; pages are fetched on DeepSeek's servers, so the device never opens a URL the model chose.
 */
object DeepSeekSearch {
    const val ENDPOINT = "https://api.deepseek.com/v1/tools/search"
    const val FETCH_ENDPOINT = "https://api.deepseek.com/v1/tools/fetch"
    /** Results per search, the service default DSH keeps. */
    const val COUNT = 10
    /** Longest query and `ori_question` the Search API accepts, in characters. */
    const val MAX_QUERY = 128

    /** [question] is the user's question that led to the search, sent as `ori_question` (whitespace folded, first 128 characters). */
    fun request(query: String, question: String = ""): JSONObject {
        val value = query.trim()
        require(value.isNotEmpty() && value.codePointCount(0, value.length) <= MAX_QUERY) { AiText.t(AiMsg.SEARCH_QUERY_LENGTH) }
        val body = JSONObject().put("query", value).put("count", COUNT).put("need_content", false)
        bounded(question)?.let { body.put("ori_question", it) }
        return body
    }

    private fun bounded(question: String): String? {
        val folded = question.replace(Regex("\\s+"), " ").trim()
        if (folded.isEmpty()) return null
        val end = if (folded.codePointCount(0, folded.length) <= MAX_QUERY) folded.length else folded.offsetByCodePoints(0, MAX_QUERY)
        return folded.substring(0, end)
    }

    /** Valid sources in provider order; entries with an unusable URL are skipped, a malformed reply is an error. */
    fun sources(response: String): List<AiWebSource> {
        val json = JSONObject(response)
        check(!json.has("error")) { AiText.t(AiMsg.SEARCH_RETURNED_ERROR) }
        val results = json.getJSONArray("results")
        return (0 until results.length()).mapNotNull { index ->
            val item = results.getJSONObject(index)
            val raw = item.getString("url")
            if (!usableUrl(raw)) null
            else AiWebSource(raw, item.optString("title").take(300), (item.opt("snippet") as? String).orEmpty().take(1000),
                (item.opt("summary") as? String).orEmpty().take(4000), (item.opt("date") as? String).orEmpty().take(40))
        }
    }

    fun fetchRequest(url: String): JSONObject {
        require(usableUrl(url)) { AiText.t(AiMsg.FETCH_URL_INVALID) }
        return JSONObject().put("url", url)
    }

    /** The page as Markdown; the API reports a page with no usable content as an error status, not here. */
    fun content(response: String): String {
        val json = JSONObject(response)
        check(!json.has("error")) { AiText.t(AiMsg.FETCH_RETURNED_ERROR) }
        return json.opt("content") as? String ?: throw IllegalStateException(AiText.t(AiMsg.FETCH_RETURNED_ERROR))
    }

    /** Public http(s) only, no credentials in the URL, bounded length. */
    fun usableUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        return uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null && raw.length <= 8192
    }
}
