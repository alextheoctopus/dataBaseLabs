package com.customDB.node.repl

import com.customDB.api.LocalStorageEngine
import com.customDB.api.RepBatch
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * HTTP-приёмник репликации.
 * Эндпоинты:
 *  - POST /repl/push      — принимает JSON RepBatch и применяет его локально
 *  - GET  /repl/heartbeat — возвращает {"applied":N} для мониторинга
 */
class ReplicationHttpServer(
    engine: LocalStorageEngine,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val applier = ReplicaApplierHttp(engine, json)

    fun start(port: Int) {
        val srv = HttpServer.create(InetSocketAddress(port), 0)

        srv.createContext("/repl/heartbeat") { ex ->
            val body = """{"applied":${applier.appliedCount()}}"""
            respond(ex, 200, "application/json; charset=utf-8", body)
        }

        srv.createContext("/repl/push", PushHandler())

        srv.executor = null
        srv.start()
        println("Replication HTTP listening on :$port  (/repl/push, /repl/heartbeat)")
    }

    private inner class PushHandler : HttpHandler {
        override fun handle(ex: HttpExchange) {
            try {
                if (ex.requestMethod.uppercase() != "POST") {
                    respond(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed"); return
                }
                val text = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8).trim()
                if (text.isEmpty()) {
                    respond(ex, 400, "text/plain; charset=utf-8", "Empty body"); return
                }

                val batch = json.decodeFromString(RepBatch.serializer(), text)
                // apply(...) у тебя suspend → оборачиваем
                runBlocking { applier.apply(batch) }

                val body = """{"status":"OK","applied":${applier.appliedCount()}}"""
                respond(ex, 200, "application/json; charset=utf-8", body)
            } catch (t: Throwable) {
                val msg = t.message ?: t::class.simpleName ?: "error"
                respond(ex, 500, "application/json; charset=utf-8", """{"error":"$msg"}""")
            }
        }
    }

    private fun respond(ex: HttpExchange, code: Int, contentType: String, body: String) {
        val headers: Headers = ex.responseHeaders
        headers.add("Content-Type", contentType)
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
}
