package com.customDB.api

import kotlinx.serialization.Serializable

@Serializable
data class NodeRef(val host: String, val port: Int)

@Serializable
data class ShardCfg(
    val id: String,
    val startSlot: Int,          // вместо IntRange
    val endSlot: Int,
    val leader: NodeRef,
    val replicas: List<NodeRef> = emptyList()
)

@Serializable
data class ClusterCfg(
    val hashSlots: Int = 1024,
    val vnodesPerShard: Int = 64,
    val shards: List<ShardCfg>
)
