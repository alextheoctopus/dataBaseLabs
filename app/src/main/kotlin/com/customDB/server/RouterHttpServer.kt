package com.customDB.server

import com.customDB.api.NodeRef
import com.sun.net.httpserver.HttpServer
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.alter.Alter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets

class RouterHttpServer {
    fun start(port: Int) {
        val srv = HttpServer.create(InetSocketAddress(port), 0)

        fun forward(node: NodeRef, path: String, body: ByteArray): ByteArray {
            val url = URL("http://${node.host}:${node.port}$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                doOutput = true
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                outputStream.use { it.write(body) }
            }
            return conn.inputStream.readAllBytes()
        }

        fun isDdl(sql: String): Boolean {
            val st: Statement = CCJSqlParserUtil.parse(sql)
            return st is CreateTable || st is Drop || st is Alter
        }

        fun allMasters(): List<NodeRef> {
            val st = ClusterBus.current()
            return st.cfg.shards.map { it.master }
        }

        // Запись (INSERT/UPDATE/DELETE/DDL)
        srv.createContext("/execute") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val body = sql.toByteArray()

            if (isDdl(sql)) {
                // фан-аут на все мастера; берём ответ последнего как итог
                var last: ByteArray = "[]".toByteArray()
                for (m in allMasters()) {
                    last = forward(m, "/query", body) // узел исполняет на /query
                }
                ex.sendResponseHeaders(200, last.size.toLong())
                ex.responseBody.use { it.write(last) }
            } else {
                val st = ClusterBus.current()
                val locator = ShardLocator(st, "id")
                val shardId = locator.defineSlotAndShard(sql)
                val leader  = st.masterOf(shardId.second)
                val target  = if (HealthRegistry.isAlive(leader)) leader
                else st.bestReplicaOrMaster(shardId.second)
                val resp = forward(target, "/query", body)
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
        }

        // Чтение (SELECT) — как и раньше: в реплику, иначе в мастера
        srv.createContext("/query") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val st  = ClusterBus.current()
            val locator = ShardLocator(st, "id")
            val shardId = locator.defineSlotAndShard(sql)
            val cand    = st.bestReplicaOrMaster(shardId.second)
            val leader  = st.masterOf(shardId.second)
            val target  = if (HealthRegistry.isAlive(cand)) cand
            else if (HealthRegistry.isAlive(leader)) leader
            else cand
            val resp = forward(target, "/query", sql.toByteArray())
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }

        srv.start()
        println("Router listening on :$port")
    }
}
