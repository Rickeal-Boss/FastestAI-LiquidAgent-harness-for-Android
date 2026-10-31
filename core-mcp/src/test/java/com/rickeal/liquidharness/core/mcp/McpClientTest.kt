package com.rickeal.liquidharness.core.mcp

import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolSourceKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [McpClient] 的协议状态机单测。全部走 [FakeTransport]，不起真实网络。 */
class McpClientTest {

    // ------------------------------------------------------------ 握手 / 协商

    @Test
    fun discoverSuccessNegotiatesModernEra() = runBlocking {
        val transport = FakeTransport(happyRoute("""{"tools":[]}"""))
        val client = McpClient(config(), transport)
        val result = client.connect()

        assertTrue(result is ConnectResult.Ok)
        assertEquals(McpProtocol.ERA_MODERN, client.era)
        assertEquals(1, transport.count("server/discover"))
        assertEquals(0, transport.count("initialize"))
        assertEquals("sess-1", client.sessionId)
    }

    @Test
    fun discoverMethodNotFoundFallsBackToInitialize() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> rpcError(body, McpProtocol.METHOD_NOT_FOUND, "method not found")
                "initialize" -> result(body, """{"protocolVersion":"2025-11-25"}""", session = "sess-legacy")
                else -> TransportResponse(202, "", "", null)
            }
        })
        val client = McpClient(config(), transport)
        val result = client.connect()

        assertTrue(result is ConnectResult.Ok)
        assertEquals(McpProtocol.ERA_LEGACY, client.era)
        assertEquals(1, transport.count("initialize"))
        assertEquals(1, transport.count("notifications/initialized"))
        assertEquals("sess-legacy", client.sessionId)
    }

    @Test
    fun discoverHttp404FallsBackToInitialize() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> TransportResponse(404, "", "text/plain", null)
                "initialize" -> result(body, "{}")
                else -> TransportResponse(202, "", "", null)
            }
        })
        val client = McpClient(config(), transport)
        assertTrue(client.connect() is ConnectResult.Ok)
        assertEquals(1, transport.count("initialize"))
    }

    @Test
    fun explicitLegacyEraSkipsDiscovery() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "initialize" -> result(body, "{}")
                else -> TransportResponse(202, "", "", null)
            }
        })
        val client = McpClient(config(era = McpProtocol.ERA_LEGACY), transport)
        assertTrue(client.connect() is ConnectResult.Ok)
        assertEquals(0, transport.count("server/discover"))
        assertEquals(1, transport.count("initialize"))
    }

    // ------------------------------------------------------------ 失败即数据

    @Test
    fun unauthorizedLoadFailsWithHttpReasonCode() = runBlocking {
        val transport = FakeTransport { TransportResponse(401, "", "application/json", null) }
        val snapshot = McpClient(config(), transport).load()

        assertFalse(snapshot.ok)
        assertEquals("srv-1", snapshot.sourceId)
        assertEquals("http_401", snapshot.message)
        assertTrue(snapshot.tools.isEmpty())
    }

    @Test
    fun transportTimeoutMapsToTimeoutReason() = runBlocking {
        val transport = FakeTransport { TransportResponse(0, "timeout") }
        val snapshot = McpClient(config(), transport).load()

        assertFalse(snapshot.ok)
        assertEquals("timeout", snapshot.message)
    }

    @Test
    fun malformedBodyFailsWithoutThrowing() = runBlocking {
        val transport = FakeTransport { TransportResponse(200, "<html>oops</html>", "text/html", null) }
        val snapshot = McpClient(config(), transport).load()

        assertFalse(snapshot.ok)
        assertEquals("malformed", snapshot.message)
    }

    @Test
    fun toolsListHttpErrorPropagatesReasonCode() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/list" -> TransportResponse(500, "", "application/json", null)
                else -> TransportResponse(202, "", "", null)
            }
        })
        val snapshot = McpClient(config(), transport).load()

        assertFalse(snapshot.ok)
        assertEquals("http_500", snapshot.message)
    }

    // ------------------------------------------------------------ 工具清单

    @Test
    fun toolsListFollowsCursorAcrossPages() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/list" -> {
                    val cursor = paramsOf(body)?.get("cursor")?.jsonPrimitive?.contentOrNull
                    if (cursor == null) {
                        result(
                            body,
                            """{"tools":[{"name":"alpha","description":"A","inputSchema":{"type":"object"}}],"nextCursor":"page-2"}""",
                        )
                    } else {
                        assertEquals("page-2", cursor)
                        result(
                            body,
                            """{"tools":[{"name":"beta","description":"B","inputSchema":{"type":"object"}}]}""",
                        )
                    }
                }
                else -> TransportResponse(202, "", "", null)
            }
        })
        val snapshot = McpClient(config(), transport).load()

        assertTrue(snapshot.ok)
        assertEquals(listOf("mcp__srv__alpha", "mcp__srv__beta"), snapshot.tools.map { it.name })
        assertEquals(2, transport.count("tools/list"))
    }

    @Test
    fun toolNamesAreNamespacedAndSanitized() = runBlocking {
        val transport = FakeTransport(
            happyRoute("""{"tools":[{"name":"read.file","description":"R","inputSchema":{}}]}"""),
        )
        val spec = McpClient(config(alias = "My Server"), transport).load().tools.single()

        assertEquals("mcp__My_Server__read_file", spec.name)
        assertEquals("My Server", spec.namespace)
        assertEquals(ToolSourceKind.MCP, spec.source)
        assertTrue(spec.requiresConfirmation)
        assertEquals(ToolEffect.WRITE, spec.effect)
    }

    @Test
    fun chineseAliasProducesAsciiSafeName() = runBlocking {
        val transport = FakeTransport(
            happyRoute("""{"tools":[{"name":"search","description":"","inputSchema":{}}]}"""),
        )
        val name = McpClient(config(alias = "服务器"), transport).load().tools.single().name

        assertTrue(name.matches(Regex("[a-zA-Z0-9_-]+")))
        assertTrue(name.startsWith("mcp__"))
        assertTrue(name.length <= McpProtocol.MAX_TOOL_NAME)
    }

    @Test
    fun longAliasAndToolNameAreCappedAt64() = runBlocking {
        val toolsJson = """{"tools":[{"name":"${"t".repeat(80)}","description":"","inputSchema":{}}]}"""
        val transport = FakeTransport(happyRoute(toolsJson))
        val name = McpClient(config(alias = "a".repeat(80)), transport).load().tools.single().name

        assertEquals(McpProtocol.MAX_TOOL_NAME, name.length)
    }

    @Test
    fun repeatedLoadsProduceIdenticalNames() = runBlocking {
        val transport = FakeTransport(
            happyRoute(
                """{"tools":[{"name":"x.y","description":"d","inputSchema":{}},{"name":"x y","description":"d","inputSchema":{}}]}""",
            ),
        )
        val client = McpClient(config(), transport)
        val first = client.load().tools.map { it.name }
        val second = client.load().tools.map { it.name }

        assertEquals(first, second)
        assertEquals(1, first.size)
    }

    @Test
    fun descriptionIsTruncatedAndControlCharactersStripped() = runBlocking {
        val noisy = "a".repeat(600) + "\u0000\u0007\u001B"
        val toolsJson = buildJsonObject {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("name", "noisy")
                    put("description", noisy)
                    put("inputSchema", buildJsonObject { put("type", "object") })
                })
            })
        }.toString()
        val spec = McpClient(config(), FakeTransport(happyRoute(toolsJson))).load().tools.single()

        assertEquals(McpProtocol.MAX_DESCRIPTION, spec.description.length)
        assertTrue(spec.description.none { it.code < 0x20 || it.code == 0x7F })
    }

    @Test
    fun newlinesInDescriptionBecomeSpaces() = runBlocking {
        val toolsJson = buildJsonObject {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("name", "t")
                    put("description", "line one\nline two")
                    put("inputSchema", buildJsonObject { })
                })
            })
        }.toString()
        val spec = McpClient(config(), FakeTransport(happyRoute(toolsJson))).load().tools.single()

        assertEquals("line one line two", spec.description)
    }

    @Test
    fun parametersAreMappedFromInputSchema() = runBlocking {
        val toolsJson = """
            {"tools":[{"name":"q","description":"","inputSchema":
            {"type":"object","properties":{
              "query":{"type":"string","description":"what"},
              "count":{"type":"integer"},
              "flag":{"type":"boolean"},
              "tags":{"type":"array"}
            },"required":["query","count"]}}]}
        """.trimIndent()
        val spec = McpClient(config(), FakeTransport(happyRoute(toolsJson))).load().tools.single()
        val byName = spec.parameters.associateBy { it.name }

        assertEquals(4, byName.size)
        assertEquals(ToolParamType.STRING, byName.getValue("query").type)
        assertEquals(ToolParamType.INTEGER, byName.getValue("count").type)
        assertEquals(ToolParamType.BOOLEAN, byName.getValue("flag").type)
        assertEquals(ToolParamType.ARRAY, byName.getValue("tags").type)
        assertTrue(byName.getValue("query").required)
        assertFalse(byName.getValue("flag").required)
    }

    // ------------------------------------------------------------ 工具调用

    @Test
    fun callJoinsTextBlocksAndSendsRemoteName() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/list" -> result(
                    body,
                    """{"tools":[{"name":"search","description":"","inputSchema":{}}]}""",
                )
                "tools/call" -> {
                    val params = paramsOf(body)!!
                    assertEquals("search", params["name"]!!.jsonPrimitive.content)
                    assertEquals("k", params["arguments"]!!.jsonObject["q"]!!.jsonPrimitive.content)
                    result(
                        body,
                        """{"content":[{"type":"text","text":"one"},{"type":"text","text":"two"}]}""",
                    )
                }
                else -> TransportResponse(202, "", "", null)
            }
        })
        val client = McpClient(config(), transport)
        client.load()
        val outcome = client.call("mcp__srv__search", """{"q":"k"}""")

        assertTrue(outcome.ok)
        assertEquals("one\ntwo", outcome.output)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun callMapsIsErrorToFailure() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/call" -> result(
                    body,
                    """{"content":[{"type":"text","text":"boom"}],"isError":true}""",
                )
                else -> TransportResponse(202, "", "", null)
            }
        })
        val outcome = McpClient(config(), transport).call("search", "{}")

        assertFalse(outcome.ok)
        assertEquals("boom", outcome.output)
        assertEquals("boom", outcome.errorMessage)
    }

    @Test
    fun callTimeoutIsReportedAsData() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/call" -> TransportResponse(0, "timeout")
                else -> TransportResponse(202, "", "", null)
            }
        })
        val outcome = McpClient(config(), transport).call("search", "{}")

        assertFalse(outcome.ok)
        assertEquals("timeout", outcome.errorMessage)
    }

    @Test
    fun callRejectsMalformedArgumentsWithoutNetwork() = runBlocking {
        val transport = FakeTransport(happyRoute("""{"tools":[]}"""))
        val outcome = McpClient(config(), transport).call("search", "{oops")

        assertFalse(outcome.ok)
        assertEquals("invalid_arguments", outcome.errorMessage)
        assertEquals(0, transport.count("tools/call"))
    }

    @Test
    fun callAcceptsSseResponse() = runBlocking {
        val transport = FakeTransport(route { method, body ->
            when (method) {
                "server/discover" -> result(body, "{}")
                "tools/call" -> TransportResponse(
                    200,
                    "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":${idOf(body)},\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"sse\"}]}}\n\n",
                    "text/event-stream",
                    "sess-1",
                )
                else -> TransportResponse(202, "", "", null)
            }
        })
        val outcome = McpClient(config(), transport).call("search", "{}")

        assertTrue(outcome.ok)
        assertEquals("sse", outcome.output)
    }

    // ------------------------------------------------------------ 测试替身

    private fun config(alias: String = "srv", era: String = McpProtocol.ERA_AUTO): McpServerConfig =
        McpServerConfig(id = "srv-1", alias = alias, url = "https://mcp.example.test/rpc", protocolEra = era)

    private fun happyRoute(toolsJson: String): (String) -> TransportResponse = route { method, body ->
        when (method) {
            "server/discover" -> result(body, """{"protocolVersion":"2026-07-28"}""")
            "tools/list" -> result(body, toolsJson)
            "tools/call" -> result(body, """{"content":[{"type":"text","text":"ok"}]}""")
            else -> TransportResponse(202, "", "", null)
        }
    }

    private fun route(handler: (String, String) -> TransportResponse): (String) -> TransportResponse =
        { body -> handler(methodOf(body).orEmpty(), body) }

    private fun result(
        requestBody: String,
        resultJson: String,
        session: String? = "sess-1",
    ): TransportResponse = TransportResponse(
        status = 200,
        body = """{"jsonrpc":"2.0","id":${idOf(requestBody)},"result":$resultJson}""",
        contentType = "application/json",
        sessionId = session,
    )

    private fun rpcError(requestBody: String, code: Int, message: String): TransportResponse =
        TransportResponse(
            status = 200,
            body = """{"jsonrpc":"2.0","id":${idOf(requestBody)},"error":{"code":$code,"message":"$message"}}""",
            contentType = "application/json",
            sessionId = "sess-1",
        )

    private class FakeTransport(
        private val responder: (String) -> TransportResponse,
    ) : McpTransport {

        private val bodies = mutableListOf<String>()

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: String,
            sessionId: String?,
        ): TransportResponse {
            bodies += body
            return responder(body)
        }

        fun count(method: String): Int = bodies.count { methodOf(it) == method }
    }
}

private fun methodOf(body: String): String? = runCatching {
    Json.parseToJsonElement(body).jsonObject["method"]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun paramsOf(body: String): JsonObject? = runCatching {
    Json.parseToJsonElement(body).jsonObject["params"] as? JsonObject
}.getOrNull()

private fun idOf(body: String): Long = runCatching {
    Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}.getOrNull() ?: 0L
