package com.customDB

import com.customDB.api.LocalStorageEngine
import com.customDB.api.NodeRef
import com.customDB.api.SqlEngine
import com.customDB.node.repl.ReplicationHttpServer
import com.customDB.node.repl.PrimaryReplicatorHttp
import com.customDB.server.ClusterBus
import com.customDB.server.HealthRegistry
import com.customDB.server.RouterHttpServer
import java.io.File
import java.nio.file.Paths

fun main() {
    val clusterFile = File("cluster/cluster.json")
    require(clusterFile.exists()) { "cluster/cluster.json not found" }

    // 1) общий кластер + health
    ClusterBus.initAndWatch(clusterFile)
    HealthRegistry.startPolling(periodSec = 60)

    val st = ClusterBus.current()

    // 2) поднимаем все узлы по кластеру
    st.cfg.shards.forEach { shard ->
        val shardId = shard.id

        // ---- master
        run {
            val sqlPort = shard.master.port
            val replPort = sqlPort + 1000
            val dataDir = Paths.get("src", "LocalDB", shardId).toFile().apply { mkdirs() }
            val engine = LocalStorageEngine(dataDir)

            val replTargets: List<NodeRef> =
                shard.replicas.map { NodeRef(it.host, it.port + 1000) }

            val replicator = PrimaryReplicatorHttp(shardId, replTargets)
            val sql = SqlEngine(engine, shardId, replicator)

            SqlHttp.start(sqlPort, engine, sql)
            ReplicationHttpServer(engine).start(replPort)

            println("MASTER[$shardId] sql=:$sqlPort repl=:$replPort → replicas(repl)=${replTargets.joinToString { "${it.host}:${it.port}" }}")
        }

        // ---- replicas
        shard.replicas.forEach { r ->
            val sqlPort = r.port
            val replPort = sqlPort + 1000
            val dataDir = Paths.get("src", "LocalDB", "${shardId}_replica_${sqlPort}").toFile().apply { mkdirs() }
            val engine = LocalStorageEngine(dataDir)
            val sql = SqlEngine(engine, shardId, null)

            SqlHttp.start(sqlPort, engine, sql)
            ReplicationHttpServer(engine).start(replPort)

            println("REPLICA[$shardId] sql=:$sqlPort repl=:$replPort")
        }
    }

    // 3) роутер
    RouterHttpServer().start(8080)
}

private object SqlHttp {
    fun start(port: Int, engine: LocalStorageEngine, sql: SqlEngine) {
        // твой SqlHttpServer уже есть; просто запускаем
        val s = com.customDB.server.SqlHttpServer(engine, sql)
        s.start(port)
    }
}
