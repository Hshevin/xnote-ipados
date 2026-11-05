package com.xnotes.ai

import java.nio.file.Files
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiProvidersTest {
    private val deepseek = AiProvider("ds", "DeepSeek", "https://api.deepseek.com/v1", "ds-key", listOf("deepseek-chat", "deepseek-reasoner"), AiThinking.DEEPSEEK)
    private val router = AiProvider("or", "OpenRouter", "https://openrouter.ai/api/v1", "or-key", listOf("openai/gpt-4o", "vision"))
    private val setup = AiConfig(searchKey = "s", providers = listOf(deepseek, router), provider = "ds").route("ds", "deepseek-chat")

    @Test fun legacySingleProviderConfigMigratesToTheFixedDefaultProvider() {
        val legacy = JSONObject().put("url", "https://example.com/v1").put("model", "vision").put("key", "k").put("searchKey", "")
            .put("models", org.json.JSONArray(listOf("other"))).put("thinkingFormat", AiThinking.QWEN).put("instructions", "")
        val config = AiConfigStore.decode(legacy, "默认")
        val only = config.providers.single()
        assertEquals(AiConfigStore.LEGACY_PROVIDER, only.id)
        assertEquals(listOf("vision", "other"), only.modelChoices)
        assertEquals(AiThinking.QWEN, only.thinkingFormat)
        // The decoded config is already routed through the default provider.
        assertEquals(Triple("https://example.com/v1", "k", "vision"), Triple(config.url, config.key, config.model))
        assertEquals(AiConfigStore.LEGACY_PROVIDER, config.provider)
        // Decoding twice gives the same ID, so chats that saved it keep resolving.
        assertEquals(only.id, AiConfigStore.decode(legacy, "默认").providers.single().id)
    }

    @Test fun emptyLegacyConfigHasNoProviders() {
        val config = AiConfigStore.decode(JSONObject().put("url", "").put("model", "").put("key", ""), "默认")
        assertTrue(config.providers.isEmpty())
        assertEquals("默认", config.instructions)
    }

    @Test fun encodeDecodeKeepsProvidersAndWritesTheDefaultForOlderBuilds() {
        val json = AiConfigStore.encode(setup.route("or", "vision"))
        // An older build reads only the top-level fields: they must be the default provider's.
        assertEquals("https://openrouter.ai/api/v1", json.getString("url"))
        assertEquals("or-key", json.getString("key"))
        assertEquals("vision", json.getString("model"))
        assertEquals(listOf("openai/gpt-4o", "vision"), (0 until json.getJSONArray("models").length()).map { json.getJSONArray("models").getString(it) })
        val back = AiConfigStore.decode(JSONObject(json.toString()), "")
        assertEquals(listOf(deepseek, router), back.providers)
        assertEquals("or", back.provider)
        assertEquals("vision", back.model)
        assertEquals("or-key", back.key)
    }

    @Test fun unknownStoredDefaultFallsBackToTheFirstProvider() {
        val json = AiConfigStore.encode(setup).put("provider", "gone")
        val back = AiConfigStore.decode(json, "")
        assertEquals("ds", back.provider)
        assertEquals("ds-key", back.key)
    }

    @Test fun chatRoutesThroughItsOwnProviderAndKeepsItsThinkingFormat() {
        val chat = AiChat(model = "vision", provider = "or", effort = "high")
        val route = chat.settings(setup)
        assertEquals(Triple("https://openrouter.ai/api/v1", "or-key", "vision"), Triple(route.url, route.key, route.model))
        assertEquals(AiThinking.AUTO, route.thinkingFormat)
        assertEquals("high", route.effort)
        val body = AiProtocol.request(AiChat(model = "deepseek-reasoner", provider = "ds", effort = "high").settings(setup), listOf(AiMessage("user", "q")))
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"))
    }

    @Test fun chatWithoutPicksFollowsTheDefaultRoute() {
        val route = AiChat().settings(setup)
        assertEquals(Triple("ds", "deepseek-chat", "ds-key"), Triple(route.provider, route.model, route.key))
    }

    @Test fun chatFromBeforeProvidersFindsTheProviderListingItsModel() {
        assertEquals("or", AiChat(model = "vision").settings(setup).provider)
        // A model no provider lists still goes through the default.
        assertEquals("ds", AiChat(model = "unlisted").settings(setup).provider)
        // The default provider wins when two list the same model.
        val shared = setup.copy(providers = listOf(deepseek, router.copy(models = listOf("deepseek-chat"))))
        assertEquals("ds", AiChat(model = "deepseek-chat").settings(shared).provider)
    }

    @Test fun deletedProviderLeavesTheChatUnroutableWithoutEndpointOrKey() {
        val chat = AiChat(model = "vision", provider = "or")
        val withoutRouter = setup.copy(providers = listOf(deepseek))
        assertTrue(chat.routable(setup))
        assertFalse(chat.routable(withoutRouter))
        val route = chat.settings(withoutRouter)
        assertEquals("", route.url); assertEquals("", route.key)
        // No providers at all (a bare route) is never blocked here.
        assertTrue(chat.routable(AiConfig("https://example.com/v1", "m", "k")))
    }

    @Test fun validDefaultSurvivesEditsAndMovesWhenItsProviderGoes() {
        assertEquals("deepseek-chat", setup.withValidDefault().model)
        val removed = setup.copy(providers = listOf(router)).withValidDefault()
        assertEquals(Triple("or", "openai/gpt-4o", "or-key"), Triple(removed.provider, removed.model, removed.key))
        val modelDropped = setup.copy(providers = listOf(deepseek.copy(models = listOf("deepseek-reasoner")), router)).withValidDefault()
        assertEquals("deepseek-reasoner", modelDropped.model)
        val none = setup.copy(providers = emptyList()).withValidDefault()
        assertEquals("" to "", none.provider to none.model)
    }

    @Test fun validationNamesTheProviderAndRequiresADefaultModel() {
        AiConfigStore.validate(setup)
        val badUrl = setup.copy(providers = listOf(deepseek, router.copy(url = "http://openrouter.ai/api/v1")))
        val message = assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(badUrl) }.message.orEmpty()
        assertTrue(message, message.contains("OpenRouter") && message.contains(AiMsg.ENDPOINT_INVALID.zh))
        assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(setup.copy(providers = listOf(deepseek, router.copy(models = emptyList())))) }
        assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(setup.copy(providers = listOf(deepseek, router.copy(key = "")))) }
        // The default model has to be one the default provider lists.
        assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(setup.copy(model = "vision")) }
        assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(setup.copy(providers = listOf(deepseek, deepseek.copy(name = "again")))) }
        val many = (0..AiConfigStore.MAX_PROVIDERS).map { router.copy(id = "p$it") }
        val tooMany = assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(setup.copy(providers = many, provider = "p0", model = "vision")) }
        assertEquals(AiText.t(AiMsg.CONFIG_TOO_MANY_PROVIDERS, AiConfigStore.MAX_PROVIDERS), tooMany.message)
        assertThrows(IllegalArgumentException::class.java) { AiConfigStore.validate(AiConfig()) }
    }

    @Test fun providerLabelFallsBackToTheHost() {
        assertEquals("api.example.com", AiProvider(name = " ", url = "https://api.example.com/v1").label)
        assertEquals("Mine", AiProvider(name = " Mine ", url = "https://api.example.com/v1").label)
    }

    @Test fun historyKeepsTheChatsProvider() {
        val dir = Files.createTempDirectory("history").toFile()
        try {
            val store = AiHistoryStore(dir)
            val chat = AiChat(model = "vision", provider = "or")
            store.write(chat)
            assertEquals("or", store.readAll().first.single().provider)
            // A record from before providers reads with a blank provider.
            val file = dir.listFiles()!!.single()
            file.writeText(JSONObject(file.readText()).apply { remove("provider") }.toString())
            assertEquals("", store.readAll().first.single().provider)
        } finally { dir.deleteRecursively() }
    }

    @Test fun choosingAModelRecordsItsProvider() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            val sessions = AiSessions(AiHistoryStore(dir), scope, { setup })
            withTimeout(5000) { while (!sessions.ready) delay(5) }
            val id = sessions.newChat()
            sessions.choose(id, model = "vision", provider = "or")
            assertEquals("or" to "vision", sessions.chats[id]!!.provider to sessions.chats[id]!!.model)
            sessions.choose(id, effort = "low")
            assertEquals("or", sessions.chats[id]!!.provider)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun sendingFromAChatWhoseProviderWasDeletedFailsWithoutARequest() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        var requested = false
        val transport = object : AiTransport {
            override fun cancel() {}
            override fun search(key: String, query: String, question: String): List<AiWebSource> = emptyList()
            override fun fetch(key: String, url: String) = ""
            override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) { requested = true }
        }
        var config = setup
        try {
            val sessions = AiSessions(AiHistoryStore(dir), scope, { config }, { transport })
            withTimeout(5000) { while (!sessions.ready) delay(5) }
            val id = sessions.newChat()
            sessions.choose(id, model = "vision", provider = "or")
            config = setup.copy(providers = listOf(deepseek))
            sessions.send(id, AiMessage("user", "问题"), false)
            withTimeout(5000) { while (sessions.isRunning(id) || sessions.chats[id]!!.turns.isEmpty()) delay(5) }
            val turn = sessions.chats[id]!!.turns.single()
            assertEquals("生成失败", turn.state)
            assertEquals(AiMsg.PROVIDER_REMOVED.zh, turn.error)
            assertFalse(requested)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
