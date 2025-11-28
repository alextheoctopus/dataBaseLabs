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
    @Volatile
    private var server: HttpServer? = null

    fun start(port: Int) {
        val srv = HttpServer.create(InetSocketAddress(port), 0)
        srv.createContext("/repl/heartbeat") { ex ->
            val body = """{"applied":${applier.appliedCount()}}"""
            respond(ex, 200, "application/json; charset=utf-8", body)
        }
        srv.createContext("/repl/push") { ex ->
            if (ex.requestMethod.uppercase() != "POST") {
                respond(ex, 405, "text/plain", "Method Not Allowed"); return@createContext
            }
            val text = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            val batch = json.decodeFromString(RepBatch.serializer(), text)
            runBlocking { applier.apply(batch) }
            respond(
                ex,
                200,
                "application/json; charset=utf-8",
                """{"status":"OK","applied":${applier.appliedCount()}}"""
            )
        }
        srv.start()
        server = srv
        println("Replication HTTP listening on :$port  (/repl/push, /repl/heartbeat)")
    }

    fun stop(delaySeconds: Int = 0) {
        server?.stop(delaySeconds); server = null
    }

    private fun respond(ex: HttpExchange, code: Int, ct: String, body: String) {
        ex.responseHeaders.add("Content-Type", ct)
        val b = body.toByteArray(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(code, b.size.toLong())
        ex.responseBody.use { it.write(b) }
    }
}
