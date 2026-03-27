package com.customDB.server

import com.customDB.api.NodeRef
import com.sun.net.httpserver.HttpServer
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.alter.Alter
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets

class RouterHttpServer(
    private val locator: ShardLocator
) {
    fun start(port: Int) {
        val srv = HttpServer.create(InetSocketAddress(port), 0)

        fun forward(node: NodeRef, path: String, body: ByteArray): Pair<Int, ByteArray> {
            val url = URL("http://${node.host}:${node.port}$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                doOutput = true
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connectTimeout = 2000
                readTimeout = 5000
                outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val bytes = try {
                conn.inputStream.readAllBytes()
            } catch (_: Throwable) {
                (conn.errorStream ?: ByteArrayInputStream(ByteArray(0))).readAllBytes()
            }
            return code to bytes
        }

        fun isDdl(sql: String): Boolean {
            val st: Statement = CCJSqlParserUtil.parse(sql)
            return st is CreateTable || st is Drop || st is Alter
        }

        fun allMasters(): List<NodeRef> = ClusterBus.current().cfg.shards.map { it.master }

        // ---- /execute: DDL -> broadcast; DML -> по shardKey ----
        srv.createContext("/execute") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)

            // 1) DDL: шлём всем мастерам
            if (isDdl(sql)) {
                val masters = allMasters()
                var ok = false
                val details = StringBuilder()
                masters.forEach { m ->
                    val (code, _) = forward(m, "/execute", sql.toByteArray(StandardCharsets.UTF_8))
                    details.append(" ${m.host}:${m.port}=$code;")
                    if (code in 200..299) ok = true
                }
                val body = """{"status":"${if (ok) "OK" else "ERROR"}","broadcasted":${masters.size},"detail":"$details"}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                ex.sendResponseHeaders(if (ok) 200 else 500, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
                println("[ROUTER/DDL] -> masters: ${masters.joinToString { "${it.host}:${it.port}" }} :: ${sql.take(120)}")
                return@createContext
            }

            // 2) DML/прочее: по shardKey
            val (slot, shardId) = locator.defineSlotAndShard(sql)
            val cluster = ClusterBus.current()
            val leader  = cluster.masterOf(shardId)

            val alive = HealthRegistry.isAlive(leader) || HealthRegistry.pingNow(leader)
            val target = if (alive) leader else cluster.bestReplicaOrMaster(shardId)

            val (code, resp) = forward(target, "/execute", sql.toByteArray(StandardCharsets.UTF_8))
            ex.sendResponseHeaders(code, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }

            println("[ROUTER/EXEC] slot=$slot shard=$shardId leaderAlive=$alive -> ${target.host}:${target.port} :: ${sql.take(120)}")
        }

        // ---- /query: читаем с лучшей реплики/мастера ----
        srv.createContext("/query") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val (_, shardId) = locator.defineSlotAndShard(sql)
            val cluster = ClusterBus.current()
            val node = cluster.bestReplicaOrMaster(shardId)
            val (code, resp)  = forward(node, "/query", sql.toByteArray(StandardCharsets.UTF_8))
            ex.sendResponseHeaders(code, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
            println("[ROUTER/QUERY] shard=$shardId -> ${node.host}:${node.port} :: ${sql.take(120)}")
        }

        srv.start()
        println("Router listening on :$port")
    }
}
