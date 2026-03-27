package com.customDB.node.repl

import com.customDB.api.*
import com.customDB.server.ClusterBus
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class PrimaryReplicatorHttp(
    private val shardId: String,
    private val json: Json = Json { encodeDefaults = true }
): PrimaryReplicator {

    private val published = AtomicLong(0)
    private fun currentReplicaEndpoints(): List<NodeRef> {
        val st = ClusterBus.current()
        // берём реплики шардa и мапим их SQL-порт -> порт репликации (sql+1000)
        return st.cfg.shards.first { it.id == shardId }.replicas.map {
            NodeRef(it.host, it.port + 1000)
        }
    }
    //передача данных в реплику
    override suspend fun publish(batch: RepBatch) {
        val body = json.encodeToString(RepBatch.serializer(), batch).toByteArray()
        val targets = currentReplicaEndpoints()
        if (targets.isEmpty()) return

        var acks = 0
        targets.parallelStream().forEach { r ->
            try {
                val url = URL("http://${r.host}:${r.port}/repl/push")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000; readTimeout = 3000
                    doOutput = true; requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body) }
                }
                if (conn.responseCode in 200..299) synchronized(this) { acks++ }
            } catch (_: Throwable) { /* ignore */ }
        }
        if (acks > 0) published.addAndGet(batch.ops.size.toLong())
    }
}