package com.customDB.server

import com.customDB.api.ClusterCfg
import com.customDB.api.ShardCfg
import kotlinx.serialization.json.Json
import java.io.File

class ClusterState(val cfg: ClusterCfg) {
    val hashSlots = cfg.hashSlots

    fun shardBySlot(slot: Int): ShardCfg =
        cfg.shards.first { slot in it.startSlot..it.endSlot }

    fun leaderOf(id: String) =
        cfg.shards.first { it.id == id }.leader

    fun bestReplicaOrLeader(id: String) =
        cfg.shards.first { it.id == id }.replicas.firstOrNull()
            ?: cfg.shards.first { it.id == id }.leader

    companion object {
        fun load(file: File): ClusterState {
            val text = file.readText()
            val cfg = Json.decodeFromString(ClusterCfg.serializer(), text)
            return ClusterState(cfg)
        }
    }
}
