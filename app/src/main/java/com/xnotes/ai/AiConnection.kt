package com.xnotes.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * The provider and how to ask it. [model] is the default for new chats and [models] the user's pick
 * list; [effort] is the default thinking level ("" leaves it to the provider) and [thinkingFormat]
 * which request field carries it. [instructions] is the user's own system prompt for answers.
 * [searchByDefault] and [attachPdfByDefault] are how a new chat starts out.
 */
data class AiConfig(
    val url: String = "", val model: String = "", val key: String = "", val searchKey: String = "",
    val models: List<String> = emptyList(),
    val effort: String = "",
    val thinkingFormat: String = AiThinking.AUTO,
    val maxTokens: Int = AiProtocol.DEFAULT_MAX_TOKENS,
    val instructions: String = "",
    val searchByDefault: Boolean = false,
    val attachPdfByDefault: Boolean = false,
) {
    /** The saved pick list in the user's order, always holding the default model. */
    val modelChoices: List<String> get() = (if (model.trim() in models.map(String::trim)) models else listOf(model) + models).map(String::trim).filter(String::isNotEmpty).distinct()
    /** Search planning and PDF tool steps must return bare JSON/text: no thinking, no user prompt. */
    fun forTools(): AiConfig = copy(effort = "", instructions = "")
}

/** Keystore-encrypted, atomic, excluded from Android backup and note exports. */
class AiConfigStore(context: Context) {
    /** The default system prompt in the UI language, for a setup that has never chosen one. */
    private val defaultInstructions = AiDefaults.instructions(context.resources.configuration.locales[0].toLanguageTag())
    private val file = AtomicFile(File(context.noBackupFilesDir, "ask-ai.config"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ask-ai", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("ask-ai", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): AiConfig {
        if (!file.baseFile.exists()) return AiConfig(instructions = defaultInstructions)
        val bytes = file.readFully()
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val models = json.optJSONArray("models")?.let { list -> (0 until list.length()).mapNotNull { list.opt(it) as? String } } ?: emptyList()
        return AiConfig(json.getString("url"), json.getString("model"), json.getString("key"), json.optString("searchKey", ""),
            models = models, effort = json.optString("effort", "").takeIf { it in AiThinking.LEVELS } ?: "",
            thinkingFormat = json.optString("thinkingFormat", AiThinking.AUTO).takeIf { it in AiThinking.FORMATS } ?: AiThinking.AUTO,
            maxTokens = json.optInt("maxTokens", AiProtocol.DEFAULT_MAX_TOKENS).coerceIn(AiProtocol.MIN_MAX_TOKENS, AiProtocol.MAX_MAX_TOKENS),
            // A config saved before prompts existed gets the default; an emptied prompt stays empty.
            instructions = if (json.has("instructions")) json.optString("instructions", "") else defaultInstructions,
            searchByDefault = json.optBoolean("searchByDefault", false), attachPdfByDefault = json.optBoolean("attachPdfByDefault", false))
    }
    fun save(config: AiConfig) {
        validate(config)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().put("url", config.url.trim()).put("model", config.model.trim()).put("key", config.key.trim()).put("searchKey", config.searchKey.trim())
            .put("models", org.json.JSONArray(config.modelChoices)).put("effort", config.effort).put("thinkingFormat", config.thinkingFormat)
            .put("maxTokens", config.maxTokens).put("instructions", config.instructions.trim())
            .put("searchByDefault", config.searchByDefault).put("attachPdfByDefault", config.attachPdfByDefault)
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun clear() = file.delete()


    companion object {
        /** Everything [save] refuses, checked before anything is written; the previous file stays. */
        fun validate(config: AiConfig) {
            AiProtocol.endpoint(config.url)
            require(config.model.isNotBlank() && config.key.isNotBlank()) { AiText.t(AiMsg.CONFIG_MODEL_KEY_REQUIRED) }
            require(!config.key.contains('\n') && !config.key.contains('\r')) { AiText.t(AiMsg.CONFIG_KEY_INVALID) }
            require(!config.searchKey.contains('\n') && !config.searchKey.contains('\r')) { AiText.t(AiMsg.CONFIG_SEARCH_KEY_INVALID) }
            require(config.modelChoices.size <= AiProtocol.MAX_MODELS) { AiText.t(AiMsg.CONFIG_TOO_MANY_MODELS, AiProtocol.MAX_MODELS) }
            require(config.maxTokens in AiProtocol.MIN_MAX_TOKENS..AiProtocol.MAX_MAX_TOKENS) { AiText.t(AiMsg.CONFIG_MAX_TOKENS_INVALID, AiProtocol.MIN_MAX_TOKENS, AiProtocol.MAX_MAX_TOKENS) }
            require(config.instructions.length <= AiProtocol.MAX_INSTRUCTIONS) { AiText.t(AiMsg.CONFIG_INSTRUCTIONS_TOO_LONG, AiProtocol.MAX_INSTRUCTIONS) }
            require(config.effort in AiThinking.LEVELS && config.thinkingFormat in AiThinking.FORMATS) { AiText.t(AiMsg.CONFIG_THINKING_INVALID) }
        }
    }
}

interface AiTransport {
    fun cancel()
    fun search(key: String, query: String): String
    suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit)
}

/** One request, no redirects/retries/logging; cancellation closes the active socket. */
class AiConnection(private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) : AiTransport {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    override fun cancel() { cancelled = true; connection?.disconnect() }
    override fun search(key: String, query: String): String {
        require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) { AiText.t(AiMsg.SEARCH_KEY_REQUIRED) }
        val bytes = DeepSeekSearch.request(query).toString().toByteArray(Charsets.UTF_8)
        check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
        val conn = openConnection(DeepSeekSearch.ENDPOINT)
        connection = conn
        try {
            check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            check(conn.responseCode == 200) { AiText.t(AiMsg.SEARCH_HTTP_FAILED, conn.responseCode) }
            val result = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (!cancelled) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(result.length + count <= 1024 * 1024) { AiText.t(AiMsg.SEARCH_RESPONSE_TOO_LARGE) }
                    result.append(buffer, 0, count)
                }
            }
            check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
            return DeepSeekSearch.context(result.toString())
        } finally { conn.disconnect(); connection = null }
    }
    /** The provider's OpenAI-style model list (GET /models); sent only when the user asks for it in settings. */
    fun listModels(url: String, key: String): List<String> {
        require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) { AiText.t(AiMsg.CONFIG_MODEL_KEY_REQUIRED) }
        val conn = openConnection(AiProtocol.modelsEndpoint(url))
        connection = conn
        try {
            check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Accept", "application/json")
            check(conn.responseCode == 200) { AiText.t(AiMsg.MODELS_HTTP_FAILED, conn.responseCode) }
            val result = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (!cancelled) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(result.length + count <= 4 * 1024 * 1024) { AiText.t(AiMsg.MODELS_RESPONSE_TOO_LARGE) }
                    result.append(buffer, 0, count)
                }
            }
            check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
            return AiProtocol.models(result.toString())
        } finally { conn.disconnect(); connection = null }
    }
    override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) {
        val conn = openConnection(AiProtocol.endpoint(config.url))
        connection = conn
        try {
            check(!cancelled) { AiText.t(AiMsg.CONNECTION_STOPPED) }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${config.key}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            val bytes = AiProtocol.request(config, messages).toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 12 * 1024 * 1024) { AiText.t(AiMsg.REQUEST_TOO_LARGE) }
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            check(conn.responseCode in 200..299) { AiText.t(AiMsg.REQUEST_HTTP_FAILED, conn.responseCode) }
            var received = 0
            var finished = false
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val line = StringBuilder()
                while (!cancelled) {
                    val ch = reader.read()
                    if (ch < 0) break
                    received++
                    check(received <= 1024 * 1024 && line.length < 65536) { AiText.t(AiMsg.ANSWER_TOO_LONG) }
                    if (ch == 10) {
                        val value = line.toString().trimEnd('\r'); line.setLength(0)
                        if (value.startsWith("data:")) {
                            val data = value.substring(5).trim()
                            if (data == "[DONE]") { finished = true; break }
                            if (data.isNotEmpty()) {
                                val json = JSONObject(data)
                                check(!json.has("error")) { AiText.t(AiMsg.PROVIDER_ERROR) }
                                delta(AiProtocol.delta(data))
                            }
                        }
                    } else line.append(ch.toChar())
                }
            }
            check(!cancelled && finished) { if (cancelled) AiText.t(AiMsg.CONNECTION_STOPPED) else AiText.t(AiMsg.CONNECTION_ENDED_EARLY) }
        } finally { conn.disconnect(); connection = null }
    }
}
