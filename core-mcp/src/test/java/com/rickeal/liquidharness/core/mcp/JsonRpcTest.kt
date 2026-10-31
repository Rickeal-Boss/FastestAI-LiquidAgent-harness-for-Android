package com.rickeal.liquidharness.core.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JSON-RPC 帧构造 / 解析 / 载体分帧的纯函数单测。无网络、无协程。 */
class JsonRpcTest {

    @Test
    fun requestFrameCarriesIdMethodAndParams() {
        val frame = Json.parseToJsonElement(
            requestFrame(7L, "tools/list", """{"cursor":"p2"}"""),
        ).jsonObject
        assertEquals("2.0", frame["jsonrpc"]!!.jsonPrimitive.content)
        assertEquals("7", frame["id"]!!.jsonPrimitive.content)
        assertEquals("tools/list", frame["method"]!!.jsonPrimitive.content)
        assertEquals("p2", frame["params"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
    }

    @Test
    fun requestFrameOmitsParamsWhenNull() {
        val frame = Json.parseToJsonElement(requestFrame(1L, "server/discover")).jsonObject
        assertNull(frame["params"])
        assertEquals("server/discover", frame["method"]!!.jsonPrimitive.content)
    }

    @Test
    fun notificationFrameHasNoId() {
        val frame = Json.parseToJsonElement(notificationFrame("notifications/initialized")).jsonObject
        assertNull(frame["id"])
        assertEquals("notifications/initialized", frame["method"]!!.jsonPrimitive.content)
    }

    @Test
    fun parseResponseReturnsResultJson() {
        val outcome = parseResponse("""{"jsonrpc":"2.0","id":3,"result":{"tools":[]}}""")
        assertTrue(outcome is RpcOutcome.Ok)
        outcome as RpcOutcome.Ok
        assertEquals(3L, outcome.id)
        assertEquals("""{"tools":[]}""", outcome.resultJson)
    }

    @Test
    fun parseResponseReturnsErrorObject() {
        val outcome = parseResponse(
            """{"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"no such method"}}""",
        )
        assertTrue(outcome is RpcOutcome.Err)
        outcome as RpcOutcome.Err
        assertEquals(4L, outcome.id)
        assertEquals(-32601, outcome.code)
        assertEquals("no such method", outcome.message)
    }

    @Test
    fun parseResponseAllowsNullIdOnError() {
        val outcome = parseResponse("""{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse"}}""")
        assertTrue(outcome is RpcOutcome.Err)
        assertNull((outcome as RpcOutcome.Err).id)
        assertEquals(-32700, outcome.code)
    }

    @Test
    fun parseResponseRejectsNonJson() {
        val outcome = parseResponse("<html>gateway</html>")
        assertTrue(outcome is RpcOutcome.Malformed)
        assertEquals("not_json", (outcome as RpcOutcome.Malformed).detail)
    }

    @Test
    fun parseResponseRejectsMissingJsonrpc() {
        val outcome = parseResponse("""{"id":1,"result":{}}""")
        assertTrue(outcome is RpcOutcome.Malformed)
        assertEquals("missing_jsonrpc", (outcome as RpcOutcome.Malformed).detail)
    }

    @Test
    fun extractPayloadsHandlesSingleJsonObject() {
        val payloads = extractPayloads("""{"jsonrpc":"2.0","id":1,"result":{}}""", "application/json")
        assertEquals(1, payloads.size)
        assertTrue(payloads.single().contains("\"result\""))
    }

    @Test
    fun extractPayloadsHandlesSseMultiFrame() {
        val raw = "event: message\n" +
            "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n" +
            "\n" +
            ": keep-alive\n" +
            "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n" +
            "\n" +
            "data: [DONE]\n\n"
        val payloads = extractPayloads(raw, "text/event-stream")
        assertEquals(2, payloads.size)
        assertTrue(payloads[0].contains("\"id\":1"))
        assertTrue(payloads[1].contains("\"id\":2"))
    }

    @Test
    fun extractPayloadsJoinsMultilineData() {
        val raw = "data: {\"jsonrpc\":\"2.0\",\n" +
            "data: \"id\":9,\"result\":{}}\n\n"
        val payloads = extractPayloads(raw, "text/event-stream")
        assertEquals(1, payloads.size)
        val parsed = parseResponse(payloads.single())
        assertTrue(parsed is RpcOutcome.Ok)
        assertEquals(9L, (parsed as RpcOutcome.Ok).id)
    }

    @Test
    fun extractPayloadsReturnsEmptyForBlankBody() {
        assertTrue(extractPayloads("", "application/json").isEmpty())
        assertTrue(extractPayloads("   \n", "text/event-stream").isEmpty())
    }

    @Test
    fun extractPayloadsHandlesBatchArray() {
        val raw = """[{"jsonrpc":"2.0","id":1,"result":{}},{"jsonrpc":"2.0","id":2,"result":{}}]"""
        assertEquals(2, extractPayloads(raw, "application/json").size)
    }

    @Test
    fun extractPayloadsKeepsNonJsonBodyForDiagnosis() {
        val payloads = extractPayloads("upstream unavailable", "text/plain")
        assertEquals(1, payloads.size)
        assertEquals("upstream unavailable", payloads.single())
    }
}
