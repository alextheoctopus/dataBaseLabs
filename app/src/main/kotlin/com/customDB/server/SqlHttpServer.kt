package com.customDB.server

import com.customDB.api.*
import com.customDB.api.FieldType.*
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64

class SqlHttpServer(
    private val engine: LocalStorageEngine,
    private val sql: SqlEngine,
) {
    @Volatile private var server: HttpServer? = null

    fun start(port: Int = 8080): HttpServer {
        val s = HttpServer.create(InetSocketAddress(port), 0)
        s.createContext("/health", JsonHandler { _ -> mapOf("status" to "ok") })
        s.createContext("/query", QueryHandler(sql))
        s.createContext("/execute", QueryHandler(sql))
        s.executor = null
        s.start()
        this.server = s
        println("SQL server is listening on http://localhost:${s.address.port}")
        return s
    }

    fun stop(delaySeconds: Int = 0) {
        server?.stop(delaySeconds)
        server = null
    }

    private class QueryHandler(private val sql: SqlEngine) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod.uppercase() != "POST") {
                respondJson(exchange, 405, mapOf("error" to "Method Not Allowed"))
                return
            }
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8).trim()
            if (body.isEmpty()) {
                respondJson(exchange, 400, mapOf("error" to "Empty body"))
                return
            }
            try {
                val res = sql.execute(body)
                val json = resultToJson(res)
                respond(exchange, 200, "application/json; charset=utf-8", json)
            } catch (e: Throwable) {
                respondJson(exchange, 400, mapOf("error" to (e.message ?: e::class.simpleName)))
            }
        }
    }

    private class JsonHandler(
        private val block: (HttpExchange) -> Any?
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val result = block(exchange)
                respond(exchange, 200, "application/json; charset=utf-8", anyToJson(result))
            } catch (e: Throwable) {
                respondJson(exchange, 500, mapOf("error" to (e.message ?: e::class.simpleName)))
            }
        }
    }

    private companion object {
        fun respondJson(exchange: HttpExchange, code: Int, payload: Any?) {
            respond(exchange, code, "application/json; charset=utf-8", anyToJson(payload))
        }

        fun respond(exchange: HttpExchange, code: Int, contentType: String, body: String) {
            val headers: Headers = exchange.responseHeaders
            headers.add("Content-Type", contentType)
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        fun resultToJson(res: Any?): String = when (res) {
            null -> """{"status":"OK"}"""
            is String -> anyToJson(mapOf("status" to "OK", "detail" to res))
            is Number, is Boolean -> anyToJson(res)
            is Row -> anyToJson(rowToJsonLike(res))
            is Iterable<*> -> anyToJson(res.map {
                when (it) {
                    is Row -> rowToJsonLike(it)
                    is Map<*, *> -> it
                    null -> null
                    else -> it.toString()
                }
            })
            is Map<*, *> -> anyToJson(res)
            else -> anyToJson(res.toString())
        }

        fun rowToJsonLike(row: Row): Map<String, Any?> =
            mapOf("values" to row.values.mapValues { (_, ft) -> fieldTypeToJson(ft) })

        fun fieldTypeToJson(ft: FieldType?): Any? = when (ft) {
            null -> null
            is STRING -> mapOf("type" to "STRING", "v" to ft.v)
            is LONG -> mapOf("type" to "LONG", "v" to ft.v)
            is DOUBLE -> mapOf("type" to "DOUBLE", "v" to ft.v)
            is BOOL -> mapOf("type" to "BOOL", "v" to ft.v)
            is PK -> mapOf("type" to "PK", "v" to ft.start)
            is INSTANT -> mapOf("type" to "INSTANT", "v" to ft.v)
            is BYTES -> mapOf("type" to "BYTES", "v" to Base64.getEncoder().encodeToString(ft.v))
        }

        fun anyToJson(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "\"${escape(k.toString())}\":${anyToJson(v)}"
            }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { anyToJson(it) }
            is Row -> anyToJson(rowToJsonLike(value))
            is FieldType -> anyToJson(fieldTypeToJson(value))
            else -> "\"${escape(value.toString())}\""
        }

        fun escape(s: String): String = buildString(s.length + 8) {
            s.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"'  -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }
    }
}
