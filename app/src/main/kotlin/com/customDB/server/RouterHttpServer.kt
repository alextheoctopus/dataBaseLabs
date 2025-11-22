//фронт-роутер для шардирования
package com.customDB.server

import com.customDB.api.NodeRef
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets

class RouterHttpServer(
    private val cluster: ClusterState,
    private val locator: ShardLocator
) {
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

        srv.createContext("/execute") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            //получаем слот и шард
            val (slot, shardId) = locator.defineSlotAndShard(sql)
            //Получаем мастера по шарду
            val master = cluster.masterOf(shardId)
            println("Router [ROUTER/EXEC] slot=$slot shard=$shardId -> ${master.host}:${master.port} :: ${sql.take(120)}")

            //  отладочные заголовки 
            ex.responseHeaders.add("X-Route-Slot", slot.toString())
            ex.responseHeaders.add("X-Route-Shard", shardId)
            ex.responseHeaders.add("X-Route-Node", "${master.host}:${master.port}")

            val resp = forward(master, "/query", sql.toByteArray())
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }

        srv.createContext("/query") { ex ->
            val sql = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val (slot, shardId) = locator.defineSlotAndShard(sql)
            val node = cluster.bestReplicaOrMaster(shardId)
            println("[ROUTER/QUERY] slot=$slot shard=$shardId -> ${node.host}:${node.port} :: ${sql.take(120)}")

            ex.responseHeaders.add("X-Route-Slot", slot.toString())
            ex.responseHeaders.add("X-Route-Shard", shardId)
            ex.responseHeaders.add("X-Route-Node", "${node.host}:${node.port}")

            val resp = forward(node, "/query", sql.toByteArray())
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }

        srv.start()
    }
}
