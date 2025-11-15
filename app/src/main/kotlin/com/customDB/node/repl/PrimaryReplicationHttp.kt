package com.customDB.node.repl

import com.customDB.api.*
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class PrimaryReplicatorHttp(
    private val shardId: String,
    private val replicas: List<NodeRef>,
    private val json: Json = Json { encodeDefaults = true }
): PrimaryReplicator {

    private val published = AtomicLong(0)

    override suspend fun publish(batch: RepBatch) {
        if (replicas.isEmpty()) return
        val body = json.encodeToString(RepBatch.serializer(), batch).toByteArray()
        var acks = 0
        replicas.parallelStream().forEach { r ->
            try {
                val url = URL("http://${r.host}:${r.port}/repl/push")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body) }
                }
                if (conn.responseCode in 200..299) synchronized(this) { acks++ }
            } catch (_: Throwable) {}
        }
        if (acks > 0) published.addAndGet(batch.ops.size.toLong())
        // Для ЛР достаточно ack>=1; можно сделать флаг ackQuorum.
    }
}
