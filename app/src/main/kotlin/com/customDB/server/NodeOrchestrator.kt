package com.customDB.server

import com.customDB.api.*
import com.customDB.node.repl.ReplicationHttpServer
import com.customDB.node.repl.PrimaryReplicatorHttp
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class NodeOrchestrator {

    data class ServerBundle(
        val shardId: String,
        val role: String,          // "master"|"replica"
        val index: Int?,           // индекс реплики в списке (для ключа), для мастера null
        val sqlPort: Int,
        val replPort: Int,
        val sqlHttp: SqlHttpServer,
        val replHttp: ReplicationHttpServer
    )

    private val masters  = ConcurrentHashMap<String, ServerBundle>()
    private val replicas = ConcurrentHashMap<Pair<String,Int>, ServerBundle>()

    fun startAll(cfg: ClusterCfg) {
        // мастера
        cfg.shards.forEach { sh ->
            if (!masters.containsKey(sh.id)) startMaster(sh)
        }
        // реплики
        cfg.shards.forEach { sh ->
            sh.replicas.forEachIndexed { i, r ->
                val key = sh.id to i
                if (!replicas.containsKey(key)) startReplica(sh.id, r.port, key)
            }
        }
    }

    fun applyDiff(prev: ClusterCfg?, cur: ClusterCfg) {
        // stop удалённых мастеров
        prev?.shards?.map { it.id }?.toSet()
            ?.minus(cur.shards.map { it.id }.toSet())
            ?.forEach { stopMaster(it) }

        // start новых мастеров
        cur.shards.forEach { if (!masters.containsKey(it.id)) startMaster(it) }

        // реплики: сравниваем по (shardId, index) и по портам
        cur.shards.forEach { sh ->
            val desired = sh.replicas.mapIndexed { i, r -> (sh.id to i) to r.port }.toMap()
            val have    = replicas.filterKeys { it.first == sh.id }.mapValues { it.value.sqlPort }

            // stop лишние/переехавшие
            have.forEach { (k, port) ->
                val want = desired[k]
                if (want == null || want != port) stopReplica(k)
            }
            // start недостающие
            desired.forEach { (k, port) ->
                if (!replicas.containsKey(k)) startReplica(sh.id, port, k)
            }
        }
    }

    private fun startMaster(sh: ShardCfg) {
        val dataDir = Paths.get("src", "LocalDB", sh.id).toFile().apply { mkdirs() }
        val engine = LocalStorageEngine(dataDir)
        val sqlEngine = SqlEngine(engine, sh.id, PrimaryReplicatorHttp(sh.id))

        val sqlHttp  = SqlHttpServer(engine, sqlEngine).apply { start(sh.master.port) }
        val replHttp = ReplicationHttpServer(engine).apply { start(sh.master.port + 1000) }

        val b = ServerBundle(sh.id, "master", null, sh.master.port, sh.master.port + 1000, sqlHttp, replHttp)
        masters[sh.id] = b
        println("MASTER[${sh.id}] sql=:${b.sqlPort} repl=:${b.replPort}")
    }

    private fun stopMaster(shardId: String) {
        masters.remove(shardId)?.let {
            it.sqlHttp.stop(0); it.replHttp.stop(0)
            println("MASTER[$shardId] stopped")
        }
    }

    private fun startReplica(shardId: String, sqlPort: Int, key: Pair<String,Int>) {
        val dataDir = Paths.get("src", "LocalDB", "${shardId}_replica${key.second}").toFile().apply { mkdirs() }
        val engine = LocalStorageEngine(dataDir)
        val sqlEngine = SqlEngine(engine, shardId, null)

        val sqlHttp  = SqlHttpServer(engine, sqlEngine).apply { start(sqlPort) }
        val replHttp = ReplicationHttpServer(engine).apply { start(sqlPort + 1000) }

        val b = ServerBundle(shardId, "replica", key.second, sqlPort, sqlPort + 1000, sqlHttp, replHttp)
        replicas[key] = b
        println("REPLICA[$shardId#${key.second}] sql=:${b.sqlPort} repl=:${b.replPort}")
    }

    private fun stopReplica(key: Pair<String,Int>) {
        replicas.remove(key)?.let {
            it.sqlHttp.stop(0); it.replHttp.stop(0)
            println("REPLICA[${it.shardId}#${it.index}] stopped")
        }
    }
}
