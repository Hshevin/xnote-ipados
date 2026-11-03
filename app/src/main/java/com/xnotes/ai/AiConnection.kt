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

data class AiConfig(val url: String = "", val model: String = "", val key: String = "", val searchKey: String = "")

/** Keystore-encrypted, atomic, excluded from Android backup and note exports. */
class AiConfigStore(context: Context) {
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
        if (!file.baseFile.exists()) return AiConfig()
        val bytes = file.readFully()
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return AiConfig(json.getString("url"), json.getString("model"), json.getString("key"), json.optString("searchKey", ""))
    }
    fun save(config: AiConfig) {
        AiProtocol.endpoint(config.url)
        require(config.model.isNotBlank() && config.key.isNotBlank()) { "请填写模型名称和 API Key" }
        require(!config.key.contains('\n') && !config.key.contains('\r')) { "API Key 格式无效" }
        require(!config.searchKey.contains('\n') && !config.searchKey.contains('\r')) { "搜索 API Key 格式无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().put("url", config.url.trim()).put("model", config.model.trim()).put("key", config.key.trim()).put("searchKey", config.searchKey.trim())
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun clear() = file.delete()
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
        require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) { "请先填写 DeepSeek 搜索 API Key" }
        val bytes = DeepSeekSearch.request(query).toString().toByteArray(Charsets.UTF_8)
        check(!cancelled) { "已停止" }
        val conn = openConnection(DeepSeekSearch.ENDPOINT)
        connection = conn
        try {
            check(!cancelled) { "已停止" }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            check(conn.responseCode == 200) { "DeepSeek 搜索失败（HTTP ${conn.responseCode}），请检查搜索密钥与额度；未继续生成回答" }
            val result = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (!cancelled) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(result.length + count <= 1024 * 1024) { "搜索响应过大" }
                    result.append(buffer, 0, count)
                }
            }
            check(!cancelled) { "已停止" }
            return DeepSeekSearch.context(result.toString())
        } finally { conn.disconnect(); connection = null }
    }
    override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) {
        val conn = openConnection(AiProtocol.endpoint(config.url))
        connection = conn
        try {
            check(!cancelled) { "已停止" }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${config.key}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            val bytes = AiProtocol.request(config.model, messages).toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 12 * 1024 * 1024) { "截图或会话过大，请新建会话并缩小截图" }
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            check(conn.responseCode in 200..299) { "请求失败（HTTP ${conn.responseCode}），请检查接口、密钥、模型及额度" }
            var received = 0
            var finished = false
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val line = StringBuilder()
                while (!cancelled) {
                    val ch = reader.read()
                    if (ch < 0) break
                    received++
                    check(received <= 1024 * 1024 && line.length < 65536) { "回答超出长度限制" }
                    if (ch == 10) {
                        val value = line.toString().trimEnd('\r'); line.setLength(0)
                        if (value.startsWith("data:")) {
                            val data = value.substring(5).trim()
                            if (data == "[DONE]") { finished = true; break }
                            if (data.isNotEmpty()) {
                                val json = JSONObject(data)
                                check(!json.has("error")) { "供应商返回错误，请检查模型配置" }
                                delta(AiProtocol.delta(data))
                            }
                        }
                    } else line.append(ch.toChar())
                }
            }
            check(!cancelled && finished) { if (cancelled) "已停止" else "连接提前结束，请重试" }
        } finally { conn.disconnect(); connection = null }
    }
}
