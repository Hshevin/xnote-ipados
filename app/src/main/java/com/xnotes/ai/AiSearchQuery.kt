package com.xnotes.ai

import org.json.JSONObject

/** Resolve references such as 'this paper' using the image before contacting the search API. */
object AiSearchQuery {
    fun messages(history: List<AiMessage>, user: AiMessage): List<AiMessage> = listOf(AiMessage("system",
        "你现在只负责生成网页检索词，不回答用户问题。结合用户当前截图、PDF附件、问题及对话确定具体对象。" +
        "论文问题优先逐字识别截图或PDF首页里的完整论文标题，可补充作者以消歧。不得猜测看不清的标题、作者或年份。" +
        "将‘这篇论文’‘这个’等指代替换为明确名称。无法确定对象则输出空 query。" +
        "仅输出 JSON：{\"query\":\"1至128个Unicode字符的明确检索词，或空字符串\"}，不要 Markdown，不要解释。")) +
        history.map { it.copy(searchContext = "") } + user.copy(searchContext = "")

    fun parse(raw: String): String {
        val value = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val query = try { JSONObject(value).opt("query") as? String } catch (_: Exception) { null }
        require(!query.isNullOrBlank()) { "无法从截图和问题确定搜索对象，请补充清晰截图或具体标题后重试" }
        val trimmed = query.trim()
        DeepSeekSearch.request(trimmed)
        return trimmed
    }
}
